# LLD Mastery — Road to Top 1%

**Goal:** Crack any MAANG-level Low Level Design interview
**Interview:** Kiwi (Insurance Tech) — Friday, June 20, 2026 | 1:30–2:30 PM

---

## 5-Day Battle Plan

| Day | Focus | Problems | Status |
|-----|-------|----------|--------|
| Mon | Framework + OOP Thinking | Parking Lot | ✅ Done |
| Tue | Relationships + SOLID | Library Management System | ⏳ |
| Wed | Design Patterns | Elevator / ATM | ⏳ |
| Thu | Insurance Domain | Policy Management / Claims System | ⏳ |
| Fri Morning | Mock Round | Full Timed Simulation | ⏳ |

---

## Full Problem List

### Tier 1 — Must Know
| # | Problem | Status |
|---|---------|--------|
| 1 | Parking Lot | ✅ Done |
| 2 | Library Management System | ⏳ |
| 3 | Elevator System | ⏳ |
| 4 | ATM Machine | ⏳ |
| 5 | Hotel Booking System | ⏳ |

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

## How This Works
- Concepts are taught interactively — you think, you answer, then we code
- You write all code, mentor reviews
- Each problem is committed after completion for revision
