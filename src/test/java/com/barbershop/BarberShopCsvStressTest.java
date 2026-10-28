package com.barbershop;

import com.barbershop.csv.CsvMetricsExporter;
import com.barbershop.csv.CustomerWorkloadItem;
import com.barbershop.starvation.StarvationAudit;
import com.barbershop.starvation.StarvationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stresstesting med generering og avspilling av CSV-filer")
class BarberShopCsvStressTest {

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Stresstest med 500 samtidige kunder skal eksportere gyldige CSV-filer for målinger og sammendrag")
    void testHighVolumeStressExportsDetailedCustomerCsv() throws Exception {
        int chairs = 4;
        BarberShop shop = new BarberShop(chairs, HaircutStrategy.instant());
        shop.open();

        int totalCustomers = 500;
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<CustomerResult>> futures = new ArrayList<>(totalCustomers);

        long start = System.currentTimeMillis();

        for (int i = 1; i <= totalCustomers; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "CSV-Stress-" + id))));
        }

        List<CustomerResult> results = new ArrayList<>(totalCustomers);
        for (Future<CustomerResult> f : futures) {
            results.add(f.get());
        }

        long elapsed = System.currentTimeMillis() - start;
        pool.shutdown();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));

        shop.close(true);
        shop.awaitTermination(Duration.ofSeconds(2));

        // Analyser for utsulting
        StarvationReport report = StarvationAudit.audit(results, chairs, 0);
        assertTrue(report.isStarvationFree(), "Stresstesten skal være fri for utsulting");

        // Eksporter til CSV-filer
        Path customersCsv = Path.of("target", "stress-results", "stress_customers.csv");
        Path summaryCsv = Path.of("target", "stress-results", "stress_summary.csv");

        CsvMetricsExporter.exportCustomerMetrics(results, customersCsv);
        CsvMetricsExporter.exportSummaryMetrics(report, elapsed, summaryCsv);

        // Verifiser at filene eksisterer og har innhold
        assertTrue(Files.exists(customersCsv), "Kunde-CSV skal være opprettet");
        assertTrue(Files.exists(summaryCsv), "Sammendrags-CSV skal være opprettet");

        List<String> customerLines = Files.readAllLines(customersCsv);
        // Header + 500 kunder = 501 linjer
        assertEquals(totalCustomers + 1, customerLines.size(), "CSV skal inneholde nøyaktig 500 kuderader pluss header");
        assertEquals(CsvMetricsExporter.CUSTOMER_HEADER, customerLines.get(0));

        List<String> summaryLines = Files.readAllLines(summaryCsv);
        assertTrue(summaryLines.stream().anyMatch(l -> l.contains("is_starvation_free,true")),
                "Sammendrags-CSV må bekrefte at systemet er starvation-free");
        assertTrue(summaryLines.stream().anyMatch(l -> l.contains("total_customers," + totalCustomers)));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Skal kunne lese et scenario fra en CSV-fil, utføre arbeidsbelastningen og eksportere resultat-CSV")
    void testReplayScenarioFromCsvFile() throws Exception {
        Path inputCsv = Path.of("target", "test-workloads", "scenario_input.csv");

        // Opprett en syntetisk scenario-CSV med varierende ankomsttider og klippetider
        List<CustomerWorkloadItem> scenario = List.of(
                new CustomerWorkloadItem(1, "Alice", 0, 10),
                new CustomerWorkloadItem(2, "Bob", 5, 10),
                new CustomerWorkloadItem(3, "Charlie", 5, 10),
                new CustomerWorkloadItem(4, "Dave", 5, 10),
                new CustomerWorkloadItem(5, "Eva", 5, 10),
                new CustomerWorkloadItem(6, "Frank", 0, 10) // Full bølge
        );

        CsvMetricsExporter.writeWorkloadCsv(scenario, inputCsv);
        assertTrue(Files.exists(inputCsv));

        // Les scenarioet tilbake fra CSV
        List<CustomerWorkloadItem> loadedItems = CsvMetricsExporter.readWorkloadFromCsv(inputCsv);
        assertEquals(scenario.size(), loadedItems.size());

        // Kjør arbeidsbelastningen mot en BarberShop med 2 ventestoler
        int chairs = 2;
        BarberShop shop = new BarberShop(chairs, (customer, barber) -> {
            // Bruk klippetid spesifisert per kunde i scenarioet
            CustomerWorkloadItem item = loadedItems.stream()
                    .filter(w -> w.customerId() == customer.getId())
                    .findFirst()
                    .orElse(null);
            long duration = item != null ? item.haircutDurationMs() : 10L;
            if (duration > 0) Thread.sleep(duration);
            return duration;
        });

        shop.open();

        ExecutorService runnerPool = Executors.newCachedThreadPool();
        List<Future<CustomerResult>> futures = new ArrayList<>();

        for (CustomerWorkloadItem item : loadedItems) {
            if (item.arrivalDelayMs() > 0) {
                Thread.sleep(item.arrivalDelayMs());
            }
            futures.add(runnerPool.submit(() -> shop.enter(new Customer(item.customerId(), item.customerName()))));
        }

        List<CustomerResult> replayResults = new ArrayList<>();
        for (Future<CustomerResult> f : futures) {
            replayResults.add(f.get());
        }

        runnerPool.shutdown();
        shop.close(true);
        shop.awaitTermination(Duration.ofSeconds(1));

        // Eksporter resultatene fra CSV-avspillingen
        Path replayCsv = Path.of("target", "stress-results", "replay_results.csv");
        CsvMetricsExporter.exportCustomerMetrics(replayResults, replayCsv);

        assertTrue(Files.exists(replayCsv));
        List<String> resultLines = Files.readAllLines(replayCsv);
        assertEquals(scenario.size() + 1, resultLines.size());

        // Verifiser at minst noen ble betjent og at ingen ble utsultet
        StarvationReport report = StarvationAudit.audit(replayResults, chairs, 20);
        assertTrue(report.isStarvationFree());
    }
}

