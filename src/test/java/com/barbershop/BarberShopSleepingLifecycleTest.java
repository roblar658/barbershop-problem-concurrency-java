package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tester for barbererens søvn- og oppvåkningssyklus")
class BarberShopSleepingLifecycleTest {

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
    @DisplayName("Hendelsesloggen skal bekrefte at barbereren sover, våkner, klipper og sovner igjen")
    void eventLogConfirmsSleepWakeCycle() throws Exception {
        List<ShopEventType> recordedEvents = new CopyOnWriteArrayList<>();

        shop = new BarberShop(2, HaircutStrategy.fixed(Duration.ofMillis(50)));
        shop.addListener(e -> recordedEvents.add(e.type()));
        shop.open();

        Thread.sleep(100);
        assertTrue(recordedEvents.contains(ShopEventType.BARBER_FELL_ASLEEP), "Barbereren må ha sovnet");

        CustomerResult res = shop.enter(new Customer(1, "Sverre"));
        assertTrue(res.isServed());

        Thread.sleep(100);

        // Verifiser at vekking, start på klipp, fullføring og ny sovning forekom
        assertTrue(recordedEvents.contains(ShopEventType.BARBER_WOKEN_UP), "Må ha blitt vekket av kunden");
        assertTrue(recordedEvents.contains(ShopEventType.HAIRCUT_STARTED), "Klipping må ha startet");
        assertTrue(recordedEvents.contains(ShopEventType.HAIRCUT_COMPLETED), "Klipping må ha blitt fullført");
        assertTrue(recordedEvents.contains(ShopEventType.CUSTOMER_DEPARTED), "Kunde må ha forlatt salongen");

        // Det skal være minst to BARBER_FELL_ASLEEP (ved åpning og etter at Sverre gikk)
        long fellAsleepCount = recordedEvents.stream().filter(t -> t == ShopEventType.BARBER_FELL_ASLEEP).count();
        assertTrue(fellAsleepCount >= 2, "Barbereren skal ha sovnet minst 2 ganger");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Gjentatte sykluser mellom søvn og oppvåkning skal fungere stabilt")
    void repeatedSleepWakeCycles() throws Exception {
        shop = new BarberShop(2, HaircutStrategy.fixed(Duration.ofMillis(20)));
        shop.open();

        for (int cycle = 1; cycle <= 4; cycle++) {
            Thread.sleep(60);
            assertTrue(shop.getBarber().isSleeping(), "Syklus " + cycle + ": Barbereren må sove når venterommet er tomt");

            CustomerResult res = shop.enter(new Customer(cycle, "Kunde-" + cycle));
            assertTrue(res.isServed(), "Syklus " + cycle + ": Kunden må bli betjent");
        }

        Thread.sleep(60);
        assertTrue(shop.getBarber().isSleeping(), "Må sove etter siste syklus");
        assertEquals(4, shop.getTotalCustomersServed());
    }
}

