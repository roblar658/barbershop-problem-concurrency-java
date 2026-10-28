package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tester for ventestol-kapasitet og avvisning (Balking)")
class BarberShopBalkingTest {

    private BarberShop shop;

    @AfterEach
    void tearDown() throws Exception {
        if (shop != null && shop.isOpen()) {
            shop.close(false);
            shop.awaitTermination(Duration.ofSeconds(1));
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Kunde skal umiddelbart avvises når alle ventestoler er opptatt")
    void customerBalksWhenAllChairsAreOccupied() throws Exception {
        // Kontrollert strategi der barbereren holdes i saksen via en latch
        CountDownLatch releaseHaircutLatch = new CountDownLatch(1);
        int waitingChairs = 2;
        shop = new BarberShop(waitingChairs, HaircutStrategy.controlled(releaseHaircutLatch));
        shop.open();

        Thread.sleep(50);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<CustomerResult>> futures = new ArrayList<>();

        // Kunde 1: Tar barberstolen
        futures.add(pool.submit(() -> shop.enter(new Customer(1, "Kunde-1"))));

        // Vent til barbereren begynner å klippe kunde 1
        Thread.sleep(80);
        assertEquals(BarberState.CUTTING_HAIR, shop.getBarber().getState());

        // Kunde 2: Tar ventestol 1
        futures.add(pool.submit(() -> shop.enter(new Customer(2, "Kunde-2"))));
        Thread.sleep(40);
        assertEquals(1, shop.getWaitingCustomerCount());

        // Kunde 3: Tar ventestol 2 (nå er venterommet helt fullt!)
        futures.add(pool.submit(() -> shop.enter(new Customer(3, "Kunde-3"))));
        Thread.sleep(40);
        assertEquals(2, shop.getWaitingCustomerCount());

        // Kunde 4: Ankommer når alle 2 ventestoler er fulle! Må avvises umiddelbart!
        long start = System.currentTimeMillis();
        CustomerResult overflowResult = shop.enter(new Customer(4, "Kunde-4"));
        long elapsed = System.currentTimeMillis() - start;

        // Skal returnere momentant (< 100 ms) uten å blokkere
        assertTrue(elapsed < 100, "Avvisningen skal skje umiddelbart uten venting");
        assertEquals(CustomerStatus.LEFT_BECAUSE_FULL, overflowResult.getStatus());
        assertTrue(overflowResult.hasLeftBecauseFull());
        assertEquals(1, shop.getTotalCustomersTurnedAway());

        // Kunde 5: Også avvises umiddelbart
        CustomerResult overflowResult2 = shop.enter(new Customer(5, "Kunde-5"));
        assertEquals(CustomerStatus.LEFT_BECAUSE_FULL, overflowResult2.getStatus());
        assertEquals(2, shop.getTotalCustomersTurnedAway());

        // Nå slipper vi løs barbereren så kunde 1, 2 og 3 blir ferdige
        releaseHaircutLatch.countDown();

        CustomerResult r1 = futures.get(0).get();
        CustomerResult r2 = futures.get(1).get();
        CustomerResult r3 = futures.get(2).get();

        assertTrue(r1.isServed());
        assertTrue(r2.isServed());
        assertTrue(r3.isServed());

        assertEquals(3, shop.getTotalCustomersServed());
        assertEquals(2, shop.getTotalCustomersTurnedAway());

        pool.shutdown();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Når en kunde flytter til barberstolen, blir en ventestol ledig for ny ankommende")
    void freedChairAllowsNewCustomerToEnter() throws Exception {
        CountDownLatch c1Latch = new CountDownLatch(1);
        CountDownLatch c2Latch = new CountDownLatch(1);

        // Strategi som venter på c1Latch for kunde 1, og c2Latch for kunde 2
        HaircutStrategy selectiveStrategy = (customer, barber) -> {
            if (customer.getId() == 1) {
                c1Latch.await();
            } else if (customer.getId() == 2) {
                c2Latch.await();
            }
            return 10L;
        };

        // 1 ventestol
        shop = new BarberShop(1, selectiveStrategy);
        shop.open();

        Thread.sleep(50);

        ExecutorService pool = Executors.newCachedThreadPool();

        // Kunde 1 i barberstolen
        pool.submit(() -> shop.enter(new Customer(1, "Kunde-1")));
        Thread.sleep(60);

        // Kunde 2 i ventestolen (1/1 opptatt)
        pool.submit(() -> shop.enter(new Customer(2, "Kunde-2")));
        Thread.sleep(40);
        assertEquals(1, shop.getWaitingCustomerCount());

        // Kunde 3 prøver å gå inn -> fullt!
        CustomerResult r3 = shop.enter(new Customer(3, "Kunde-3"));
        assertEquals(CustomerStatus.LEFT_BECAUSE_FULL, r3.getStatus());

        // Fullfør kunde 1 -> Kunde 2 blir kalt inn til barberstolen -> ventestolen blir ledig!
        c1Latch.countDown();
        Thread.sleep(60);

        // Nå sitter kunde 2 i barberstolen, så ventestolen er ledig (0 ventende)!
        assertEquals(0, shop.getWaitingCustomerCount());

        // Kunde 4 ankommer nå -> skal få plass i den ledige ventestolen!
        Future<CustomerResult> f4 = pool.submit(() -> shop.enter(new Customer(4, "Kunde-4")));
        Thread.sleep(40);
        assertEquals(1, shop.getWaitingCustomerCount());

        // Fullfør kunde 2
        c2Latch.countDown();

        CustomerResult r4 = f4.get();
        assertTrue(r4.isServed());
        assertEquals(CustomerStatus.SERVED, r4.getStatus());

        pool.shutdown();
    }
}

