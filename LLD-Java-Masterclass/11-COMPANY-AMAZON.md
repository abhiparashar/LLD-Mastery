# 11 — Company-Specific Problems: Amazon

> **Note**: This file covers problem statements, requirements, entity models, design approach, patterns, and interview traps. No implementation code — focus on thinking through the design.

---

## Problem 1: Amazon Locker System

**Frequency**: Very High | **Level**: Medium-Hard

### The Problem
Design Amazon's package locker system where customers pick up parcels without a delivery person.

### Requirements to Clarify
- Multiple locker locations, each with lockers of different sizes (S, M, L, XL)
- Package assigned to nearest available locker of appropriate size
- Customer gets a code to open locker, valid for N days
- Package auto-returned if not picked up
- Locker can be opened only with the right code

### Core Entities
```
Locker          → id, size, status (EMPTY/OCCUPIED/RESERVED), location
LockerLocation  → id, address, GPS coordinates, List<Locker>
Package         → id, size, customerId, deliveryDate
LockerReservation → package, locker, code, expiryTime, status
Customer        → id, name, preferredLocation
```

### Design Approach
- **Strategy**: `LockerSelectionStrategy` — selects optimal locker (nearest, smallest fitting size)
- **State**: Locker states — EMPTY → RESERVED → OCCUPIED → EMPTY
- **Observer**: When expiry time passes, system auto-releases locker and triggers return shipment
- **Factory**: `CodeGenerator` creates one-time codes

### Patterns Used
- Strategy (locker selection)
- State (locker lifecycle)
- Observer (expiry notification)
- Template Method (locker assignment workflow)

### Traps & Follow-ups
- **"Same package fits S and M — which to assign?"** → Always smallest fitting size to preserve larger lockers for larger packages
- **"Customer doesn't pick up in 3 days?"** → Scheduled job checks expiry, auto-releases, triggers return shipment, notifies customer
- **"Code security?"** → One-time use, time-limited, hashed in storage
- **"What if all lockers at nearest location are full?"** → Find next nearest, notify customer of change
- **"Multiple packages for same customer?"** → Try to assign to same location, group notification

---

## Problem 2: Amazon Prime Membership Management

**Frequency**: Medium | **Level**: Medium

### The Problem
Design the subscription management system for Amazon Prime.

### Requirements to Clarify
- Individual and Family membership types
- Benefits: free shipping, Prime Video, Prime Music, early access deals
- Automatic renewal, payment failure handling
- Family plan: one primary + up to 5 members
- Trial periods, pause, cancellation with prorated refund

### Core Entities
```
Membership      → id, type (INDIVIDUAL/FAMILY), status, startDate, renewalDate
Member          → id, name, email, membershipRole (PRIMARY/FAMILY)
Benefit         → id, type, eligibleMembershipTypes
Subscription    → membership, paymentMethod, billingCycle, autoRenew
FamilyGroup     → primaryMember, familyMembers (max 5), sharedBenefits
PaymentHistory  → subscription, amount, date, status
```

### Design Approach
- **State**: Membership status — TRIAL → ACTIVE → SUSPENDED (payment failed) → CANCELLED
- **Strategy**: Pricing strategy per membership type and region
- **Observer**: Renewal reminder (7 days, 1 day before), payment failure notification
- **Decorator**: Benefits can be layered (Base Prime + Prime Video + Prime Music)
- **Chain of Responsibility**: Payment retry — first card → backup card → bank account → suspend

### Patterns Used
- State (membership lifecycle)
- Strategy (pricing)
- Observer (notifications)
- Chain of Responsibility (payment retry)

### Traps & Follow-ups
- **"Family member uses benefits — how tracked?"** → Each benefit check goes through `MembershipValidator` which resolves family group membership
- **"Payment fails on renewal?"** → Retry 3 times over 3 days → grace period 7 days → suspend → cancel
- **"Prorated refund on cancellation?"** → `RefundCalculator`: (daysRemaining / totalDays) * amount
- **"Concurrent renewal and cancellation?"** → Optimistic locking on `Membership` entity

---

## Problem 3: Inventory Management System

**Frequency**: High | **Level**: Hard

### The Problem
Design Amazon's warehouse inventory system managing millions of SKUs across warehouses.

### Requirements to Clarify
- Multiple warehouses, each holding multiple SKUs
- Real-time stock levels
- Reserve inventory when order placed, deduct when shipped
- Reorder triggers when stock falls below threshold
- Demand forecasting hints (for extensions)

### Core Entities
```
SKU             → id, name, category, dimensions, weight
Warehouse       → id, location, capacity
InventoryItem   → sku, warehouse, totalQty, availableQty, reservedQty, reorderPoint
Reservation     → id, orderId, sku, warehouse, quantity, expiryTime
StockMovement   → sku, warehouse, type (IN/OUT/RESERVE/RELEASE), quantity, timestamp
PurchaseOrder   → supplierId, items, expectedArrival, status
```

### Design Approach
- **Command**: Every stock change is a command (ReceiveStock, ReserveStock, ShipStock, ReturnStock) — enables audit trail and undo
- **Observer**: When `availableQty < reorderPoint`, notify `PurchaseOrderService`
- **Strategy**: `WarehouseSelectionStrategy` — pick closest warehouse with stock to order location
- **CQRS hint**: High-volume reads (availability checks) vs writes (reservations) benefit from read/write separation

### Critical Concurrency Design
```
Reserve flow:
  1. Check availableQty >= requested (READ)
  2. Decrement availableQty, increment reservedQty (WRITE)
  → Steps 1+2 must be atomic. 
  → Use: DB transaction with row-level lock on InventoryItem, or optimistic locking with version field
```

### Patterns Used
- Command (stock operations)
- Observer (reorder triggers)
- Strategy (warehouse selection)

### Traps & Follow-ups
- **"Race condition — two orders reserve last item?"** → Optimistic locking: `UPDATE inventory SET available = available - 1, version = version + 1 WHERE id = ? AND available >= 1 AND version = ?`. If 0 rows affected → retry
- **"Reservation expiry?"** → Timed reservations. Background job or scheduled task releases expired reservations
- **"Multi-warehouse fulfillment (split shipment)?"** → `FulfillmentPlanner` splits order across warehouses if single warehouse can't fulfill
- **"Demand forecasting for reorder point?"** → ML extension — `DemandForecaster` strategy adjusts `reorderPoint` dynamically

---

## Problem 4: Order Processing & Fulfillment System

**Frequency**: High | **Level**: Hard

### The Problem
Design the end-to-end order processing system from cart checkout to delivery.

### Requirements to Clarify
- Cart → Order → Payment → Fulfillment → Delivery → Completion
- Multiple payment methods
- Order cancellation (before shipment)
- Order modification (before payment)
- Real-time status tracking

### Core Entities
```
Cart            → userId, items (List<CartItem>), totalAmount
Order           → id, userId, items, shippingAddress, status, paymentId, shipmentId
OrderItem       → orderId, skuId, quantity, unitPrice, subtotal
Payment         → id, orderId, amount, method, status, transactionId
Shipment        → id, orderId, warehouseId, carrier, trackingId, estimatedDelivery, status
OrderEvent      → orderId, eventType, timestamp, metadata (for audit trail)
```

### State Machine (The Core)
```
CART
  ↓ checkout()
PENDING_PAYMENT
  ↓ paymentSucceeded()    → PAYMENT_FAILED (terminal)
CONFIRMED
  ↓ inventoryReserved()
PROCESSING
  ↓ shipped()
SHIPPED
  ↓ delivered()
DELIVERED (terminal)
  ↓ cancel() (only before PROCESSING)
CANCELLED (terminal)
```

### Design Approach
- **State**: Order lifecycle as above
- **Command**: Each order transition is a command (PlaceOrderCommand, CancelOrderCommand) — for undo and audit
- **Chain of Responsibility**: Order validation pipeline — StockValidation → PriceValidation → FraudValidation → PaymentValidation
- **Observer**: Order events publish to notification service (email/SMS), analytics, loyalty points service
- **Strategy**: Shipping carrier selection (cheapest, fastest, most reliable)

### Patterns Used
- State (order lifecycle)
- Chain of Responsibility (validation pipeline)
- Observer (event-driven side effects)
- Strategy (carrier selection)
- Command (audit trail)

### Traps & Follow-ups
- **"Payment succeeds but inventory fails?"** → Saga pattern: compensating transaction — refund payment, cancel order
- **"Order modification after payment?"** → Complex: calculate delta, charge/refund difference, re-check inventory
- **"Idempotency in payment?"** → Idempotency key per order — prevents double charge on retry
- **"Distributed transaction across payment + inventory?"** → Two-phase commit OR Saga (choreography or orchestration)

---

## Problem 5: Product Recommendation Engine (Design Only)

**Frequency**: Medium | **Level**: Hard

### The Problem
Design the data model and service layer for Amazon's product recommendations.

### Requirements to Clarify
- "Customers who bought X also bought Y" (collaborative filtering)
- "Based on your browsing history" (content-based)
- "Frequently bought together" (basket analysis)
- Real-time updates vs batch
- A/B testing of recommendation algorithms

### Core Entities
```
UserBehavior    → userId, productId, eventType (VIEW/CLICK/ADD_TO_CART/PURCHASE), timestamp
ProductSimilarity → productA, productB, similarityScore, algorithm, computedAt
UserProfile     → userId, preferredCategories, priceRange, brandAffinities
Recommendation  → userId, products (ranked), algorithm, generatedAt, expiresAt
ABExperiment    → id, name, algorithms (variants), userSegmentation, metrics
```

### Design Approach
- **Strategy**: `RecommendationStrategy` interface — `CollaborativeFiltering`, `ContentBased`, `HybridStrategy`
- **Composite**: `HybridRecommendationStrategy` combines multiple strategies with weights
- **Factory**: `RecommendationStrategyFactory` selects strategy based on user segment and A/B test bucket
- **Observer**: User behavior events trigger async profile updates
- **CQRS**: Recommendations are pre-computed (write side: batch jobs), served from cache (read side)

### Traps & Follow-ups
- **"Cold start problem — new user with no history?"** → Fall back to popularity-based recommendations for new users
- **"Real-time vs batch?"** → Hybrid: batch-compute similarity scores nightly, serve from cache, blend with real-time click stream
- **"A/B testing recommendation algorithms?"** → `ABTestingService` assigns user to variant, routes to respective strategy, collects conversion metrics
