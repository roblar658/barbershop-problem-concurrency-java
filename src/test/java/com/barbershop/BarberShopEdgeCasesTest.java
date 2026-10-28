package com.barbershop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tester for grensetilfeller, feilhåndtering og parameter-validering")
class BarberShopEdgeCasesTest {

    @Test
    @DisplayName("Ugyldige konstruktørparametre skal kaste IllegalArgumentException / NullPointerException")
    void invalidConstructorParametersThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new BarberShop(-1));
        assertThrows(IllegalArgumentException.class, () -> new BarberShop(3, 0, HaircutStrategy.instant()));
        assertThrows(NullPointerException.class, () -> new BarberShop(3, null));
        assertThrows(NullPointerException.class, () -> new Customer(1, null));
    }

    @Test
    @DisplayName("Null-kunde til enter eller enterAsync skal kaste NullPointerException")
    void nullCustomerThrowsNpe() {
        BarberShop shop = new BarberShop(2);
        shop.open();

        assertThrows(NullPointerException.class, () -> shop.enter(null));
        assertThrows(NullPointerException.class, () -> shop.enter(null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> shop.enterAsync(null));

        shop.close();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Flere registrerte hendelseslyttere skal alle motta hendelsene")
    void multipleListenersReceiveAllEvents() throws Exception {
        BarberShop shop = new BarberShop(2, HaircutStrategy.instant());
        List<ShopEvent> list1 = new ArrayList<>();
        List<ShopEvent> list2 = new ArrayList<>();

        ShopEventListener l1 = list1::add;
        ShopEventListener l2 = list2::add;

        shop.addListener(l1);
        shop.addListener(l2);
        shop.open();

        shop.enter(new Customer(1, "Test-Kunde"));

        Thread.sleep(50);

        assertFalse(list1.isEmpty());
        assertEquals(list1.size(), list2.size(), "Begge lyttere skal motta nøyaktig samme antall hendelser");

        // Fjern lytter 1 og verifiser at lytter 2 fortsetter å motta
        shop.removeListener(l1);
        int l1CountBefore = list1.size();
        shop.enter(new Customer(2, "Test-Kunde-2"));
        Thread.sleep(50);

        assertEquals(l1CountBefore, list1.size(), "Fjernet lytter skal ikke motta nye hendelser");
        assertTrue(list2.size() > list1.size());

        shop.close();
    }

    @Test
    @DisplayName("getEventHistory() skal returnere en uforanderlig liste")
    void eventHistoryIsUnmodifiable() {
        BarberShop shop = new BarberShop(2);
        shop.open();

        List<ShopEvent> history = shop.getEventHistory();
        assertThrows(UnsupportedOperationException.class, () -> history.add(null));

        shop.close();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Trådavbrudd av en ventende kundetråd skal håndteres ryddig")
    void customerThreadInterruptionHandledGracefully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        BarberShop shop = new BarberShop(1, HaircutStrategy.controlled(latch));
        shop.open();

        // Kunde 1 okkuperer barberstolen
        new Thread(() -> {
            try {
                shop.enter(new Customer(1, "Stol-Kunde"));
            } catch (InterruptedException ignored) {}
        }).start();

        Thread.sleep(50);

        // Kunde 2 setter seg på ventestolen, men avbrytes
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        Thread customer2Thread = new Thread(() -> {
            try {
                shop.enter(new Customer(2, "Avbrutt-Kunde"));
            } catch (InterruptedException e) {
                wasInterrupted.set(true);
            }
        });

        customer2Thread.start();
        Thread.sleep(50);

        customer2Thread.interrupt();
        customer2Thread.join(1000);

        assertTrue(wasInterrupted.get(), "Kundetråden skal fange InterruptedException");

        latch.countDown();
        shop.close();
        shop.awaitTermination(Duration.ofSeconds(1));
    }
}

