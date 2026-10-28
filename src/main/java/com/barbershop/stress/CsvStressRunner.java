package com.barbershop.stress;

import com.barbershop.*;
import com.barbershop.csv.CsvMetricsExporter;
import com.barbershop.starvation.StarvationAudit;
import com.barbershop.starvation.StarvationReport;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Konsollverktøy for å kjøre stresstesting med høy belastning,
 * verifisere at systemet er fritt for utsulting (starvation-free),
 * og eksportere resultatene til CSV-filer.
 */
public class CsvStressRunner {

    public static void main(String[] args) throws Exception {
        int totalCustomers = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int chairs = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int clientThreads = args.length > 2 ? Integer.parseInt(args[2]) : 20;

        System.out.println("================================================================================");
        System.out.println("          STRESSTEST OG UTSULTINGSANALYSE (SLEEPING BARBER)                     ");
        System.out.println("================================================================================");
        System.out.printf("Parametre: %d kunder | %d ventestoler | %d klienttråder%n", totalCustomers, chairs, clientThreads);
        System.out.println("--------------------------------------------------------------------------------");

        // Rask klipping (0-2 ms) for å simulere høy gjennomstrømning
        HaircutStrategy strategy = HaircutStrategy.random(Duration.ofMillis(0), Duration.ofMillis(2));
        BarberShop shop = new BarberShop(chairs, strategy);
        shop.open();

        ExecutorService pool = Executors.newFixedThreadPool(clientThreads);
        List<Future<CustomerResult>> futures = new ArrayList<>(totalCustomers);

        System.out.printf("Sender inn %d kunder fordelt på %d tråder...%n", totalCustomers, clientThreads);
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= totalCustomers; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "StressKunde-" + id))));
        }

        List<CustomerResult> results = new ArrayList<>(totalCustomers);
        for (Future<CustomerResult> f : futures) {
            results.add(f.get());
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        shop.close(true);
        shop.awaitTermination(Duration.ofSeconds(2));

        System.out.printf("Kjøring fullført på %d ms (%.2f kunder/sekund)%n%n",
                elapsedTime, (totalCustomers * 1000.0) / Math.max(1, elapsedTime));

        // Utfør formell analyse av utsulting
        StarvationReport report = StarvationAudit.audit(results, chairs, 5);
        System.out.println(report);

        // Eksporter til CSV-filer i mappen data/
        Path dataDir = Path.of("data");
        Path customerCsv = dataDir.resolve("customers_metrics.csv");
        Path summaryCsv = dataDir.resolve("stress_summary.csv");

        CsvMetricsExporter.exportCustomerMetrics(results, customerCsv);
        CsvMetricsExporter.exportSummaryMetrics(report, elapsedTime, summaryCsv);

        System.out.println("================================================================================");
        System.out.println("                         CSV-EKSPORT FULLFØRT                                   ");
        System.out.println("================================================================================");
        System.out.printf("Detaljert kundemetrikk: %s (%d rader)%n", customerCsv.toAbsolutePath(), results.size() + 1);
        System.out.printf("Sammendragsrapport:     %s%n", summaryCsv.toAbsolutePath());
        System.out.println("================================================================================");
    }
}

