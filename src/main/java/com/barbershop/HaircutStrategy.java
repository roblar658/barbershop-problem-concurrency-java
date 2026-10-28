package com.barbershop;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strategi for hvor lang tid en hårklipp tar og hvordan den utføres.
 */
@FunctionalInterface
public interface HaircutStrategy {

    /**
     * Utfører selve hårklippen.
     *
     * @param customer kunden som klippes
     * @param barber barbereren som klipper
     * @return faktisk varighet i millisekunder
     * @throws InterruptedException hvis tråden avbrytes under klippen
     */
    long performHaircut(Customer customer, Barber barber) throws InterruptedException;

    /**
     * Fast klippetid.
     */
    static HaircutStrategy fixed(Duration duration) {
        Objects.requireNonNull(duration, "Varighet kan ikke være null");
        long millis = Math.max(0, duration.toMillis());
        return (customer, barber) -> {
            if (millis > 0) {
                Thread.sleep(millis);
            }
            return millis;
        };
    }

    /**
     * Tilfeldig klippetid mellom min og max.
     */
    static HaircutStrategy random(Duration min, Duration max) {
        long minMillis = Math.max(0, min.toMillis());
        long maxMillis = Math.max(minMillis, max.toMillis());
        return (customer, barber) -> {
            long duration = minMillis == maxMillis
                    ? minMillis
                    : ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
            if (duration > 0) {
                Thread.sleep(duration);
            }
            return duration;
        };
    }

    /**
     * Umiddelbar klipping (0 millisekunder). Perfekt for stresstester.
     */
    static HaircutStrategy instant() {
        return (customer, barber) -> 0L;
    }

    /**
     * Manuell klipping styrt av en ekstern CountDownLatch.
     * Barbereren venter på at testen frigjør latch-en.
     */
    static HaircutStrategy controlled(CountDownLatch releaseLatch) {
        return (customer, barber) -> {
            releaseLatch.await();
            return 1L;
        };
    }
}

