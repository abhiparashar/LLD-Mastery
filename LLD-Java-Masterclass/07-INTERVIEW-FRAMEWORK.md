# 07 — LLD Interview Framework: Solve ANY Problem in 45 Minutes

---

## The 5-Step Framework

### Step 1: Clarify Requirements (5 minutes)

Never jump to code. Ask:

**Functional:**
- What are the core use cases? (top 3–5)
- Who are the actors? (User, Admin, System)
- What does X do when Y happens?

**Non-functional:**
- Scale? (100 users vs 1M users)
- Read-heavy or write-heavy?
- Is real-time required?
- Do we need history/audit trail?

**Scope:**
- What's OUT of scope for this session?
- Which use case to implement first?

**Example for Parking Lot:**
> "Before I start — a few clarifications. Do we support multiple vehicle types? Multiple floors? Is payment in scope? Should I handle the case of the lot being full? Should I design for a single lot or a chain of lots?"

---

### Step 2: Identify Entities and Relationships (5 minutes)

Nouns = classes. Verbs = methods.

Draw quickly on whiteboard:

```
ParkingLot → has → Floors → has → Spots
Spot → can park → Vehicle
Ticket → issued for → ParkingSession
ParkingSession → has → Vehicle, Spot, EntryTime, ExitTime
PricingStrategy → calculates → ParkingFee
```

Identify:
- Entities (with identity): `ParkingLot`, `Spot`, `Vehicle`, `Ticket`
- Value Objects (no identity): `Money`, `ParkingFee`, `Duration`
- Enums: `VehicleType`, `SpotType`, `SpotStatus`

---

### Step 3: Define Interfaces First (5 minutes)

Start with interfaces, never concrete classes:

```java
public interface ParkingSpotFinder {
    Optional<ParkingSpot> findAvailableSpot(VehicleType type, int floor);
}

public interface PricingStrategy {
    Money calculateFee(Duration duration, VehicleType type);
}

public interface TicketRepository {
    Ticket save(Ticket ticket);
    Optional<Ticket> findById(String ticketId);
}
```

This shows: "I think in contracts, not implementations."

---

### Step 4: Implement Core Classes (25 minutes)

Priority order:
1. Core domain model (entities + value objects)
2. Core service with business logic
3. One concrete implementation of each interface
4. Thread safety where relevant

Code clean, not fast. Interviewers prefer 60% code that's clean over 100% code that's spaghetti.

---

### Step 5: Handle Extensions (5 minutes)

Interviewer WILL say "What if we need to add X?"

Be ready with:
- "I'd add a new implementation of [interface] — zero modification to existing code"
- "I'd introduce [pattern] here to handle that"
- "That would require changing [class] because... but here's how I'd refactor it"

---

## The Golden Template: Parking Lot

Use this as your mental template for any problem.

```java
// Enums
public enum VehicleType { MOTORCYCLE, CAR, BUS }
public enum SpotStatus { AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE }

// Value Objects
public record Money(BigDecimal amount, Currency currency) {
    public Money add(Money other) { ... }
}

// Entities
public class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
}

public class ParkingSpot {
    private final String spotId;
    private final VehicleType supportedType;
    private final int floor;
    private SpotStatus status;
    
    public synchronized boolean occupy() {
        if (status != SpotStatus.AVAILABLE) return false;
        status = SpotStatus.OCCUPIED;
        return true;
    }
    
    public synchronized void vacate() {
        status = SpotStatus.AVAILABLE;
    }
}

// Core Service
public class ParkingLotService {
    private final ParkingSpotFinder spotFinder;
    private final PricingStrategy pricingStrategy;
    private final TicketRepository ticketRepository;
    
    public Ticket park(Vehicle vehicle) {
        ParkingSpot spot = spotFinder
            .findAvailableSpot(vehicle.getType(), 0)
            .orElseThrow(() -> new ParkingFullException());
        
        if (!spot.occupy()) throw new SpotUnavailableException();
        
        Ticket ticket = new Ticket(generateId(), vehicle, spot, Instant.now());
        return ticketRepository.save(ticket);
    }
    
    public Payment exit(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new InvalidTicketException());
        
        Duration duration = Duration.between(ticket.getEntryTime(), Instant.now());
        Money fee = pricingStrategy.calculateFee(duration, ticket.getVehicle().getType());
        
        ticket.getSpot().vacate();
        ticket.markExited(Instant.now());
        ticketRepository.save(ticket);
        
        return new Payment(ticketId, fee);
    }
}
```

---

## Extension Handling Scripts (Memorize These)

**"Add a new vehicle type?"**
> "I'd add a new `VehicleType` enum value and a new `SpotType`. The `PricingStrategy` implementations would need to handle the new type — if using a map-based strategy, I'd just add a new entry. Zero modification to core service."

**"Add multiple payment methods?"**
> "I'd create a `PaymentStrategy` interface with `process(Money amount)`. `CashPayment`, `CardPayment`, `UPIPayment` implement it. The exit flow accepts a `PaymentStrategy` — Open/Closed."

**"Add real-time spot tracking for a mobile app?"**
> "I'd add an Observer on `ParkingSpot` status changes. A `SpotStatusPublisher` notifies a WebSocket handler. No changes to core parking logic."

**"Support multiple parking lots?"**
> "I'd introduce a `ParkingLotRegistry` that maps lot IDs to `ParkingLotService` instances. A `ParkingLotRouter` selects the right lot based on location or availability."

**"Add pricing tiers (hourly, daily, monthly)?"**
> "Strategy pattern — `HourlyPricing`, `DailyCapPricing`, `MonthlySubscription` all implement `PricingStrategy`. The service doesn't change."

---

## Anti-Patterns to Avoid (Instant Red Flags)

```java
// RED FLAG 1 — God class
public class ParkingLotManager {
    public void park() { }
    public void exit() { }
    public void findSpot() { }
    public void calculateFee() { }
    public void sendSMS() { }
    public void generateReport() { }
    public void manageMaintenance() { }
    // 500 lines of spaghetti
}

// RED FLAG 2 — Switch on type
public double calculateFee(String vehicleType) {
    switch (vehicleType) {
        case "CAR": return 20;
        case "TRUCK": return 50;
        // Adding motorcycle = modify this method
    }
}

// RED FLAG 3 — No interfaces, only concrete classes
public class ParkingService {
    private MySQLParkingRepo repo = new MySQLParkingRepo(); // hardcoded!
}

// RED FLAG 4 — Ignoring thread safety on shared mutable state
public class ParkingSpot {
    private boolean isOccupied; // accessed by multiple threads, no sync!
}
```

---

## Time Budget for 45-Minute Round

| Minute | Activity |
|---|---|
| 0–5 | Clarify requirements, confirm scope |
| 5–10 | Identify entities, sketch relationships on board |
| 10–15 | Define core interfaces, walk interviewer through them |
| 15–35 | Implement: entities → service → one concrete impl |
| 35–40 | Walk through a complete scenario end-to-end |
| 40–45 | Handle extensions interviewer throws at you |

**If running short on time**: Prioritize correctness over completeness. A clean Parking + Exit flow beats a half-baked 10-class system.
