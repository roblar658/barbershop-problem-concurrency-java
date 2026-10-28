package com.barbershop;

import java.util.Objects;

/**
 * Representerer en kunde som ankommer barbersalongen.
 */
public class Customer {
    private final int id;
    private final String name;
    private final long arrivalTimestamp;

    public Customer(int id, String name) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "Navn kan ikke være null");
        this.arrivalTimestamp = System.currentTimeMillis();
    }

    public Customer(int id) {
        this(id, "Kunde-" + id);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getArrivalTimestamp() {
        return arrivalTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return id == customer.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return name + " (#" + id + ")";
    }
}

