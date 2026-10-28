package com.barbershop;

/**
 * Resultatet av et kundebesøk hos barbersalongen.
 * Inneholder detaljerte tidsmålinger og køsekvens for analyse, statistikk og CSV-eksport.
 */
public class CustomerResult {
    private final Customer customer;
    private final CustomerStatus status;
    private final long waitTimeMs;
    private final haircutDurationMsWrapper haircutDurationMs;
    private final String barberName;
    private final String message;

    private final long arrivalTimeMs;
    private final long serviceStartTimeMs;
    private final long completionTimeMs;
    private final int queueDepthOnArrival;
    private final long admissionSequenceNumber;
    private final long serviceSequenceNumber;

    private record haircutDurationMsWrapper(long value) {}

    public CustomerResult(Customer customer, CustomerStatus status, long waitTimeMs,
                          long haircutDurationMs, String barberName, String message,
                          long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs,
                          int queueDepthOnArrival, long admissionSequenceNumber,
                          long serviceSequenceNumber) {
        this.customer = customer;
        this.status = status;
        this.waitTimeMs = waitTimeMs;
        this.haircutDurationMs = new haircutDurationMsWrapper(haircutDurationMs);
        this.barberName = barberName;
        this.message = message;
        this.arrivalTimeMs = arrivalTimeMs;
        this.serviceStartTimeMs = serviceStartTimeMs;
        this.completionTimeMs = completionTimeMs;
        this.queueDepthOnArrival = queueDepthOnArrival;
        this.admissionSequenceNumber = admissionSequenceNumber;
        this.serviceSequenceNumber = serviceSequenceNumber;
    }

    public CustomerResult(Customer customer, CustomerStatus status, long waitTimeMs,
                          long haircutDurationMs, String barberName, String message,
                          long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs,
                          int queueDepthOnArrival, long admissionSequenceNumber) {
        this(customer, status, waitTimeMs, haircutDurationMs, barberName, message,
                arrivalTimeMs, serviceStartTimeMs, completionTimeMs, queueDepthOnArrival,
                admissionSequenceNumber, 0L);
    }

    public CustomerResult(Customer customer, CustomerStatus status, long waitTimeMs,
                          long haircutDurationMs, String barberName, String message,
                          long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs,
                          int queueDepthOnArrival) {
        this(customer, status, waitTimeMs, haircutDurationMs, barberName, message,
                arrivalTimeMs, serviceStartTimeMs, completionTimeMs, queueDepthOnArrival, 0L, 0L);
    }

    public CustomerResult(Customer customer, CustomerStatus status, long waitTimeMs,
                          long haircutDurationMs, String barberName, String message) {
        this(customer, status, waitTimeMs, haircutDurationMs, barberName, message,
                customer != null ? customer.getArrivalTimestamp() : System.currentTimeMillis(),
                0, System.currentTimeMillis(), 0, 0L, 0L);
    }

    public static CustomerResult served(Customer customer, long waitTimeMs, long haircutDurationMs,
                                        String barberName, int queueDepthOnArrival,
                                        long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs,
                                        long admissionSequenceNumber, long serviceSequenceNumber) {
        return new CustomerResult(
                customer,
                CustomerStatus.SERVED,
                waitTimeMs,
                haircutDurationMs,
                barberName,
                customer.getName() + " ble klippet av " + barberName + " (ventetid: " + waitTimeMs + "ms, klippetid: " + haircutDurationMs + "ms).",
                arrivalTimeMs,
                serviceStartTimeMs,
                completionTimeMs,
                queueDepthOnArrival,
                admissionSequenceNumber,
                serviceSequenceNumber
        );
    }

    public static CustomerResult served(Customer customer, long waitTimeMs, long haircutDurationMs,
                                        String barberName, int queueDepthOnArrival,
                                        long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs,
                                        long admissionSequenceNumber) {
        return served(customer, waitTimeMs, haircutDurationMs, barberName, queueDepthOnArrival,
                arrivalTimeMs, serviceStartTimeMs, completionTimeMs, admissionSequenceNumber, 0L);
    }

    public static CustomerResult served(Customer customer, long waitTimeMs, long haircutDurationMs,
                                        String barberName, int queueDepthOnArrival,
                                        long arrivalTimeMs, long serviceStartTimeMs, long completionTimeMs) {
        return served(customer, waitTimeMs, haircutDurationMs, barberName, queueDepthOnArrival,
                arrivalTimeMs, serviceStartTimeMs, completionTimeMs, 0L, 0L);
    }

    public static CustomerResult served(Customer customer, long waitTimeMs, long haircutDurationMs, String barberName) {
        long now = System.currentTimeMillis();
        return served(customer, waitTimeMs, haircutDurationMs, barberName, 0,
                customer != null ? customer.getArrivalTimestamp() : now - waitTimeMs - haircutDurationMs,
                now - haircutDurationMs, now, 0L, 0L);
    }

    public static CustomerResult leftBecauseFull(Customer customer, int capacity) {
        long now = System.currentTimeMillis();
        return new CustomerResult(
                customer,
                CustomerStatus.LEFT_BECAUSE_FULL,
                0,
                0,
                null,
                customer.getName() + " forlot salongen fordi alle ventestolene (" + capacity + ") var opptatt.",
                customer != null ? customer.getArrivalTimestamp() : now,
                0,
                now,
                capacity,
                0L,
                0L
        );
    }

    public static CustomerResult leftBecauseFull(Customer customer) {
        return leftBecauseFull(customer, 0);
    }

    public static CustomerResult leftOnTimeout(Customer customer, long waitTimeMs, int queueDepth) {
        long now = System.currentTimeMillis();
        return new CustomerResult(
                customer,
                CustomerStatus.LEFT_ON_TIMEOUT,
                waitTimeMs,
                0,
                null,
                customer.getName() + " forlot salongen etter å ha ventet i " + waitTimeMs + "ms uten å bli betjent.",
                customer != null ? customer.getArrivalTimestamp() : now - waitTimeMs,
                0,
                now,
                queueDepth,
                0L,
                0L
        );
    }

    public static CustomerResult leftOnTimeout(Customer customer, long waitTimeMs) {
        return leftOnTimeout(customer, waitTimeMs, 0);
    }

    public static CustomerResult shopClosed(Customer customer) {
        long now = System.currentTimeMillis();
        return new CustomerResult(
                customer,
                CustomerStatus.SHOP_CLOSED,
                0,
                0,
                null,
                customer.getName() + " ble avvist fordi barbersalongen er stengt.",
                customer != null ? customer.getArrivalTimestamp() : now,
                0,
                now,
                0,
                0L,
                0L
        );
    }

    public Customer getCustomer() {
        return customer;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public boolean isServed() {
        return status == CustomerStatus.SERVED;
    }

    public boolean hasLeftBecauseFull() {
        return status == CustomerStatus.LEFT_BECAUSE_FULL;
    }

    public boolean hasLeftOnTimeout() {
        return status == CustomerStatus.LEFT_ON_TIMEOUT;
    }

    public boolean isShopClosed() {
        return status == CustomerStatus.SHOP_CLOSED;
    }

    public long getWaitTimeMs() {
        return waitTimeMs;
    }

    public long getHaircutDurationMs() {
        return haircutDurationMs.value();
    }

    public long getTotalDurationMs() {
        return waitTimeMs + haircutDurationMs.value();
    }

    public String getBarberName() {
        return barberName;
    }

    public String getMessage() {
        return message;
    }

    public long getArrivalTimeMs() {
        return arrivalTimeMs;
    }

    public long getServiceStartTimeMs() {
        return serviceStartTimeMs;
    }

    public long getCompletionTimeMs() {
        return completionTimeMs;
    }

    public int getQueueDepthOnArrival() {
        return queueDepthOnArrival;
    }

    public long getAdmissionSequenceNumber() {
        return admissionSequenceNumber;
    }

    public long getServiceSequenceNumber() {
        return serviceSequenceNumber;
    }

    @Override
    public String toString() {
        return "CustomerResult{" +
                "customer=" + customer +
                ", status=" + status +
                ", waitTimeMs=" + waitTimeMs +
                ", haircutDurationMs=" + haircutDurationMs.value() +
                ", barberName='" + barberName + '\'' +
                ", queueDepth=" + queueDepthOnArrival +
                ", admissionSeq=" + admissionSequenceNumber +
                ", serviceSeq=" + serviceSequenceNumber +
                ", message='" + message + '\'' +
                '}';
    }
}
