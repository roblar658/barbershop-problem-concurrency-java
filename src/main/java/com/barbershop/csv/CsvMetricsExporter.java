package com.barbershop.csv;

import com.barbershop.CustomerResult;
import com.barbershop.starvation.StarvationReport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Eksporterer og leser CSV-filer for stresstesting, målinger og analyser.
 */
public class CsvMetricsExporter {

    public static final String CUSTOMER_HEADER =
            "customer_id,customer_name,status,arrival_time_ms,service_start_time_ms,completion_time_ms,wait_time_ms,haircut_duration_ms,total_duration_ms,barber_name,queue_depth_on_arrival";

    public static final String SUMMARY_HEADER = "metric,value";

    /**
     * Eksporterer alle kunders resultater til en detaljert CSV-fil.
     */
    public static void exportCustomerMetrics(List<CustomerResult> results, Path destination) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(CUSTOMER_HEADER);
            writer.newLine();

            for (CustomerResult r : results) {
                String line = String.format("%d,%s,%s,%d,%d,%d,%d,%d,%d,%s,%d",
                        r.getCustomer().getId(),
                        escapeCsv(r.getCustomer().getName()),
                        r.getStatus(),
                        r.getArrivalTimeMs(),
                        r.getServiceStartTimeMs(),
                        r.getCompletionTimeMs(),
                        r.getWaitTimeMs(),
                        r.getHaircutDurationMs(),
                        r.getTotalDurationMs(),
                        escapeCsv(r.getBarberName() != null ? r.getBarberName() : ""),
                        r.getQueueDepthOnArrival()
                );
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Eksporterer en sammendragsrapport til CSV.
     */
    public static void exportSummaryMetrics(StarvationReport report, long totalElapsedMs, Path destination) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        double elapsedSeconds = Math.max(0.001, totalElapsedMs / 1000.0);
        double throughputServedPerSec = report.servedCount() / elapsedSeconds;

        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(SUMMARY_HEADER);
            writer.newLine();

            writeMetric(writer, "total_customers", report.totalAnalyzed());
            writeMetric(writer, "served_customers", report.servedCount());
            writeMetric(writer, "turned_away_or_other", report.totalAnalyzed() - report.servedCount());
            writeMetric(writer, "overtaken_customers", report.overtakenCount());
            writeMetric(writer, "starved_customers", report.starvedCount());
            writeMetric(writer, "is_starvation_free", report.isStarvationFree());
            writeMetric(writer, "mean_wait_ms", String.format(java.util.Locale.US, "%.2f", report.meanWaitMs()));
            writeMetric(writer, "p95_wait_ms", String.format(java.util.Locale.US, "%.2f", report.p95WaitMs()));
            writeMetric(writer, "p99_wait_ms", String.format(java.util.Locale.US, "%.2f", report.p99WaitMs()));
            writeMetric(writer, "max_observed_wait_ms", report.maxObservedWaitMs());
            writeMetric(writer, "theoretical_max_wait_ms", report.theoreticalMaxWaitMs());
            writeMetric(writer, "total_elapsed_ms", totalElapsedMs);
            writeMetric(writer, "throughput_served_per_sec", String.format(java.util.Locale.US, "%.2f", throughputServedPerSec));
        }
    }

    private static void writeMetric(BufferedWriter writer, String key, Object val) throws IOException {
        writer.write(key + "," + val);
        writer.newLine();
    }

    /**
     * Leser en CSV-arbeidsbelastningsfil.
     * Forventer header: customer_id,customer_name,arrival_delay_ms,haircut_duration_ms
     */
    public static List<CustomerWorkloadItem> readWorkloadFromCsv(Path csvPath) throws IOException {
        List<CustomerWorkloadItem> items = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) return items;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    int id = Integer.parseInt(parts[0].trim());
                    String name = parts[1].trim();
                    long arrivalDelayMs = Long.parseLong(parts[2].trim());
                    long haircutDurationMs = Long.parseLong(parts[3].trim());
                    items.add(new CustomerWorkloadItem(id, name, arrivalDelayMs, haircutDurationMs));
                }
            }
        }
        return items;
    }

    /**
     * Skriver en syntetisk scenario-CSV-fil for tester.
     */
    public static void writeWorkloadCsv(List<CustomerWorkloadItem> items, Path destination) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write("customer_id,customer_name,arrival_delay_ms,haircut_duration_ms");
            writer.newLine();
            for (CustomerWorkloadItem item : items) {
                writer.write(String.format("%d,%s,%d,%d",
                        item.customerId(),
                        escapeCsv(item.customerName()),
                        item.arrivalDelayMs(),
                        item.haircutDurationMs()));
                writer.newLine();
            }
        }
    }

    private static String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
