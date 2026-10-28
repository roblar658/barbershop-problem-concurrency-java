package com.barbershop;

/**
 * Utfallet eller den nåværende statusen til en kunde i barbersalongen.
 */
public enum CustomerStatus {
    /** Kunden venter for øyeblikket på en ventestol. */
    WAITING,

    /** Kunden sitter i barberstolen og blir klippet. */
    IN_CHAIR,

    /** Kunden har blitt ferdig klippet og forlater salongen fornøyd. */
    SERVED,

    /** Alle ventestolene var opptatt da kunden ankom; kunden forlot umiddelbart. */
    LEFT_BECAUSE_FULL,

    /** Kunden ventet for lenge og forlot venterommet på grunn av tidsavbrudd. */
    LEFT_ON_TIMEOUT,

    /** Salongen stengte før eller mens kunden ventet. */
    SHOP_CLOSED
}

