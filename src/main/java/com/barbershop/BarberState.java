package com.barbershop;

/**
 * Representerer de mulige tilstandene til en barberer.
 */
public enum BarberState {
    /** Barbereren sover fordi det ikke er noen kunder i venteområdet. */
    SLEEPING,

    /** Barbereren klipper hår for en kunde i barberstolen. */
    CUTTING_HAIR,

    /** Barbereren er i ferd med å kalle inn neste kunde eller rydde opp. */
    IDLE,

    /** Barbereren har avsluttet arbeidet (salongen er stengt). */
    STOPPED
}

