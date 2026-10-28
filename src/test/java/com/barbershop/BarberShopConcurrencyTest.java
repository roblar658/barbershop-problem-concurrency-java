package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tester for høy samtidighet, kappløp og bevaringslover")
class BarberShopConcurrencyTest {

    private BarberShop shop;

    @AfterEach
    void tearDown() throws Exception {
        if (shop != null && shop.isOpen()) {
            shop.close(false);
            shop.awaitTermination(Duration.ofSeconds(1));
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Plutselig bølge av 40 samtidige tråder skal aldri overskride venteromskapasiteten")
    void suddenBurstNeverExceedsWaitingCapacity() throws Exception {
        int capacity = 4;
        shop = new BarberShop(capacity, HaircutStrategy.fixed(Duration.ofMillis(20)));

        AtomicInteger maxWaitingObserved = new AtomicInteger(0);
        shop.addListener(event -> {
            maxWaitingObserved.accumulateAndGet(event.waitingCustomerCount(), Math::max);
        });

        shop.open();

        int customerCount = 40;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(customerCount);
        ExecutorService pool = Executors.newFixedThreadPool(customerCount);

        List<CustomerResult> results = new CopyOnWriteArrayList<>();

        for (int i = 1; i <= customerCount; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    startGate.await(); // Alle tråder slippes samtidig
                    CustomerResult res = shop.enter(new Customer(id, "Bølge-Kunde-" + id));
                    results.add(res);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Avfyr alle tråder på nøyaktig samme nanosekund
        startGate.countDown();
        assertTrue(doneGate.await(8, TimeUnit.SECONDS), "Alle kunder må fullføre innen tidsfristen");

        // Bevaringslov: Alle kunder må enten være SERVED eller LEFT_BECAUSE_FULL
        assertEquals(customerCount, results.size());
        long served = results.stream().filter(CustomerResult::isServed).count();
        long turnedAway = results.stream().filter(CustomerResult::hasLeftBecauseFull).count();

        assertEquals(customerCount, served + turnedAway, "Bevaringslov: served + turnedAway == total");
        assertEquals(served, shop.getTotalCustomersServed());
        assertEquals(turnedAway, shop.getTotalCustomersTurnedAway());

        // Verifiser at venterommet aldri hadde flere enn 'capacity' stoler opptatt
        assertTrue(maxWaitingObserved.get() <= capacity,
                "Venterommet overskred kapasiteten! Observert: " + maxWaitingObserved.get() + ", Max: " + capacity);

        pool.shutdown();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Blanding av asynkrone og synkrone kunder under vedvarende belastning")
    void mixedSyncAndAsyncCustomers() throws Exception {
        int capacity = 3;
        shop = new BarberShop(capacity, HaircutStrategy.fixed(Duration.ofMillis(10)));
        shop.open();

        int total = 50;
        List<CompletableFuture<CustomerResult>> futures = new ArrayList<>();

        for (int i = 1; i <= total; i++) {
            Customer c = new Customer(i, "Mix-" + i);
            if (i % 2 == 0) {
                // Asynkron
                futures.add(shop.enterAsync(c));
            } else {
                // Synkron i virtuell/egen tråd
                CompletableFuture<CustomerResult> cf = new CompletableFuture<>();
                new Thread(() -> {
                    try {
                        cf.complete(shop.enter(c));
                    } catch (Exception e) {
                        cf.completeExceptionally(e);
                    }
                }).start();
                futures.add(cf);
            }
            Thread.sleep(5);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long served = shop.getTotalCustomersServed();
        long turnedAway = shop.getTotalCustomersTurnedAway();

        assertEquals(total, served + turnedAway);
        assertTrue(served > 0, "Noen kunder må ha blitt klippet");
    }
}

