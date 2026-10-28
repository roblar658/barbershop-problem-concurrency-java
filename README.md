# Det sovende barber-problemet (The Sleeping Barber Problem)

En komplett, robust og trådsikker løsning på Edsger Dijkstras klassiske synkroniseringsproblem implementert i **Java 21**, levert med et omfattende testbatteri (**28 tester på tvers av 12 testklasser**), formell **utsultingsanalyse (Starvation Check)** og **stresstesting med CSV-eksport og CSV-avspilling**.

---

## 📌 Problembeskrivelse

> *"A barber cuts hair when customers exist; otherwise, the barber sleeps. Arriving customers take a waiting chair or leave if all chairs are occupied."*

### Regler og forutsetninger:
1. **Barbereren**:
   - Klipper hår når det finnes kunder i salongen.
   - Når ingen kunder venter og ingen klippes, legger barbereren seg til å sove (`SLEEPING`).
2. **Kunder**:
   - Når en kunde ankommer og finner ledig ventestol, setter kunden seg ned (`WAITING`).
   - Dersom barbereren sover, vekker den første kunden barbereren (`AWAKE` -> `CUTTING_HAIR`).
   - Dersom alle ventestolene er opptatt når kunden ankommer, forlater kunden salongen umiddelbart uten å vente (**balking** / `LEFT_BECAUSE_FULL`).
3. **Kø og rekkefølge**:
   - Kunder i ventestoler betjenes i streng FIFO-rekkefølge (First-In, First-Out).
   - Når en kunde flyttes fra ventestolen til barberstolen, blir ventestolen umiddelbart ledig for nye ankommende kunder.

---

## 🛡️ Utsultingssjekk (Starvation Check)

En av de mest kritiske egenskapene i samtidighetssystemer er fravær av utsulting (**Starvation-freedom**):
- **Ikke-forbikjøring (Strict Non-Overtaking)**: Dersom kunde $A$ ankommer og får plass i venterommet før kunde $B$, skal kunde $B$ **aldri** bli betjent før kunde $A$.
- **Begrenset ventetid (Bounded Wait Time)**: For et venterom med kapasitet $C$ og maksimal klippetid $T_{\max}$, er en klients ventetid strengt begrenset til:
  $$\text{MaxWaitTime} \le (C + 1) \times T_{\max}$$
- Klassen `StarvationAudit` utfører matematisk og empirisk revisjon av alle kunders tidsstempler og køposisjoner. Den bekrefter:
  1. `overtakenCount == 0` (ingen forbikjøring i køen)
  2. `starvedCount == 0` (ingen uendelig venting)
  3. `isStarvationFree == true`

---

## 📊 Stresstesting og CSV-filer

Systemet inkluderer full støtte for å generere, analysere og lese inn CSV-filer via `CsvMetricsExporter`:

### 1. Detaljert kundemetrikk (`customers_metrics.csv`):
Hver eneste kunde som ankommer registreres med nøyaktige nanosekund-/millisekundtidsstempler:
```csv
customer_id,customer_name,status,arrival_time_ms,service_start_time_ms,completion_time_ms,wait_time_ms,haircut_duration_ms,total_duration_ms,barber_name,queue_depth_on_arrival
1,StressKunde-1,SERVED,1793185565781,1793185565797,1793185565800,16,2,18,Barber-1,2
2,StressKunde-2,LEFT_BECAUSE_FULL,1793185565772,0,1793185565782,0,0,0,,5
...
```

### 2. Sammendragsrapport (`stress_summary.csv`):
Statistisk sammendrag med persentiler og gjennomstrømning:
```csv
metric,value
total_customers,1000
served_customers,12
turned_away_or_other,988
overtaken_customers,0
starved_customers,0
is_starvation_free,true
mean_wait_ms,13.67
p95_wait_ms,23.00
p99_wait_ms,23.00
max_observed_wait_ms,23
theoretical_max_wait_ms,230
total_elapsed_ms,47
throughput_served_per_sec,255.32
```

### 3. Scenario-avspilling fra CSV (`input_workload.csv`):
Du kan forhåndsdefinere et scenario i en CSV-fil (med kolonner `customer_id,customer_name,arrival_delay_ms,haircut_duration_ms`) og spille det av direkte gjennom `BarberShop`.

---

## 🏗️ Prosjektstruktur

```
C:\dev\sleeping-barber/
├── pom.xml                               # Maven-konfigurasjon (Java 21, JUnit 5.10.0)
├── mvnw.cmd                              # Maven Wrapper for enkel kjøring
├── run-tests.bat                         # Kjører alle 28 enhets- og stresstester
├── run-demo.bat                          # Kjører interaktiv konsoll-demonstrasjon
├── run-stress-csv.bat                    # Kjører 1000-kunders stresstest med CSV-eksport
├── README.md                             # Denne dokumentasjonen
├── data/                                 # Genererte CSV-filer fra stresstester
│   ├── customers_metrics.csv
│   └── stress_summary.csv
├── src/
│   ├── main/java/com/barbershop/
│   │   ├── BarberShop.java               # Hovedkoordinator for synkronisering (ReentrantLock + Condition)
│   │   ├── Barber.java                   # Barberer-arbeidertråd (klipper eller sover)
│   │   ├── BarberState.java              # Enum: SLEEPING, CUTTING_HAIR, IDLE, STOPPED
│   │   ├── Customer.java                 # Kundemodell med ID, navn og tidsstempel
│   │   ├── CustomerStatus.java           # Enum: WAITING, IN_CHAIR, SERVED, LEFT_BECAUSE_FULL, etc.
│   │   ├── CustomerResult.java           # Resultatkvittering med tidsmålinger og kødybde
│   │   ├── CustomerRequest.java          # Intern billett/future mellom kundetråd og barberer
│   │   ├── HaircutStrategy.java          # Strategier for klippetid (fast, tilfeldig, instant, kontrollert)
│   │   ├── ShopEvent.java                # Hendelsespost for observasjon og testing
│   │   ├── ShopEventType.java            # Hendelsestyper (SOVNET, VEKKET, ANKOMMET, AVVIST, etc.)
│   │   ├── ShopEventListener.java        # Observatørgrensesnitt for sanntidshendelser
│   │   ├── Main.java                     # Interaktiv fargerik konsolldemonstrasjon
│   │   ├── starvation/
│   │   │   ├── StarvationAudit.java      # Revisor som detekterer utsulting og FIFO-avvik
│   │   │   └── StarvationReport.java     # Rapportpost med persentiler og revisjonsstatus
│   │   ├── csv/
│   │   │   ├── CsvMetricsExporter.java   # Eksporterer og importerer CSV-filer
│   │   │   └── CustomerWorkloadItem.java # DTO for arbeidsbelastningsrader i CSV
│   │   └── stress/
│   │       └── CsvStressRunner.java      # CLI-stresstestverktøy med CSV-eksport
│   └── test/java/com/barbershop/
│       ├── BarberShopStarvationTest.java         # Tester som beviser null utsulting under kontinuerlig metning
│       ├── BarberShopCsvStressTest.java          # Stresstest som validerer CSV-eksport og CSV-avspilling
│       ├── BarberShopBasicTest.java              # Grunnleggende tester: soving, vekking, enkeltsak
│       ├── BarberShopBalkingTest.java            # Tester for stolkroning og avvisning (balking)
│       ├── BarberShopFifoTest.java               # Verifisering av streng FIFO-betjeningsrekkefølge
│       ├── BarberShopSleepingLifecycleTest.java  # Sykluser mellom søvn og oppvåkning
│       ├── BarberShopConcurrencyTest.java       # Bølger av 40 samtidige tråder og bevaringslover
│       ├── BarberShopTimeoutTest.java            # Kunder som gir opp ved ventetidsavbrudd (reneging)
│       ├── BarberShopShutdownTest.java           # Rolig (graceful) og umiddelbar stenging av salong
│       ├── BarberShopStressTest.java             # 500 kunder under høy belastning
│       ├── MultiBarberShopTest.java             # Utvidelse: støtte for flere barberere samtidig
│       └── BarberShopEdgeCasesTest.java          # Null-sjekker, feilaktige parametre og trådavbrudd
```

---

## 🧪 Testbatteri (28 tester)

| Testklasse | Antall tester | Fokusområde |
|---|---|---|
| `BarberShopStarvationTest` | 3 | Kontinuerlig metning, maksimal teoretisk ventetidsbegrensning, og deteksjon av syntetiske brudd. |
| `BarberShopCsvStressTest` | 2 | 500-kunders stresstest med generering av `stress_customers.csv` og `stress_summary.csv`, samt avspilling av scenario fra CSV. |
| `BarberShopBasicTest` | 5 | Barberer sover ved tom salong, vekkes av kunde, klippes og sovner igjen. Sekvensiell betjening. 0 ventestoler-oppførsel. |
| `BarberShopBalkingTest` | 2 | Kunde avvises momentant når alle stoler er fulle. Frigjøring av stol ved oppkalling til barberstol. |
| `BarberShopFifoTest` | 1 | Kunder i venterommet betjenes i nøyaktig samme rekkefølge som de ankom (FIFO). |
| `BarberShopSleepingLifecycleTest` | 2 | Hendelsesloggen verifiserer korrekt sekvens: `BARBER_FELL_ASLEEP` -> `BARBER_WOKEN_UP` -> `HAIRCUT_STARTED` -> `HAIRCUT_COMPLETED` -> `BARBER_FELL_ASLEEP`. |
| `BarberShopConcurrencyTest` | 2 | 40 samtidige tråder via `CountDownLatch`. Maks ventende overskrider aldri stolkapsiteten. Bevaringslov: `served + turnedAway == total`. |
| `BarberShopTimeoutTest` | 1 | Kunder med tidsavbrudd forlater venterommet og stolen frigjøres for neste kunde. |
| `BarberShopShutdownTest` | 2 | `close(true)` venter på at ventende kunder skal fullføres; `close(false)` avbryter umiddelbart. |
| `BarberShopStressTest` | 1 | 500 kunder sendt fra 16 tråder med `instant()` klippestrategi for å påvise fravær av vranglås (deadlock). |
| `MultiBarberShopTest` | 2 | Utvidelse til $M$ barberere: alle sover når tomt, og klipper i parallell når flere kunder ankommer. |
| `BarberShopEdgeCasesTest` | 5 | Parameter-validering, null-sjekker, uforanderlige lister og trådavbrudd (`InterruptedException`). |

---

## 🚀 Kjøring

### 1. Kjøre alle 28 tester
```powershell
cd C:\dev\sleeping-barber
.\mvnw.cmd test
```
eller dobbeltklikk på [`run-tests.bat`](file:///C:/dev/sleeping-barber/run-tests.bat).

### 2. Kjøre stresstest med utsultingssjekk og CSV-generering
```powershell
.\run-stress-csv.bat
```
eller via Java direkte:
```powershell
java -cp target\classes com.barbershop.stress.CsvStressRunner 1000 5 20
```
Dette genererer:
- `C:\dev\sleeping-barber\data\customers_metrics.csv`
- `C:\dev\sleeping-barber\data\stress_summary.csv`

### 3. Kjøre den interaktive konsolldemonstrasjonen
```powershell
.\run-demo.bat
```
eller:
```powershell
java -cp target\classes com.barbershop.Main
```
