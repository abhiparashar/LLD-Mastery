# LLD Mastery — Road to Top 1%

**Goal:** Crack any MAANG-level Low Level Design interview
**Interview:** Kiwi (Insurance Tech) — Friday, June 20, 2026 | 1:30–2:30 PM

---

## Battle Plan (Updated)

| Day | Focus | Problems | Status |
|-----|-------|----------|--------|
| Mon | OOP Thinking + Facade | Parking Lot | ✅ Done |
| Tue (split) | Availability Logic + Booking, then Tier 2 | Hotel Booking System; Ride Sharing, Movie Ticket Booking, Food Delivery, E-commerce, Chess | ✅ Hotel Booking Done — Tier 2 in progress |
| Wed | Remaining Tier 1 | Elevator System, ATM Machine, Library Management | ⏳ |
| Thu | Insurance Domain (Kiwi specific) | Policy Management, Claims Processing | ⏳ |
| Fri (before 1:30 PM) | Insurance Domain + Mock Round | Payment/Billing, Notification, Wallet, full timed mock | ⏳ |

---

## Full Problem List

### Tier 1 — Must Know
| # | Problem | Status |
|---|---------|--------|
| 1 | Parking Lot | ✅ Done |
| 2 | Library Management System | ⏳ |
| 3 | Elevator System | ⏳ |
| 4 | ATM Machine | ⏳ |
| 5 | Hotel Booking System | ✅ Done |

### Tier 2 — Product Companies
| # | Problem | Status |
|---|---------|--------|
| 6 | Ride Sharing (Uber/Ola) | ⏳ |
| 7 | Movie Ticket Booking (BookMyShow) | ⏳ |
| 8 | Food Delivery (Swiggy/Zomato) | ⏳ |
| 9 | E-commerce / Shopping Cart | ⏳ |
| 10 | Chess / Snake & Ladder | ⏳ |

### Tier 3 — Kiwi Relevant (Insurance Tech)
| # | Problem | Status |
|---|---------|--------|
| 11 | Insurance Policy Management | ⏳ |
| 12 | Claims Processing System | ⏳ |
| 13 | Payment / Billing System | ⏳ |
| 14 | Notification System | ⏳ |
| 15 | Wallet System | ⏳ |

---

## Problem 1 — Parking Lot System

### What We Are Learning Here
1. **LLD Interview Framework** — 4 steps before writing any class
   - Clarify Requirements
   - Identify Entities (nouns → classes)
   - Define Relationships (has-a vs is-a)
   - Define Behaviors (verbs → methods)

2. **Core OOP Concepts**
   - Class vs Interface (what holds state vs what defines contract)
   - Composition over Inheritance (and when inheritance is wrong)
   - Enums for type safety (compile-time safety vs runtime String errors)

3. **Key Design Decisions**
   - Why `VehicleType` as enum, not String or int
   - Why `ParkingTicket` is the central entity of the system
   - Why `isAvailable` is set by explicit action, not by timeout
   - Single Responsibility — who owns what logic (Spot vs Floor vs ParkingLot)

4. **Classes We Are Building**
   - `VehicleType` (enum) — CAR, BIKE, TRUCK
   - `TicketStatus` (enum) — ACTIVE, PAID
   - `Vehicle` (model) — vehicleNumber, vehicleType
   - `ParkingSpot` (model) — spotId, spotType, floorNumber, isAvailable
   - `Floor` (model) — floorNumber, list of spots
   - `ParkingTicket` (model) — ticketId, vehicle, spot, entryTime, exitTime, amount, status
   - `ParkingLotService` (service) — checkAvailability, assignSpot, generateTicket, processExit

5. **Key Principles Introduced**
   - Open/Closed Principle — extend Vehicle types without changing ParkingTicket
   - Single Responsibility Principle — each class owns one concern
   - "Inherit for behavior, enum for identity"
   - Resources are released by explicit actions, not timeouts

---

## Problem 2 — Hotel Booking System

### What We Are Learning Here
1. **Date-range thinking** — availability is not a boolean flag, it depends on overlapping date ranges
2. **The interval overlap formula**
   ```
   existing.checkIn.isBefore(requested.checkOut)
       && existing.checkOut.isAfter(requested.checkIn)
   ```
   Handles every overlap shape (partial, full containment) with one expression
3. **Flat list + filter, not nested maps** — `Map<Room, Booking>` breaks when one room has many bookings over time; a flat `List<Booking>` filtered by room scales correctly
4. **Lifecycle states vs concurrency concerns** — `PENDING` exists for payment-pending business state, not for race conditions; race conditions need DB-level locks, not application status checks (out of scope for a 1hr interview, but good to mention)
5. **Scope discipline** — model the full lifecycle in the enum, implement only the happy path unless asked to go deeper
6. **Classes Built**
   - `RoomType` (enum) — SINGLE, DOUBLE, SUITE
   - `BookingStatus` (enum) — PENDING, CONFIRMED, CANCELLED, CHECKED_OUT
   - `Room` (model) — roomId, roomType, pricePerNight (static price map)
   - `Guest` (model) — guestId, name, email
   - `Booking` (model) — bookingId, guest, room, checkInDate, checkOutDate, amount, status
   - `HotelService` (service) — findAvailableRoom, createBooking, cancelBooking, checkOutBooking
   - `Hotel` (facade) — thin wrapper delegating to HotelService

---

## How This Works
- Concepts are taught interactively — you think, you answer, then we code
- You write all code, mentor reviews
- Each problem is committed after completion for revision
