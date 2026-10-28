package com.barbershop.csv;

/**
 * Representerer en kildepost fra en CSV-arbeidsbelastningsfil.
 */
public record CustomerWorkloadItem(
        int customerId,
        String customerName,
        long arrivalDelayMs,
        long haircutDurationMs
) {
}

