package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Grunnleggende tester for BarberShop")
class BarberShopBasicTest {

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
    @DisplayName("Barbereren skal sove når salongen åpner uten ventende kunder")
    void barberSleepsWhenShopIsEmpty() throws Exception {
        shop = new BarberShop(3, HaircutStrategy.instant());
        shop.open();

        // Gi tråden et øyeblikk til å starte og registrere at venterommet er tomt
        Thread.sleep(150);

        Barber barber = shop.getBarber();
        assertEquals(BarberState.SLEEPING, barber.getState(), "Barbereren skal sove når ingen kunder venter");
        assertTrue(barber.isSleeping());
        assertEquals(0, shop.getWaitingCustomerCount());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Én ankommende kunde skal vekke barbereren, bli klippet, og barbereren skal sovne igjen")
    void singleCustomerWakesBarberAndGetsServed() throws Exception {
        shop = new BarberShop(3, HaircutStrategy.fixed(Duration.ofMillis(80)));
        shop.open();

        Thread.sleep(100);
        assertTrue(shop.getBarber().isSleeping(), "Barbereren må sove før kunden ankommer");

        Customer customer = new Customer(1, "Ola");
        CustomerResult result = shop.enter(customer);

        assertNotNull(result);
        assertEquals(CustomerStatus.SERVED, result.getStatus());
        assertTrue(result.isServed());
        assertEquals("Ola", result.getCustomer().getName());
        assertEquals(1, shop.getTotalCustomersServed());
        assertEquals(0, shop.getTotalCustomersTurnedAway());

        // Etter at klippen er ferdig og venterommet er tomt, må barbereren sovne igjen
        Thread.sleep(100);
        assertTrue(shop.getBarber().isSleeping(), "Barbereren skal gå tilbake til å sove");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Flere kunder på rad skal alle bli betjent og vekke/la barbereren sovne sekvensielt")
    void multipleSequentialCustomersAllServed() throws Exception {
        shop = new BarberShop(2, HaircutStrategy.fixed(Duration.ofMillis(30)));
        shop.open();

        for (int i = 1; i <= 5; i++) {
            Customer customer = new Customer(i, "Kunde-" + i);
            CustomerResult result = shop.enter(customer);
            assertTrue(result.isServed(), "Kunde " + i + " skal bli betjent");
        }

        assertEquals(5, shop.getTotalCustomersServed());
        assertEquals(0, shop.getTotalCustomersTurnedAway());

        Thread.sleep(100);
        assertTrue(shop.getBarber().isSleeping());
    }

    @Test
    @DisplayName("Kunde som ankommer en stengt salong skal få SHOP_CLOSED")
    void customerArrivingAtClosedShopGetsRejected() throws Exception {
        shop = new BarberShop(3, HaircutStrategy.instant());
        // Åpner ikke salongen
        Customer customer = new Customer(1, "Kari");
        CustomerResult result = shop.enter(customer);

        assertEquals(CustomerStatus.SHOP_CLOSED, result.getStatus());
        assertTrue(result.isShopClosed());
        assertEquals(0, shop.getTotalCustomersServed());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Salong med 0 ventestoler skal betjene kunde når barberer sover, men avvise når barberer klipper")
    void zeroWaitingChairsBehavior() throws Exception {
        // 0 ventestoler betyr at ingen kan sitte og vente; hvis barbereren klipper, må neste kunde snu umiddelbart
        shop = new BarberShop(0, HaircutStrategy.fixed(Duration.ofMillis(150)));
        shop.open();

        Thread.sleep(50);

        // Første kunde finner barbereren sovende og skal bli betjent direkte i barberstolen
        Thread customer1 = new Thread(() -> {
            try {
                CustomerResult r = shop.enter(new Customer(1, "Kunde-1"));
                assertTrue(r.isServed());
            } catch (InterruptedException e) {
                fail(e);
            }
        });
        customer1.start();

        // Vent til barbereren er opptatt med kunde 1
        Thread.sleep(50);
        assertTrue(shop.getBarber().isCuttingHair());

        // Kunde 2 ankommer mens barbereren klipper og det er 0 ventestoler -> må snu umiddelbart!
        CustomerResult r2 = shop.enter(new Customer(2, "Kunde-2"));
        assertEquals(CustomerStatus.LEFT_BECAUSE_FULL, r2.getStatus());
        assertTrue(r2.hasLeftBecauseFull());

        customer1.join();
        assertEquals(1, shop.getTotalCustomersServed());
        assertEquals(1, shop.getTotalCustomersTurnedAway());
    }
}

