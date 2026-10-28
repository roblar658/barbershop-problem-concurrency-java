package com.barbershop;

import com.barbershop.starvation.StarvationAudit;
import com.barbershop.starvation.StarvationReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Spesifikke tester for å utelukke utsulting (Starvation)")
class BarberShopStarvationTest {

    private BarberShop shop;

    @AfterEach
    void tearDown() throws Exception {
        if (shop != null && shop.isOpen()) {
            shop.close(false);
            shop.awaitTermination(Duration.ofSeconds(1));
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Kunde i venterommet skal aldri bli forbikjørt eller utsultet under kontinuerlig kundestrøm")
    void testNoCustomerStarvesUnderContinuousQueueSaturation() throws Exception {
        int chairs = 3;
        long haircutDurationMs = 15;
        shop = new BarberShop(chairs, HaircutStrategy.fixed(Duration.ofMillis(haircutDurationMs)));
        shop.open();

        int totalCustomers = 60;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<CustomerResult>> futures = new ArrayList<>();

        for (int i = 1; i <= totalCustomers; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "Metning-" + id))));
            // Spredt ankomst som holder salongen kontinuerlig mettet
            Thread.sleep(8);
        }

        List<CustomerResult> results = new ArrayList<>();
        for (Future<CustomerResult> f : futures) {
            results.add(f.get());
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));

        // Utfør formell revisjon for utsulting
        StarvationReport report = StarvationAudit.audit(results, chairs, haircutDurationMs);

        System.out.println(report);

        assertTrue(report.isStarvationFree(), "Systemet MÅ være 100% fri for utsulting!");
        assertEquals(0, report.overtakenCount(), "Ingen kunder skal noen gang bli forbikjørt i køen!");
        assertEquals(0, report.starvedCount(), "Ingen kunder skal oppleve uendelig eller ubegrenset venting!");
        assertTrue(report.servedCount() > 0, "Kunder må ha blitt betjent");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Maksimal ventetid for enhver kunde er strengt begrenset av (Kapasitet + 1) * Klippetid")
    void testMaximumWaitTimeIsBoundedByCapacityTimesHaircut() throws Exception {
        int chairs = 2;
        long haircutMs = 20;
        shop = new BarberShop(chairs, HaircutStrategy.fixed(Duration.ofMillis(haircutMs)));
        shop.open();

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<CustomerResult>> futures = new ArrayList<>();

        for (int i = 1; i <= 15; i++) {
            final int id = i;
            futures.add(pool.submit(() -> shop.enter(new Customer(id, "Bredde-" + id))));
            Thread.sleep(10);
        }

        List<CustomerResult> results = new ArrayList<>();
        for (Future<CustomerResult> f : futures) {
            results.add(f.get());
        }

        pool.shutdown();

        StarvationReport report = StarvationAudit.audit(results, chairs, haircutMs);

        assertEquals(0, report.starvedCount(), "Ventetiden overskred den matematiske øvre grensen!");
        assertTrue(report.isStarvationFree());
    }

    @Test
    @DisplayName("StarvationAudit skal korrekt detektere og flagge syntetiske utsultingstilfeller")
    void testStarvationAuditDetectsSyntheticViolations() {
        Customer c1 = new Customer(1, "A");
        Customer c2 = new Customer(2, "B");

        // Syntetisk brudd: B ble tatt inn som #2, men betjent som #1 før A (tatt inn som #1, betjent som #2)
        CustomerResult r1 = CustomerResult.served(c1, 100, 10, "Barber-1", 1, 1000, 1200, 1210, 1L, 2L);
        CustomerResult r2 = CustomerResult.served(c2, 10, 10, "Barber-1", 1, 1050, 1100, 1110, 2L, 1L);

        StarvationReport report = StarvationAudit.audit(List.of(r1, r2), 2, 50);

        assertFalse(report.isStarvationFree());
        assertTrue(report.overtakenCount() > 0, "Skal detektere forbikjøring");
        assertFalse(report.violations().isEmpty());
    }
}
