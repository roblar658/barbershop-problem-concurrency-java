package com.barbershop;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Trådsikker implementasjon av det klassiske "Sleeping Barber"-problemet.
 *
 * <p>Regler:
 * <ul>
 *   <li>En barberer klipper hår når det finnes kunder.</li>
 *   <li>Hvis ingen kunder venter, sover barbereren.</li>
 *   <li>Ankommende kunder tar en ledig ventestol.</li>
 *   <li>Hvis alle ventestolene er opptatt, forlater kunden salongen umiddelbart (balking).</li>
 *   <li>En ankommende kunde vekker barbereren dersom barbereren sover.</li>
 * </ul>
 */
public class BarberShop implements AutoCloseable {

    private final int waitingChairsCapacity;
    private final HaircutStrategy haircutStrategy;
    private final List<Barber> barbers = new CopyOnWriteArrayList<>();
    private final List<Thread> barberThreads = new CopyOnWriteArrayList<>();

    // Synkroniseringsprimitiver
    private final ReentrantLock lock = new ReentrantLock(true); // Rettferdig lås for FIFO
    private final Condition customerAvailableCondition = lock.newCondition();
    private final Condition allCustomersFinishedCondition = lock.newCondition();

    // Venterom med ventestoler
    private final Deque<CustomerRequest> waitingRoom = new ArrayDeque<>();

    // Tilstand og statistikk
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicLong eventSequence = new AtomicLong(0);
    private final AtomicLong admissionSequence = new AtomicLong(0);
    private final AtomicLong serviceSequence = new AtomicLong(0);

    private final AtomicLong totalCustomersArrived = new AtomicLong(0);
    private final AtomicLong totalCustomersServed = new AtomicLong(0);
    private final AtomicLong totalCustomersTurnedAway = new AtomicLong(0);
    private final AtomicLong totalCustomersTimedOut = new AtomicLong(0);

    // Hendelseslyttere og historikk for testing og observasjon
    private final List<ShopEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ShopEvent> eventHistory = new CopyOnWriteArrayList<>();

    /**
     * Oppretter en barbersalong med gitt antall ventestoler og 1 barberer.
     *
     * @param waitingChairsCapacity antall ventestoler i venterommet
     */
    public BarberShop(int waitingChairsCapacity) {
        this(waitingChairsCapacity, 1, HaircutStrategy.instant());
    }

    /**
     * Oppretter en barbersalong med gitt antall ventestoler og klippestrategi.
     */
    public BarberShop(int waitingChairsCapacity, HaircutStrategy haircutStrategy) {
        this(waitingChairsCapacity, 1, haircutStrategy);
    }

    /**
     * Oppretter en barbersalong med gitt antall ventestoler, antall barberere og klippestrategi.
     *
     * @param waitingChairsCapacity antall ventestoler
     * @param numberOfBarbers antall barberere (kanonisk = 1)
     * @param haircutStrategy strategi for varighet av hårklipp
     */
    public BarberShop(int waitingChairsCapacity, int numberOfBarbers, HaircutStrategy haircutStrategy) {
        if (waitingChairsCapacity < 0) {
            throw new IllegalArgumentException("Antall ventestoler kan ikke være negativt: " + waitingChairsCapacity);
        }
        if (numberOfBarbers < 1) {
            throw new IllegalArgumentException("Må ha minst 1 barberer: " + numberOfBarbers);
        }
        this.waitingChairsCapacity = waitingChairsCapacity;
        this.haircutStrategy = Objects.requireNonNull(haircutStrategy, "HaircutStrategy kan ikke være null");

        for (int i = 1; i <= numberOfBarbers; i++) {
            Barber barber = new Barber(i, "Barber-" + i, this);
            this.barbers.add(barber);
        }
    }

    /**
     * Åpner salongen og starter barberer-tråden(e).
     */
    public synchronized BarberShop open() {
        if (open.compareAndSet(false, true)) {
            for (Barber barber : barbers) {
                Thread thread = new Thread(barber, barber.getName() + "-Thread");
                thread.setDaemon(false);
                barberThreads.add(thread);
                thread.start();
            }
            fireEvent(ShopEventType.SHOP_OPENED, null, null,
                    "Barbersalongen har åpnet med " + waitingChairsCapacity + " ventestol(er) og " + barbers.size() + " barberer(e).");
        }
        return this;
    }

    /**
     * En kunde ankommer salongen (synkron / blokkerende kall).
     *
     * <p>Kallende tråd representerer kunden. Hvis alle stoler er opptatt,
     * forlater kunden salongen umiddelbart med {@link CustomerStatus#LEFT_BECAUSE_FULL}.
     * Ellers venter tråden til hårklippen er ferdig og returnerer {@link CustomerStatus#SERVED}.
     *
     * @param customer kunden som ankommer
     * @return resultatet av kundens besøk
     * @throws InterruptedException hvis kundetråden blir avbrutt mens den venter
     */
    public CustomerResult enter(Customer customer) throws InterruptedException {
        Objects.requireNonNull(customer, "Kunde kan ikke være null");

        CustomerRequest request;
        lock.lock();
        try {
            if (!open.get()) {
                return CustomerResult.shopClosed(customer);
            }

            // Sjekk om kunden kan få plass
            if (!canAcceptCustomer()) {
                totalCustomersTurnedAway.incrementAndGet();
                fireEvent(ShopEventType.CUSTOMER_TURNED_AWAY_FULL, customer, null,
                        customer.getName() + " forlot salongen fordi alle " + waitingChairsCapacity + " ventestoler var opptatt.");
                return CustomerResult.leftBecauseFull(customer);
            }

            // Kunden tar en ventestol
            totalCustomersArrived.incrementAndGet();
            int currentWaiting = waitingRoom.size();
            long seq = admissionSequence.incrementAndGet();
            request = new CustomerRequest(customer, currentWaiting, seq);
            waitingRoom.addLast(request);

            fireEvent(ShopEventType.CUSTOMER_SEATED_IN_WAITING_ROOM, customer, null,
                    customer.getName() + " satte seg på en ventestol. (" + waitingRoom.size() + "/" + waitingChairsCapacity + " opptatt).");

            // Vekk barbereren dersom en barberer sover
            customerAvailableCondition.signal();
        } finally {
            lock.unlock();
        }

        // Kunden venter på at hårklippen skal bli fullført
        return request.awaitResult();
    }

    /**
     * En kunde ankommer salongen med en maksimal ventetid i venterommet.
     */
    public CustomerResult enter(Customer customer, Duration maxWaitTime) throws InterruptedException {
        Objects.requireNonNull(customer, "Kunde kan ikke være null");
        Objects.requireNonNull(maxWaitTime, "maxWaitTime kan ikke være null");

        CustomerRequest request;
        lock.lock();
        try {
            if (!open.get()) {
                return CustomerResult.shopClosed(customer);
            }

            if (!canAcceptCustomer()) {
                totalCustomersTurnedAway.incrementAndGet();
                fireEvent(ShopEventType.CUSTOMER_TURNED_AWAY_FULL, customer, null,
                        customer.getName() + " forlot salongen fordi alle " + waitingChairsCapacity + " ventestoler var opptatt.");
                return CustomerResult.leftBecauseFull(customer, waitingChairsCapacity);
            }

            totalCustomersArrived.incrementAndGet();
            int currentWaiting = waitingRoom.size();
            long seq = admissionSequence.incrementAndGet();
            request = new CustomerRequest(customer, currentWaiting, seq);
            waitingRoom.addLast(request);

            fireEvent(ShopEventType.CUSTOMER_SEATED_IN_WAITING_ROOM, customer, null,
                    customer.getName() + " satte seg på en ventestol. (" + waitingRoom.size() + "/" + waitingChairsCapacity + " opptatt).");

            customerAvailableCondition.signal();
        } finally {
            lock.unlock();
        }

        try {
            return request.awaitResult(maxWaitTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            lock.lock();
            try {
                if (!request.isAssigned()) {
                    waitingRoom.remove(request);
                    long waitDuration = System.currentTimeMillis() - request.getWaitingStartTimeMs();
                    CustomerResult timeoutResult = CustomerResult.leftOnTimeout(customer, waitDuration);
                    request.markCancelled(timeoutResult);
                    totalCustomersTimedOut.incrementAndGet();
                    fireEvent(ShopEventType.CUSTOMER_TIMEOUT_LEFT, customer, null,
                            customer.getName() + " forlot venterommet etter tidsavbrudd på " + waitDuration + "ms.");
                    return timeoutResult;
                }
            } finally {
                lock.unlock();
            }
            // Hvis kunden allerede var tilkalt til stolen idet tidsavbruddet inntraff, fullfør klippen
            return request.awaitResult();
        }
    }

    /**
     * Asynkront mottak av en kunde. Returnerer en CompletableFuture.
     */
    public CompletableFuture<CustomerResult> enterAsync(Customer customer) {
        Objects.requireNonNull(customer, "Kunde kan ikke være null");

        lock.lock();
        try {
            if (!open.get()) {
                return CompletableFuture.completedFuture(CustomerResult.shopClosed(customer));
            }

            if (!canAcceptCustomer()) {
                totalCustomersTurnedAway.incrementAndGet();
                fireEvent(ShopEventType.CUSTOMER_TURNED_AWAY_FULL, customer, null,
                        customer.getName() + " forlot salongen fordi alle " + waitingChairsCapacity + " ventestoler var opptatt.");
                return CompletableFuture.completedFuture(CustomerResult.leftBecauseFull(customer, waitingChairsCapacity));
            }

            totalCustomersArrived.incrementAndGet();
            int currentWaiting = waitingRoom.size();
            long seq = admissionSequence.incrementAndGet();
            CustomerRequest request = new CustomerRequest(customer, currentWaiting, seq);
            waitingRoom.addLast(request);

            fireEvent(ShopEventType.CUSTOMER_SEATED_IN_WAITING_ROOM, customer, null,
                    customer.getName() + " satte seg på en ventestol. (" + waitingRoom.size() + "/" + waitingChairsCapacity + " opptatt).");

            customerAvailableCondition.signal();
            return request.getFuture();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Kalles av barbereren for å hente neste kunde.
     * Dersom venterommet er tomt, sovner barbereren inntil en kunde vekker ham.
     */
    CustomerRequest getNextCustomer(Barber barber) {
        lock.lock();
        try {
            while (true) {
                while (waitingRoom.isEmpty() && open.get()) {
                    if (!barber.isSleeping()) {
                        barber.setState(BarberState.SLEEPING);
                        fireEvent(ShopEventType.BARBER_FELL_ASLEEP, null, barber.getName(),
                                barber.getName() + " sover fordi venterommet er tomt.");
                    }
                    try {
                        customerAvailableCondition.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }

                // Hvis salongen er stengt og det ikke er noen ventende kunder, avslutt
                if (!open.get() && waitingRoom.isEmpty()) {
                    barber.setState(BarberState.STOPPED);
                    return null;
                }

                // Hent neste kunde fra venterommet (FIFO)
                CustomerRequest next = waitingRoom.pollFirst();
                if (next == null) {
                    continue;
                }
                if (next.isCancelled()) {
                    // Kunden har forlatt salongen på grunn av tidsavbrudd, prøv neste
                    continue;
                }

                boolean wasSleeping = barber.isSleeping();
                barber.setState(BarberState.CUTTING_HAIR);

                if (wasSleeping) {
                    fireEvent(ShopEventType.BARBER_WOKEN_UP, next.getCustomer(), barber.getName(),
                            barber.getName() + " ble vekket av en ankommende kunde!");
                }

                long sSeq = serviceSequence.incrementAndGet();
                next.markAssigned(barber, sSeq);

                fireEvent(ShopEventType.CUSTOMER_CALLED_TO_CHAIR, next.getCustomer(), barber.getName(),
                        next.getCustomer().getName() + " ble ropt opp fra venterommet og setter seg i barberstolen.");
                fireEvent(ShopEventType.HAIRCUT_STARTED, next.getCustomer(), barber.getName(),
                        barber.getName() + " startet klippingen av " + next.getCustomer().getName() + ".");

                return next;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Kalles av barbereren når hårklippen er ferdig.
     */
    void finishHaircut(Barber barber, CustomerRequest request, long haircutDurationMs) {
        lock.lock();
        try {
            long waitTimeMs = request.getServiceStartTimeMs() - request.getWaitingStartTimeMs();
            Customer customer = request.getCustomer();
            long now = System.currentTimeMillis();

            CustomerResult result = CustomerResult.served(
                    customer,
                    Math.max(0, waitTimeMs),
                    haircutDurationMs,
                    barber.getName(),
                    request.getQueueDepthOnArrival(),
                    request.getWaitingStartTimeMs(),
                    request.getServiceStartTimeMs(),
                    now,
                    request.getAdmissionSequenceNumber(),
                    request.getServiceSequenceNumber()
            );

            totalCustomersServed.incrementAndGet();

            fireEvent(ShopEventType.HAIRCUT_COMPLETED, customer, barber.getName(),
                    barber.getName() + " fullførte klippen av " + customer.getName() + " etter " + haircutDurationMs + "ms.");
            fireEvent(ShopEventType.CUSTOMER_DEPARTED, customer, barber.getName(),
                    customer.getName() + " forlater salongen nyklippet og fornøyd.");

            barber.setState(BarberState.IDLE);

            if (!open.get() && waitingRoom.isEmpty()) {
                allCustomersFinishedCondition.signalAll();
            }

            // Fullfør future til slutt så alle tellere og tilstander er oppdatert før kunden våkner
            request.complete(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sjekker om en ny kunde kan aksepteres.
     * En kunde aksepteres dersom en barberer er ledig/sover, eller dersom det finnes en ledig ventestol.
     */
    private boolean canAcceptCustomer() {
        // Antall sovende/ledige barberere som ennå ikke har fått tildelt en ventende kunde
        long idleBarbers = barbers.stream()
                .filter(b -> b.isSleeping() || (b.getState() == BarberState.IDLE))
                .count();

        if (idleBarbers > waitingRoom.size()) {
            return true; // Direkte tilgang til barberstolen
        }

        return waitingRoom.size() < waitingChairsCapacity;
    }

    /**
     * Lukker salongen.
     *
     * @param finishWaitingCustomers hvis true, fullføres kundene som allerede sitter i venterommet
     */
    public void close(boolean finishWaitingCustomers) {
        lock.lock();
        try {
            if (!open.getAndSet(false)) {
                return;
            }

            fireEvent(ShopEventType.SHOP_CLOSED, null, null,
                    "Barbersalongen stenger (fullfør ventende: " + finishWaitingCustomers + ").");

            if (!finishWaitingCustomers) {
                // Avvis alle som venter i venterommet
                while (!waitingRoom.isEmpty()) {
                    CustomerRequest req = waitingRoom.pollFirst();
                    if (req != null && !req.isAssigned()) {
                        req.markCancelled(CustomerResult.shopClosed(req.getCustomer()));
                    }
                }
                for (Thread thread : barberThreads) {
                    thread.interrupt();
                }
            }

            customerAvailableCondition.signalAll();
            allCustomersFinishedCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        close(true);
    }

    /**
     * Venter på at alle barberere skal avslutte.
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long nanos = timeout.toNanos();
        long deadline = System.nanoTime() + nanos;

        for (Thread thread : barberThreads) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            long subNanos = remainingNanos % 1_000_000;
            thread.join(Math.max(1, millis), (int) subNanos);
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private void fireEvent(ShopEventType type, Customer customer, String barberName, String description) {
        long seq = eventSequence.incrementAndGet();
        ShopEvent event = new ShopEvent(
                seq,
                Instant.now(),
                type,
                customer,
                barberName,
                waitingRoom.size(),
                description
        );
        eventHistory.add(event);
        for (ShopEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    public void addListener(ShopEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ShopEventListener listener) {
        listeners.remove(listener);
    }

    public List<ShopEvent> getEventHistory() {
        return Collections.unmodifiableList(eventHistory);
    }

    public int getWaitingChairsCapacity() {
        return waitingChairsCapacity;
    }

    public int getWaitingCustomerCount() {
        lock.lock();
        try {
            return waitingRoom.size();
        } finally {
            lock.unlock();
        }
    }

    public Barber getBarber() {
        return barbers.getFirst();
    }

    public List<Barber> getBarbers() {
        return Collections.unmodifiableList(barbers);
    }

    public HaircutStrategy getHaircutStrategy() {
        return haircutStrategy;
    }

    public boolean isOpen() {
        return open.get();
    }

    public long getTotalCustomersArrived() {
        return totalCustomersArrived.get();
    }

    public long getTotalCustomersServed() {
        return totalCustomersServed.get();
    }

    public long getTotalCustomersTurnedAway() {
        return totalCustomersTurnedAway.get();
    }

    public long getTotalCustomersTimedOut() {
        return totalCustomersTimedOut.get();
    }
}
