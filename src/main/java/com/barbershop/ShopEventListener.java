package com.barbershop;

/**
 * Lyttergrensesnitt for å motta hendelser fra barbersalongen.
 */
@FunctionalInterface
public interface ShopEventListener {
    void onEvent(ShopEvent event);
}

