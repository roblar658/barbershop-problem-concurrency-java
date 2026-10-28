package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tester for First-In-First-Out (FIFO) køorden")
class BarberShopFifoTest {

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
    @DisplayName("Kunder i venterommet skal alltid betjenes i nøyaktig samme rekkefølge som de ankom (FIFO)")
    void customersAreServedInStrictFifoOrder() throws Exception {
        CountDownLatch initialHaircutLatch = new CountDownLatch(1);
        List<Integer> servedOrder = new CopyOnWriteArrayList<>();

        // Strategi som logger rekkefølgen klipper blir utført i
        HaircutStrategy strategy = (customer, barber) -> {
            if (customer.getId() == 0) {
                // Den første kunden holder barbereren opptatt mens resten setter seg i venterommet
                initialHaircutLatch.await();
            }
            servedOrder.add(customer.getId());
            return 5L;
        };

        int waitingChairs = 5;
        shop = new BarberShop(waitingChairs, strategy);
        shop.open();

        Thread.sleep(40);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<CustomerResult>> futures = new ArrayList<>();

        // Kunde 0: Låser barbereren i stolen
        futures.add(pool.submit(() -> shop.enter(new Customer(0, "Kunde-0"))));
        Thread.sleep(50);

        // Kunder 1 til 5 ankommer sekvensielt og setter seg i venterommet
        for (int i = 1; i <= waitingChairs; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "Kunde-" + id))));
            Thread.sleep(30); // Sikrer deterministisk ankomstrekkefølge i venterommet
        }

        assertEquals(waitingChairs, shop.getWaitingCustomerCount(), "Venterommet skal ha 5 kunder");

        // Lås opp barbereren
        initialHaircutLatch.countDown();

        for (Future<CustomerResult> f : futures) {
            CustomerResult res = f.get();
            assertTrue(res.isServed());
        }

        // Verifiser at rekkefølgen er nøyaktig 0, 1, 2, 3, 4, 5
        List<Integer> expectedOrder = List.of(0, 1, 2, 3, 4, 5);
        assertEquals(expectedOrder, servedOrder, "Kundene må klippes i nøyaktig FIFO-rekkefølge!");

        pool.shutdown();
    }
}

