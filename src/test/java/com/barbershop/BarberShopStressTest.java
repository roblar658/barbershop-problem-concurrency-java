package com.barbershop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stresstester med høy gjennomstrømning og mange tråder")
class BarberShopStressTest {

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("500 kunder sendt fra 16 samtidige tråder skal håndteres 100% konsistent")
    void highThroughput500Customers() throws Exception {
        int chairs = 5;
        BarberShop shop = new BarberShop(chairs, HaircutStrategy.instant());
        shop.open();

        int totalCustomers = 500;
        int clientThreads = 16;
        ExecutorService clientPool = Executors.newFixedThreadPool(clientThreads);

        List<Future<CustomerResult>> futures = new ArrayList<>(totalCustomers);

        for (int i = 1; i <= totalCustomers; i++) {
            final int id = i;
            futures.add(clientPool.submit(() -> shop.enter(new Customer(id, "Stress-" + id))));
        }

        int servedCount = 0;
        int turnedAwayCount = 0;

        for (Future<CustomerResult> f : futures) {
            CustomerResult r = f.get();
            if (r.isServed()) {
                servedCount++;
            } else if (r.hasLeftBecauseFull()) {
                turnedAwayCount++;
            }
        }

        clientPool.shutdown();
        assertTrue(clientPool.awaitTermination(5, TimeUnit.SECONDS));

        shop.close(true);
        shop.awaitTermination(Duration.ofSeconds(2));

        // Alle 500 kunder skal være regnskapsført
        assertEquals(totalCustomers, servedCount + turnedAwayCount,
                "Summen av betjente og avviste kunder må være nøyaktig lik totalt antall kunder!");
        assertEquals(servedCount, shop.getTotalCustomersServed());
        assertEquals(turnedAwayCount, shop.getTotalCustomersTurnedAway());

        assertTrue(servedCount > 0, "Minst noen kunder må ha blitt betjent");
    }
}

