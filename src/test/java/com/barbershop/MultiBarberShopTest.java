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

@DisplayName("Tester for utvidelse med flere barberere (Multi-Barber)")
class MultiBarberShopTest {

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
    @DisplayName("Alle barberere skal sove når salongen åpner uten kunder")
    void allBarbersSleepWhenShopIsEmpty() throws Exception {
        shop = new BarberShop(5, 3, HaircutStrategy.instant());
        shop.open();

        Thread.sleep(150);

        List<Barber> barbers = shop.getBarbers();
        assertEquals(3, barbers.size());
        for (Barber barber : barbers) {
            assertTrue(barber.isSleeping(), barber.getName() + " skal sove når salongen er tom");
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("3 samtidige kunder skal vekke alle 3 barberere og klippes i parallell")
    void threeBarbersCutConcurrently() throws Exception {
        CountDownLatch simultaneousLatch = new CountDownLatch(3);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        // Hver barberer melder inn at klipping har startet, og venter på felles release
        HaircutStrategy strategy = (customer, barber) -> {
            simultaneousLatch.countDown();
            releaseLatch.await();
            return 10L;
        };

        shop = new BarberShop(4, 3, strategy);
        shop.open();

        Thread.sleep(80);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<CustomerResult>> futures = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "Parallell-" + id))));
        }

        // Vent til alle 3 barberere klipper samtidig
        assertTrue(simultaneousLatch.await(2, TimeUnit.SECONDS), "Alle 3 barberere må være i gang samtidig");

        for (Barber barber : shop.getBarbers()) {
            assertEquals(BarberState.CUTTING_HAIR, barber.getState(), barber.getName() + " må klippe");
        }

        releaseLatch.countDown();

        for (Future<CustomerResult> f : futures) {
            assertTrue(f.get().isServed());
        }

        assertEquals(3, shop.getTotalCustomersServed());
        pool.shutdown();
    }
}

