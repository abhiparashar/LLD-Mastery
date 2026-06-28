# 17 — UML & Class Diagrams for Whiteboard Interviews

> Most candidates skip UML. Senior engineers who draw clean diagrams before coding signal architecture thinking. This file teaches you exactly what to draw, how fast, and what each notation means.

---

## Why Draw Before Coding

In a 45-minute LLD round:
- **Without diagram**: You start coding, interviewer asks "where does X fit?" — you're stuck
- **With diagram**: You align with interviewer in 5 minutes, code with confidence, extensions are clear

You don't need perfect UML notation. You need *legible, consistent, fast* diagrams.

---

## Class Diagram Notation (What Interviewers Recognize)

### Box Format
```
┌─────────────────────┐
│      ClassName      │  ← Class name (bold/caps)
├─────────────────────┤
│ - id: String        │  ← Fields (- private, + public, # protected)
│ - status: Status    │
│ + name: String      │
├─────────────────────┤
│ + park(): Ticket    │  ← Methods with return types
│ + exit(): Fee       │
│ - validate(): bool  │
└─────────────────────┘

For interfaces:
┌─────────────────────┐
│   «interface»       │
│  PricingStrategy    │
├─────────────────────┤
│ + calculate(): Fee  │
└─────────────────────┘

For enums:
┌─────────────────────┐
│     «enum»          │
│   VehicleType       │
├─────────────────────┤
│  MOTORCYCLE         │
│  CAR                │
│  TRUCK              │
└─────────────────────┘
```

---

## Relationship Notation

### 1. Association (has-a, simple reference)
```
ClassA ──────────────> ClassB
                   "uses" / "references"

ParkingLot ──────────> ParkingSpot
```
Arrow = directed association (A knows about B, B doesn't know A)

---

### 2. Aggregation (has-a, weak ownership — can exist independently)
```
ClassA ◇──────────────> ClassB
         aggregation

Department ◇──────────> Employee
(Employee can exist without Department)
```
Open diamond on owner side.

---

### 3. Composition (has-a, strong ownership — cannot exist independently)
```
ClassA ◆──────────────> ClassB
         composition

Order ◆──────────────> OrderItem
(OrderItem cannot exist without Order)

ParkingLot ◆──────────> Floor ◆──────────> ParkingSpot
```
Filled diamond on owner side.

---

### 4. Inheritance (is-a)
```
         ParentClass
              △
              │
     ─────────────────
     │                │
 ChildA            ChildB

         Vehicle
            △
            │
    ─────────────────
    │                │
   Car             Truck
```
Open triangle pointing to parent.

---

### 5. Interface Implementation
```
   «interface»
  PricingStrategy
        △
       ╌╌╌  (dashed line)
        │
 HourlyPricing    PerDayPricing
```
Dashed line + open triangle = implements interface.

---

### 6. Dependency (uses temporarily, not stored)
```
ClassA - - - - -> ClassB
         «uses»

OrderService - - - -> PaymentGateway
```
Dashed arrow = depends on (passes as param, creates locally, calls static method).

---

## Cardinality Notation

```
ClassA ────── "1" ────────── "1..*" ─── ClassB
             (one)          (one or more)

Common notations:
  1       = exactly one
  0..1    = zero or one (optional)
  *       = zero or many
  1..*    = one or many (at least one)
  0..*    = zero or many (same as *)
  n       = exactly n

Examples:
  ParkingLot "1" ◆──────── "1..*" Floor
  Floor "1" ◆──────────── "1..*" ParkingSpot
  ParkingSpot "1" ─────── "0..1" Vehicle
  Order "1" ◆──────────── "1..*" OrderItem
  Customer "1" ─────────── "0..*" Order
```

---

## Complete Parking Lot Class Diagram

```
                    «enum»                    «enum»
                  VehicleType               SpotStatus
                ┌───────────┐            ┌───────────┐
                │MOTORCYCLE │            │ AVAILABLE │
                │    CAR    │            │ OCCUPIED  │
                │    BUS    │            │ RESERVED  │
                └───────────┘            └───────────┘

┌──────────────────────┐         ┌──────────────────────┐
│      ParkingLot      │         │       Vehicle        │
├──────────────────────┤         ├──────────────────────┤
│ - id: String         │         │ - plate: String      │
│ - name: String       │         │ - type: VehicleType  │
├──────────────────────┤         └──────────────────────┘
│ + park(v): Ticket    │
│ + exit(t): Fee       │
└──────────────────────┘
          │◆
          │ 1..*
          ▼
┌──────────────────────┐
│        Floor         │
├──────────────────────┤
│ - number: int        │
├──────────────────────┤
│ + getAvailable(): [] │
└──────────────────────┘
          │◆
          │ 1..*
          ▼
┌──────────────────────┐         ┌──────────────────────┐
│     ParkingSpot      │         │       Ticket         │
├──────────────────────┤         ├──────────────────────┤
│ - id: String         │         │ - id: String         │
│ - type: VehicleType  │◄── 1 ───│ - spot: ParkingSpot  │
│ - status: SpotStatus │         │ - vehicle: Vehicle   │
├──────────────────────┤         │ - entryTime: Instant │
│ + occupy(): bool     │         └──────────────────────┘
│ + vacate(): void     │
└──────────────────────┘

         «interface»
        PricingStrategy
       ┌──────────────┐
       │+ calc(): Fee │
       └──────────────┘
              △
             ╌╌╌
    ┌──────────────────┐
    │                  │
HourlyPricing    DailyCapPricing
```

---

## Sequence Diagram (For Flow)

Draw sequence diagrams when interviewer asks "walk me through the flow."

```
  Client    ParkingLot   SpotFinder   ParkingSpot   TicketRepo
    │            │            │             │             │
    │──park(v)──>│            │             │             │
    │            │──find(t)──>│             │             │
    │            │<──spot─────│             │             │
    │            │──occupy()──────────────>│             │
    │            │<──true──────────────────│             │
    │            │──save(ticket)──────────────────────>  │
    │            │<──ticket──────────────────────────────│
    │<──ticket───│            │             │             │
    │            │            │             │             │
```

**Notation rules:**
- Vertical line = object's lifeline
- Horizontal arrow = method call
- Dashed arrow = return value
- Box on lifeline = object is active/processing
- X at bottom = object destroyed

---

## What to Draw First in Any Interview

**Step 1 (2 min)**: Core entities — just boxes with names, no fields yet
```
ParkingLot   Floor   ParkingSpot   Vehicle   Ticket
```

**Step 2 (2 min)**: Relationships and cardinalities
```
ParkingLot ◆─1..*─ Floor ◆─1..*─ ParkingSpot ─0..1─ Vehicle
ParkingSpot ─1─ Ticket
```

**Step 3 (1 min)**: Add key interfaces
```
«interface» SpotFinder
«interface» PricingStrategy
```

**Step 4**: Start coding — diagram is your blueprint

---

## Common Diagram Mistakes to Avoid

**Mistake 1**: Drawing inheritance when you should draw composition
```
WRONG: Car extends ParkingSpot (makes no sense)
RIGHT: ParkingSpot has-a Vehicle
```

**Mistake 2**: Missing interface boxes
```
WRONG: Just draw concrete classes
RIGHT: Always show the interface if you're using polymorphism
```

**Mistake 3**: Forgetting multiplicity
```
WRONG: ParkingLot ─── Floor
RIGHT: ParkingLot "1" ◆─── "1..*" Floor
       (multiplicity tells interviewer you've thought about the data model)
```

**Mistake 4**: Over-diagramming before understanding requirements
```
WRONG: Drawing 20 classes before clarifying requirements
RIGHT: Draw the 5-6 core entities, confirm with interviewer, then expand
```

---

## Quick Vocabulary for Whiteboard Communication

When drawing, narrate:
- "ParkingLot *composes* Floor — a floor can't exist without the lot"
- "ParkingSpot *aggregates* Vehicle — the vehicle exists independently"
- "PricingStrategy is an *interface* — HourlyPricing and DailyPricing *implement* it"
- "OrderService *depends on* PaymentGateway — it's not stored, just used in the checkout flow"
- "One customer can have *zero or many* orders — that's a one-to-many"

This narration shows architectural thinking, not just coding ability.

---

## DB Schema Alongside Class Diagram

Senior engineers connect class diagrams to DB schema. Shows full-stack LLD thinking.

```
Class Diagram          →       DB Table

ParkingLot                     parking_lots
  id: String                     id VARCHAR(36) PK
  name: String                   name VARCHAR(100) NOT NULL

ParkingSpot                    parking_spots
  id: String                     id VARCHAR(36) PK
  floor: int                     floor_number INT NOT NULL
  type: VehicleType              vehicle_type ENUM('MOTORCYCLE','CAR','BUS') NOT NULL
  status: SpotStatus             status ENUM('AVAILABLE','OCCUPIED') DEFAULT 'AVAILABLE'
                                 lot_id VARCHAR(36) FK → parking_lots.id

Ticket                         tickets
  id: String                     id VARCHAR(36) PK
  spot: ParkingSpot              spot_id VARCHAR(36) FK → parking_spots.id
  vehicle: Vehicle               vehicle_plate VARCHAR(20) NOT NULL
  entryTime: Instant             entry_time TIMESTAMP NOT NULL
  exitTime: Instant              exit_time TIMESTAMP NULL
  fee: Money                     fee_amount DECIMAL(10,2) NULL
                                 fee_currency CHAR(3) NULL

Indexes:
  parking_spots: (lot_id, status, vehicle_type) -- for spot finder query
  tickets: (vehicle_plate, exit_time) -- for active ticket lookup
```

**Interview signal**: Drawing the index alongside the schema shows you think about query performance, not just data modeling.
