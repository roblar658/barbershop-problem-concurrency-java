package com.barbershop;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hovedprogram som demonstrerer "Sleeping Barber"-simuleringen i konsollen.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("            DET SOVENDE BARBER-PROBLEMET (THE SLEEPING BARBER)                  ");
        System.out.println("================================================================================");
        System.out.println("Kapasitet: 3 ventestoler, 1 barberer.");
        System.out.println("Når det ikke er noen kunder, sover barbereren.");
        System.out.println("Når en kunde ankommer vekkes barbereren.");
        System.out.println("Dersom alle stoler er opptatt, forlater kunden salongen umiddelbart.");
        System.out.println("--------------------------------------------------------------------------------\n");

        int waitingChairs = 3;
        // Klippetid mellom 200 og 400 ms for demonstrasjon
        HaircutStrategy strategy = HaircutStrategy.random(Duration.ofMillis(200), Duration.ofMillis(400));

        BarberShop shop = new BarberShop(waitingChairs, strategy);

        // Legg til en lytter som logger hendelser pent
        shop.addListener(event -> {
            String icon = switch (event.type()) {
                case SHOP_OPENED -> "🟢";
                case SHOP_CLOSED -> "🔴";
                case BARBER_FELL_ASLEEP -> "💤";
                case BARBER_WOKEN_UP -> "⏰";
                case CUSTOMER_ARRIVED -> "🚪";
                case CUSTOMER_SEATED_IN_WAITING_ROOM -> "🪑";
                case CUSTOMER_TURNED_AWAY_FULL -> "🚫";
                case CUSTOMER_CALLED_TO_CHAIR -> "🔔";
                case HAIRCUT_STARTED -> "✂️ ";
                case HAIRCUT_COMPLETED -> "✨";
                case CUSTOMER_DEPARTED -> "👋";
                case CUSTOMER_TIMEOUT_LEFT -> "⌛";
            };
            System.out.printf("%s %-32s | Venterom: %d/%d | %s%n",
                    icon, event.type(), event.waitingCustomerCount(), shop.getWaitingChairsCapacity(), event.description());
        });

        shop.open();

        // La barbereren sovne
        Thread.sleep(300);

        ExecutorService customerPool = Executors.newCachedThreadPool();
        AtomicInteger customerIdGen = new AtomicInteger(1);

        System.out.println("\n--- FASE 1: Én kunde ankommer og vekker barbereren ---");
        Customer c1 = new Customer(customerIdGen.getAndIncrement(), "Per");
        customerPool.submit(() -> {
            try {
                shop.enter(c1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(600);

        System.out.println("\n--- FASE 2: Bølge av 5 kunder ankommer samtidig (3 stoler + 1 klipper -> 1 avvises) ---");
        for (int i = 0; i < 5; i++) {
            Customer c = new Customer(customerIdGen.getAndIncrement(), "Kunde-" + (i + 2));
            customerPool.submit(() -> {
                try {
                    shop.enter(c);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(50); // Litt spredning
        }

        // Vent til alle er ferdige
        Thread.sleep(2500);

        System.out.println("\n--- FASE 3: Ingen kunder igjen -> Barbereren sovner igjen ---");
        Thread.sleep(500);
        System.out.printf("Barberer-status: %s (Sover: %s)%n", shop.getBarber().getState(), shop.getBarber().isSleeping());

        System.out.println("\n--- FASE 4: Stenger salongen kontrollert ---");
        shop.close(true);
        shop.awaitTermination(Duration.ofSeconds(2));
        customerPool.shutdown();
        customerPool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("\n================================================================================");
        System.out.println("                                SLUTTRAPPORT                                    ");
        System.out.println("================================================================================");
        System.out.printf("Totalt ankommet:   %d%n", shop.getTotalCustomersArrived());
        System.out.printf("Totalt betjent:    %d%n", shop.getTotalCustomersServed());
        System.out.printf("Totalt avvist:     %d%n", shop.getTotalCustomersTurnedAway());
        System.out.printf("Barberer klippet:  %d ganger%n", shop.getBarber().getHaircutsDone());
        System.out.println("================================================================================");
    }
}

