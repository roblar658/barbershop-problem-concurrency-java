package com.barbershop.starvation;

import com.barbershop.CustomerResult;
import com.barbershop.CustomerStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Revisor for å påvise eller avkrefte utsulting (starvation) i barbersalongen.
 *
 * <p>En kø er fri for utsulting (starvation-free) dersom:
 * <ol>
 *   <li><b>Ikke-forbikjøring (FIFO):</b> En kunde A som aksepteres i venterommet før kunde B
 *       (ankomst A &lt; ankomst B) skal ALDRI bli betjent etter kunde B.</li>
 *   <li><b>Endelig ventetid (Bounded wait):</b> En kunde i venterommet med kapasitet C skal
 *       bli betjent etter maksimalt (C + 1) hårklipp.</li>
 *   <li><b>Null uendelig venting:</b> Ingen kunde som har fått plass i en ventestol skal
 *       forbli ubetjent så lenge salongen er åpen og barbereren jobber.</li>
 * </ol>
 */
public class StarvationAudit {

    /**
     * Utfører en full revisjon av kundebesøk for å avdekke utsulting eller FIFO-brudd.
     *
     * @param results liste over alle kunders resultater
     * @param waitingChairsCapacity kapasiteten på venterommet
     * @param maxHaircutDurationMs maksimal forventet klippetid (eller 0 hvis ukjent)
     * @return en detaljert StarvationReport
     */
    public static StarvationReport audit(List<CustomerResult> results, int waitingChairsCapacity, long maxHaircutDurationMs) {
        if (results == null || results.isEmpty()) {
            return new StarvationReport(0, 0, 0, 0, 0, 0, 0, 0, 0, true, List.of());
        }

        // Filtrer kun kunder som faktisk fikk plass og ble betjent, sortert etter faktisk betjeningssekvens
        List<CustomerResult> served = results.stream()
                .filter(r -> r.getStatus() == CustomerStatus.SERVED)
                .sorted(Comparator.comparingLong(r -> r.getServiceSequenceNumber() > 0
                        ? r.getServiceSequenceNumber()
                        : r.getServiceStartTimeMs()))
                .toList();

        List<String> violations = new ArrayList<>();
        int overtakenCount = 0;
        int starvedCount = 0;
        long maxObservedWaitMs = 0;
        double sumWaitMs = 0;

        List<Long> waitTimes = new ArrayList<>();

        for (int i = 0; i < served.size(); i++) {
            CustomerResult current = served.get(i);
            long waitTime = current.getWaitTimeMs();
            waitTimes.add(waitTime);
            sumWaitMs += waitTime;
            if (waitTime > maxObservedWaitMs) {
                maxObservedWaitMs = waitTime;
            }

            // Sjekk regel 1: Ikke-forbikjøring (FIFO)
            // Ingen kunde som ble opptatt i venterommet ETTER current skal ha blitt betjent FØR current
            for (int j = 0; j < i; j++) {
                CustomerResult earlierServed = served.get(j);
                if (current.getAdmissionSequenceNumber() > 0 && earlierServed.getAdmissionSequenceNumber() > 0) {
                    if (earlierServed.getAdmissionSequenceNumber() > current.getAdmissionSequenceNumber()) {
                        overtakenCount++;
                        violations.add(String.format("FIFO-BRUDD: %s (opptatt #%d) ble betjent etter %s (opptatt #%d)",
                                current.getCustomer().getName(), current.getAdmissionSequenceNumber(),
                                earlierServed.getCustomer().getName(), earlierServed.getAdmissionSequenceNumber()));
                    }
                } else if (earlierServed.getArrivalTimeMs() > current.getArrivalTimeMs()) {
                    overtakenCount++;
                    violations.add(String.format("FIFO-BRUDD: %s (ankom %d) ble betjent etter %s (ankom %d)",
                            current.getCustomer().getName(), current.getArrivalTimeMs(),
                            earlierServed.getCustomer().getName(), earlierServed.getArrivalTimeMs()));
                }
            }
        }

        // Teoretisk øvre grense for ventetid i en FIFO-kø med kapasitet C: (C + 1) * maxHaircutTime
        // Legger til en toleransemargin for trådsvitsjing/CPU-jitter
        long theoreticalMaxWait = maxHaircutDurationMs > 0
                ? (waitingChairsCapacity + 1) * maxHaircutDurationMs + 200
                : 0;

        if (theoreticalMaxWait > 0) {
            for (CustomerResult r : served) {
                if (r.getWaitTimeMs() > theoreticalMaxWait) {
                    starvedCount++;
                    violations.add(String.format("UTSULTING: %s ventet i %d ms, som overskrider teoretisk maks på %d ms",
                            r.getCustomer().getName(), r.getWaitTimeMs(), theoreticalMaxWait));
                }
            }
        }

        // Sorter ventetider for persentilberegning
        waitTimes.sort(Long::compareTo);
        double meanWait = served.isEmpty() ? 0 : sumWaitMs / served.size();
        double p95 = getPercentile(waitTimes, 0.95);
        double p99 = getPercentile(waitTimes, 0.99);

        boolean isStarvationFree = (overtakenCount == 0 && starvedCount == 0);

        return new StarvationReport(
                results.size(),
                served.size(),
                overtakenCount,
                starvedCount,
                maxObservedWaitMs,
                theoreticalMaxWait,
                meanWait,
                p95,
                p99,
                isStarvationFree,
                violations
        );
    }

    private static double getPercentile(List<Long> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }
}
