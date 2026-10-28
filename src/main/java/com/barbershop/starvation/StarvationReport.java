package com.barbershop.starvation;

import java.util.List;

/**
 * Resultatrapport fra en analyse av utsulting (starvation) i barbersalongen.
 */
public record StarvationReport(
        int totalAnalyzed,
        int servedCount,
        int overtakenCount,
        int starvedCount,
        long maxObservedWaitMs,
        long theoreticalMaxWaitMs,
        double meanWaitMs,
        double p95WaitMs,
        double p99WaitMs,
        boolean isStarvationFree,
        List<String> violations
) {
    @Override
    public String toString() {
        return String.format("""
                === ANALYSERAPPORT FOR UTSULTING (STARVATION) ===
                Totalt analysert:       %d kunder
                Betjente kunder:        %d
                Forbikjørte kunder:     %d (må være 0)
                Utsultede kunder:       %d (må være 0)
                Maks observert ventetid: %d ms
                Teoretisk maksgrense:   %d ms
                Snitt ventetid:         %.2f ms
                P95 ventetid:           %.2f ms
                P99 ventetid:           %.2f ms
                Utsultingsfri (Starvation-free): %s
                Avvik funnet:           %d
                """,
                totalAnalyzed, servedCount, overtakenCount, starvedCount,
                maxObservedWaitMs, theoreticalMaxWaitMs, meanWaitMs, p95WaitMs, p99WaitMs,
                isStarvationFree ? "JA (100% Starvation-free)" : "NEI (Utsulting påvist)",
                violations.size()
        );
    }
}

