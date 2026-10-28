package com.barbershop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tester for salongens livssyklus og stenging (Shutdown)")
class BarberShopShutdownTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Rolig stenging (close(true)) skal betjene alle som allerede sitter i venterommet")
    void gracefulShutdownCompletesPendingCustomers() throws Exception {
        BarberShop shop = new BarberShop(3, HaircutStrategy.fixed(Duration.ofMillis(30)));
        shop.open();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        Future<CustomerResult> f1 = pool.submit(() -> shop.enter(new Customer(1, "Kunde-1")));
        Future<CustomerResult> f2 = pool.submit(() -> shop.enter(new Customer(2, "Kunde-2")));
        Future<CustomerResult> f3 = pool.submit(() -> shop.enter(new Customer(3, "Kunde-3")));

        Thread.sleep(20);

        // Steng salongen kontrollert (skal fullføre de ventende)
        shop.close(true);

        // Ny ankommende kunde etter stenging skal avvises
        CustomerResult lateResult = shop.enter(new Customer(4, "Forsinket-Kunde"));
        assertEquals(CustomerStatus.SHOP_CLOSED, lateResult.getStatus());

        // De 3 opprinnelige kundene skal alle bli klippet ferdig
        assertTrue(f1.get().isServed());
        assertTrue(f2.get().isServed());
        assertTrue(f3.get().isServed());

        // Barberer-tråden skal avslutte pent
        boolean terminated = shop.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated, "Barbereren skal terminere etter at køen er tømt");
        assertTrue(shop.getBarber().isStopped());

        pool.shutdown();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Umiddelbar stenging (close(false)) skal avbryte ventende kunder umiddelbart")
    void immediateShutdownRejectsWaitingCustomers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        BarberShop shop = new BarberShop(2, HaircutStrategy.controlled(latch));
        shop.open();

        ExecutorService pool = Executors.newFixedThreadPool(3);

        // Kunde 1 okkuperer barberstolen
        pool.submit(() -> shop.enter(new Customer(1, "I-Stolen")));
        Thread.sleep(40);

        // Kunde 2 venter i venterommet
        Future<CustomerResult> fWaiting = pool.submit(() -> shop.enter(new Customer(2, "Venter")));
        Thread.sleep(40);

        // Nødstenging: steng umiddelbart uten å betjene ventende
        shop.close(false);

        CustomerResult waitingResult = fWaiting.get(1, TimeUnit.SECONDS);
        assertEquals(CustomerStatus.SHOP_CLOSED, waitingResult.getStatus());

        latch.countDown();
        shop.awaitTermination(Duration.ofSeconds(1));
        pool.shutdown();
    }
}

