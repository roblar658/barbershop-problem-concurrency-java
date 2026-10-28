package com.barbershop;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Intern forespørsel/billett som representerer en kunde i venterommet eller i barberstolen.
 */
class CustomerRequest {
    private final Customer customer;
    private final long waitingStartTimeMs;
    private final int queueDepthOnArrival;
    private final long admissionSequenceNumber;
    private volatile long serviceSequenceNumber = 0L;
    private volatile long serviceStartTimeMs;
    private final CompletableFuture<CustomerResult> future = new CompletableFuture<>();

    private volatile boolean assigned = false;
    private volatile boolean cancelled = false;
    private volatile Barber assignedBarber = null;

    public CustomerRequest(Customer customer, int queueDepthOnArrival, long admissionSequenceNumber) {
        this.customer = customer;
        this.queueDepthOnArrival = queueDepthOnArrival;
        this.admissionSequenceNumber = admissionSequenceNumber;
        this.waitingStartTimeMs = System.currentTimeMillis();
    }

    public CustomerRequest(Customer customer, int queueDepthOnArrival) {
        this(customer, queueDepthOnArrival, 0L);
    }

    public CustomerRequest(Customer customer) {
        this(customer, 0, 0L);
    }

    public Customer getCustomer() {
        return customer;
    }

    public long getWaitingStartTimeMs() {
        return waitingStartTimeMs;
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

    public long getServiceStartTimeMs() {
        return serviceStartTimeMs;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Barber getAssignedBarber() {
        return assignedBarber;
    }

    public void markAssigned(Barber barber, long serviceSequenceNumber) {
        this.assigned = true;
        this.assignedBarber = barber;
        this.serviceSequenceNumber = serviceSequenceNumber;
        this.serviceStartTimeMs = System.currentTimeMillis();
    }

    public void markAssigned(Barber barber) {
        markAssigned(barber, 0L);
    }

    public void markCancelled(CustomerResult result) {
        this.cancelled = true;
        future.complete(result);
    }

    public void complete(CustomerResult result) {
        future.complete(result);
    }

    public CompletableFuture<CustomerResult> getFuture() {
        return future;
    }

    public CustomerResult awaitResult() throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Uventet feil ved venting på klipp", cause);
        }
    }

    public CustomerResult awaitResult(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Uventet feil ved venting på klipp", cause);
        }
    }
}
