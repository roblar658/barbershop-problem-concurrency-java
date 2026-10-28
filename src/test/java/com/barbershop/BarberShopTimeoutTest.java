package com.barbershop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tester for kunders tidsavbrudd (Reneging)")
class BarberShopTimeoutTest {

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
    @DisplayName("Kunde som venter lenger enn sin maksimale ventetid skal forlate venterommet og frigjøre stolen")
    void customerAbandonsWaitingRoomOnTimeout() throws Exception {
        CountDownLatch longHaircutLatch = new CountDownLatch(1);

        // Barberer klipper kunde 1 uendelig helt til latch åpnes
        HaircutStrategy strategy = (customer, barber) -> {
            if (customer.getId() == 1) {
                longHaircutLatch.await();
            }
            return 10L;
        };

        shop = new BarberShop(1, strategy);
        shop.open();

        Thread.sleep(40);

        // Kunde 1 okkuperer barberstolen
        new Thread(() -> {
            try {
                shop.enter(new Customer(1, "Kunde-1"));
            } catch (InterruptedException ignored) {
            }
        }).start();

        Thread.sleep(50);
        assertTrue(shop.getBarber().isCuttingHair());

        // Kunde 2 setter seg på den ene ventestolen, men har bare tålmodighet i 100 ms
        CustomerResult r2 = shop.enter(new Customer(2, "Utålmodig-Kunde"), Duration.ofMillis(100));

        assertEquals(CustomerStatus.LEFT_ON_TIMEOUT, r2.getStatus());
        assertTrue(r2.hasLeftOnTimeout());
        assertEquals(1, shop.getTotalCustomersTimedOut());

        // Verifiser at ventestolen nå er ledig igjen!
        assertEquals(0, shop.getWaitingCustomerCount());

        // Kunde 3 kan nå ta den ledige ventestolen!
        CustomerResult r3FullCheck = shop.enter(new Customer(3, "Kunde-3"), Duration.ofMillis(300));
        assertEquals(CustomerStatus.LEFT_ON_TIMEOUT, r3FullCheck.getStatus());

        // Frigi kunde 1
        longHaircutLatch.countDown();
    }
}

