# 14 — Modern Practices, 50 Practice Problems & Interview Cheat Sheet

---

# PART A: Modern LLD Practices (Top 1% Territory)

## 1. Clean Architecture in LLD

**The Rule**: Dependencies point inward. Domain knows nothing about infrastructure.

```
Layers (outer → inner):
  Infrastructure (DB, HTTP, messaging)
      ↓
  Application (use cases, services)  
      ↓
  Domain (entities, value objects, interfaces)
      ↑
  No dependency should point outward

In practice:
  OrderRepository (interface) lives in Domain
  MySQLOrderRepository (impl) lives in Infrastructure
  Domain never imports from Infrastructure
```

**Why it matters in interviews**: Shows you can design systems that survive tech stack changes. DB swap (MySQL → MongoDB) = change only Infrastructure layer.

---

## 2. Domain-Driven Design (DDD) Concepts

### Aggregate
A cluster of domain objects treated as a single unit. One root entity, accessed only through root.

```
Order Aggregate:
  Root: Order
  Children: OrderItem, PaymentInfo, ShippingAddress
  
Rules:
  - External objects reference only Order (not OrderItem directly)
  - Transactions stay within aggregate boundary
  - Cross-aggregate communication via domain events
```

### Domain Events
```
Order placed → OrderPlacedEvent published
  → InventoryService subscribes → reserves stock
  → NotificationService subscribes → sends email
  → LoyaltyService subscribes → awards points

Events are immutable records of what happened.
Services react to events — no tight coupling.
```

### Repository Pattern (DDD style)
```
Repository:
  - Speaks domain language (not SQL)
  - Returns domain objects (not DTOs or rows)
  - Abstracts persistence completely

OrderRepository:
  Optional<Order> findById(OrderId id)
  List<Order> findByCustomerAndStatus(CustomerId id, OrderStatus status)
  void save(Order order)
  
NOT: "SELECT * FROM orders WHERE customer_id = ?"
```

---

## 3. CQRS (Command Query Responsibility Segregation)

```
One model for reads, different model for writes.

Write side: handles commands (CreateOrder, CancelOrder)
  - Validates business rules
  - Persists to write store (normalized DB)
  - Publishes domain events

Read side: handles queries (GetOrderSummary, GetOrderHistory)  
  - Reads from read store (denormalized, optimized for query)
  - Pre-joined, pre-aggregated views
  - Updated by consuming write-side events

When to mention in LLD:
  - BookMyShow seat availability (read-heavy)
  - News feed (read-heavy, write-heavy separately)
  - Stock prices (write: stream, read: cached)
```

---

## 4. Hexagonal Architecture (Ports & Adapters)

```
Core application surrounded by ports (interfaces):
  - Driving ports: how app is used (REST API, CLI, message consumer)
  - Driven ports: what app uses (DB, email, payment gateway)

Adapters plug into ports:
  - REST adapter → uses HTTP driving port
  - MySQL adapter → implements DB driven port

Benefit: can swap any adapter without touching core logic
Test: can test core with in-memory adapters, no real DB needed
```

---

## 5. Event Sourcing

```
Instead of storing current state, store sequence of events.

UserAccountEvents:
  AccountCreated(id, email, timestamp)
  EmailChanged(id, newEmail, timestamp)
  PasswordReset(id, timestamp)
  AccountSuspended(id, reason, timestamp)

Current state = replay all events in order

Benefits:
  - Complete audit history (every change tracked)
  - Rebuild state to any point in time
  - Append-only → high write throughput
  - Natural fit for event-driven systems

When to mention:
  - Banking transactions
  - Order processing (LLD shows you know this)
  - Collaborative editing (document = ordered ops)
```

---

## 6. Saga Pattern (Distributed Transactions in LLD)

```
Problem: Order placement touches multiple services (Payment, Inventory, Shipping)
  - Can't use DB transaction across services
  - If payment succeeds but inventory fails → inconsistent state

Saga: sequence of local transactions + compensating transactions

OrderSaga:
  1. ReserveInventory → success
  2. ProcessPayment → success  
  3. CreateShipment → FAIL
  ↓ compensating:
  3. RefundPayment (compensation for step 2)
  4. ReleaseInventory (compensation for step 1)

In LLD interview: mention this when asked "what if payment succeeds but inventory fails?"
Shows distributed systems awareness.
```

---

# PART B: 50 Practice Problems

## Easy (Must Do First)

| # | Problem | Key Pattern | Companies |
|---|---|---|---|
| 1 | Parking Lot | State, Strategy, Factory | Google, Amazon, Uber |
| 2 | Library Management | Strategy (search), Observer | Amazon, Infosys |
| 3 | ATM Machine | State (idle/card/pin/tx), CoR | Amazon, Wipro |
| 4 | Tic Tac Toe | Template Method, Strategy (AI) | Meta, Microsoft |
| 5 | Snake and Ladder | Observer, Builder | Goldman Sachs, PhonePe |
| 6 | Vending Machine | State (idle/selecting/dispensing) | Amazon, Microsoft |
| 7 | Coffee Machine | Builder, State | Amazon |
| 8 | Traffic Signal | State (red/yellow/green), Observer | Flipkart |
| 9 | Logger Framework | Singleton, CoR (log levels), Strategy | Every company |
| 10 | File System (basic) | Composite, Iterator | Google, Dropbox |

## Medium (Core Interview Level)

| # | Problem | Key Pattern | Companies |
|---|---|---|---|
| 11 | Elevator System | State, Strategy (SCAN), Observer | Google, Uber, Amazon |
| 12 | Chess | Strategy, Template Method | Google, Amazon, Meta |
| 13 | BookMyShow | State, Observer, synchronized | Amazon, Flipkart |
| 14 | Hotel Management | Strategy (pricing/room selection), Observer | Airbnb, OYO |
| 15 | Restaurant POS | Builder (order), Observer (kitchen display) | Zomato, Swiggy |
| 16 | URL Shortener | Strategy (encoding), Builder | Meta, Razorpay |
| 17 | Cache System (LRU) | HashMap + DLL | Google, Amazon, Meta |
| 18 | Notification System | Strategy (channel), Observer, Chain | Meta, Google |
| 19 | Task Scheduler / Cron | Strategy, PriorityQueue, Observer | Amazon, Microsoft |
| 20 | Meeting Room Booking | Interval Tree, Observer | Google, Microsoft |

## Medium-Hard

| # | Problem | Key Pattern | Companies |
|---|---|---|---|
| 21 | Splitwise | Strategy (split), Greedy (simplify) | Google, Razorpay |
| 22 | Rate Limiter | Strategy (token/sliding/fixed) | Google, Meta, Stripe |
| 23 | Food Delivery (Zomato) | Observer, Strategy (assignment) | Zomato, Swiggy |
| 24 | Cab Booking (Uber) | Observer, Strategy (matching) | Uber, Ola |
| 25 | Social Media Feed | Strategy (ranking), Observer | Meta, Twitter |
| 26 | Payment System | CoR (validation), State, Strategy | PayPal, Stripe |
| 27 | Chat System (basic) | Observer, Mediator | Meta, Microsoft |
| 28 | Inventory Management | Command (stock ops), Observer | Amazon |
| 29 | Online Auction | Observer (bidding), State | Amazon, eBay |
| 30 | Document Editor (basic) | Command (undo/redo), Observer | Google, Microsoft |

## Hard (Differentiate from Other Candidates)

| # | Problem | Key Pattern | Companies |
|---|---|---|---|
| 31 | Stock Exchange Engine | PriorityQueue, State, Observer | Goldman Sachs |
| 32 | Rate Limiter (distributed) | Token Bucket + Redis | Google, Stripe |
| 33 | LRU + LFU Cache | HashMap + DLL / Bucket per freq | Google, Amazon |
| 34 | Unix File Search (`find`) | Composite, Strategy (filter) | Amazon, Google |
| 35 | Rule Engine | Composite, Strategy | Amazon, Razorpay |
| 36 | Pub-Sub Message Queue | Observer, Strategy (delivery) | Meta, Google |
| 37 | Collaborative Editor (OT) | Command, Observer, Event Sourcing | Google, Microsoft |
| 38 | Calendar with Conflicts | Interval Tree, Observer | Google |
| 39 | Recommendation Engine | Strategy, Composite | Netflix, Amazon |
| 40 | API Gateway | CoR (auth/rate/route), Proxy | Every company |

## FAANG+ Level (Staff Engineer Territory)

| # | Problem | Key Pattern | Companies |
|---|---|---|---|
| 41 | Distributed Cache (Redis-like) | Consistent hashing, Strategy (eviction) | Google, Amazon |
| 42 | Circuit Breaker | State (closed/open/half-open) | All microservice companies |
| 43 | Saga Orchestrator | State, Command, CoR | Amazon, Flipkart |
| 44 | Event Sourcing System | Command, Observer, Event Store | Financial companies |
| 45 | Search Autocomplete (Trie) | Trie, Strategy (ranking) | Google, Amazon |
| 46 | Workflow Engine | State, Command, DAG | Amazon, Temporal |
| 47 | A/B Testing Framework | Strategy, Observer, Builder | Meta, Google, Netflix |
| 48 | Feature Flag System | Strategy, Observer, Builder | Every product company |
| 49 | Multi-tenant SaaS Platform | Strategy (tenant isolation), Proxy | Salesforce, ServiceNow |
| 50 | Distributed Job Scheduler | Strategy, Observer, PriorityQueue | Google, AWS |

---

# PART C: Interview Day Cheat Sheet

## Pattern Recognition (What to Code When)

```
See "multiple ways to do X"          → Strategy
See "hierarchy of objects"           → Composite
See "add features without modifying" → Decorator / Strategy
See "object creation complexity"     → Factory / Builder
See "one-to-many notification"       → Observer
See "state-based behavior"           → State
See "encapsulate action/undo"        → Command
See "sequence of handlers"           → Chain of Responsibility
See "simplify complex subsystem"     → Facade
See "control access"                 → Proxy
See "bridge incompatible interfaces" → Adapter
See "one instance globally"          → Singleton
```

## Opening Every Interview

> "Before I start designing, can I ask a few clarifying questions?
> - What are the core use cases we should focus on?
> - What scale are we designing for?
> - Is this read-heavy or write-heavy?
> - Do we need to handle concurrency?
> - Any specific constraints I should know about?"

## Proactively Mention These (Even If Not Asked)

- "I'm making the X interface-based so we can swap implementations without modifying this class"
- "This operation modifies shared state — I'd use synchronized/AtomicInteger/ConcurrentHashMap here"
- "I'm favoring composition over inheritance here because..."
- "To extend this with Y, we'd just add a new implementation of Z — zero modification to existing code"

## When Asked "How Would You Scale This?"

```
1. Identify bottlenecks: DB writes? Fan-out? Computation?
2. Cache hot data (Redis for reads)
3. Queue heavy operations (Kafka/SQS for async)
4. Shard by natural partition key (userId, orderId)
5. CQRS for read/write separation
6. CDN for static content
```

## Top 10 Problems — Practice These First

```
1. Parking Lot         (everywhere)
2. LRU Cache           (everywhere)
3. Rate Limiter        (Google, Meta, Stripe)
4. Elevator System     (Google, Uber)
5. Splitwise           (Google, Razorpay)
6. BookMyShow          (Amazon, Flipkart)
7. Notification System (Meta, Google)
8. Snake and Ladder    (Goldman Sachs)
9. Unix find command   (Amazon)
10. Rule Engine        (Amazon — new, 2025-2026)
```
