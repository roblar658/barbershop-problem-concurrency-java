package com.barbershop;

/**
 * Typer hendelser som kan inntreffe i barbersalongen.
 */
public enum ShopEventType {
    SHOP_OPENED,
    SHOP_CLOSED,
    BARBER_FELL_ASLEEP,
    BARBER_WOKEN_UP,
    CUSTOMER_ARRIVED,
    CUSTOMER_SEATED_IN_WAITING_ROOM,
    CUSTOMER_TURNED_AWAY_FULL,
    CUSTOMER_CALLED_TO_CHAIR,
    HAIRCUT_STARTED,
    HAIRCUT_COMPLETED,
    CUSTOMER_DEPARTED,
    CUSTOMER_TIMEOUT_LEFT
}

