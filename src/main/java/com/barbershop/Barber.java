package com.barbershop;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Representerer en barberer (arbeidertråd) som klipper hår eller sover.
 *
 * <p>I henhold til oppgaven:
 * "A barber cuts hair when customers exist; otherwise, the barber sleeps."
 */
public class Barber implements Runnable {
    private final int id;
    private final String name;
    private final BarberShop shop;

    private final AtomicReference<BarberState> state = new AtomicReference<>(BarberState.IDLE);
    private final AtomicReference<Customer> currentCustomer = new AtomicReference<>(null);
    private final AtomicInteger haircutsDone = new AtomicInteger(0);

    private volatile Thread workerThread;

    public Barber(int id, String name, BarberShop shop) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "Navn kan ikke være null");
        this.shop = Objects.requireNonNull(shop, "Barbershop kan ikke være null");
    }

    public Barber(int id, BarberShop shop) {
        this(id, "Barber-" + id, shop);
    }

    @Override
    public void run() {
        this.workerThread = Thread.currentThread();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                CustomerRequest request = shop.getNextCustomer(this);
                if (request == null) {
                    // Salongen er stengt og det er ingen flere ventende kunder.
                    break;
                }

                Customer customer = request.getCustomer();
                currentCustomer.set(customer);

                try {
                    long duration = shop.getHaircutStrategy().performHaircut(customer, this);
                    shop.finishHaircut(this, request, duration);
                    haircutsDone.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } finally {
                    currentCustomer.set(null);
                }
            }
        } finally {
            state.set(BarberState.STOPPED);
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BarberState getState() {
        return state.get();
    }

    void setState(BarberState newState) {
        this.state.set(newState);
    }

    public boolean isSleeping() {
        return state.get() == BarberState.SLEEPING;
    }

    public boolean isCuttingHair() {
        return state.get() == BarberState.CUTTING_HAIR;
    }

    public boolean isStopped() {
        return state.get() == BarberState.STOPPED;
    }

    public Customer getCurrentCustomer() {
        return currentCustomer.get();
    }

    public int getHaircutsDone() {
        return haircutsDone.get();
    }

    public Thread getWorkerThread() {
        return workerThread;
    }

    @Override
    public String toString() {
        return name + " [Tilstand: " + state.get() + ", Klipper nå: " + currentCustomer.get() + "]";
    }
}
