package com.barbershop;

import java.time.Instant;

/**
 * En hendelsespost som registreres når tilstanden i salongen endres.
 * Nyttig for hendelseslogger, sanntidsvisning og verifisering i tester.
 */
public record ShopEvent(
        long sequenceNumber,
        Instant timestamp,
        ShopEventType type,
        Customer customer,
        String barberName,
        int waitingCustomerCount,
        String description
) {
    @Override
    public String toString() {
        String custStr = customer != null ? customer.getName() : "-";
        String barbStr = barberName != null ? barberName : "-";
        return String.format("[%s | #%04d] %-32s | Barber: %-12s | Kunde: %-14s | Venterom: %d | %s",
                timestamp, sequenceNumber, type, barbStr, custStr, waitingCustomerCount, description);
    }
}

