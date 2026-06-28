# 20 — Staff Engineer Level: What Separates the Top 1%

> Senior engineers design correct systems. Staff engineers design systems that are correct, observable, operable, and evolvable. This file is what separates a 6/10 LLD from a 10/10.

---

## 1. Observability Hooks — Design for Production From Day One

Most candidates design a system that works. Top candidates design a system that can be *diagnosed when it breaks*. Interviewers at Staff level notice this immediately.

### The Three Pillars in LLD

**Logging** — What happened and when
```java
// BAD: no logging, good luck debugging production
public Ticket park(Vehicle vehicle) {
    ParkingSpot spot = finder.find(vehicle.getType());
    spot.occupy();
    return ticketRepo.save(new Ticket(vehicle, spot));
}

// GOOD: structured logging at decision points
public Ticket park(Vehicle vehicle) {
    log.info("Park request received vehiclePlate={} type={}", 
             vehicle.getPlate(), vehicle.getType());

    Optional<ParkingSpot> spotOpt = finder.find(vehicle.getType());
    if (spotOpt.isEmpty()) {
        log.warn("No spot available for type={} lotId={}", vehicle.getType(), lotId);
        throw new ParkingFullException(vehicle.getType());
    }

    ParkingSpot spot = spotOpt.get();
    spot.occupy();
    Ticket ticket = ticketRepo.save(new Ticket(vehicle, spot));

    log.info("Vehicle parked successfully plate={} spotId={} floor={} ticketId={}",
             vehicle.getPlate(), spot.getId(), spot.getFloor(), ticket.getId());

    return ticket;
}
```

**Metrics** — How the system is performing
```java
// Add metrics to every service — show this in interviews
public class InstrumentedParkingService implements ParkingService {
    private final ParkingService delegate;
    private final MetricsRegistry metrics;

    public Ticket park(Vehicle vehicle) {
        metrics.increment("parking.requests.total", "type", vehicle.getType().name());
        long start = System.currentTimeMillis();
        try {
            Ticket ticket = delegate.park(vehicle);
            metrics.increment("parking.success.total");
            metrics.recordTimer("parking.duration.ms", System.currentTimeMillis() - start);
            metrics.gauge("parking.occupancy.rate", getOccupancyRate());
            return ticket;
        } catch (ParkingFullException e) {
            metrics.increment("parking.failures.total", "reason", "LOT_FULL");
            throw e;
        } catch (Exception e) {
            metrics.increment("parking.failures.total", "reason", "SYSTEM_ERROR");
            throw e;
        }
    }
}

// Key metrics to mention for any system:
// - Request rate (requests/second)
// - Error rate (errors/total requests)
// - Latency (p50, p95, p99 — not just average)
// - Resource utilization (occupancy, queue depth, pool usage)
// - Business metrics (bookings/hour, revenue/day)
```

**Distributed Tracing** — Following a request across services
```java
// Inject trace ID at entry point, propagate through
public class ParkingController {
    public ResponseEntity<Ticket> park(ParkRequest request) {
        String traceId = request.getHeader("X-Trace-Id", generateTraceId());
        MDC.put("traceId", traceId); // Mapped Diagnostic Context — auto-included in all logs
        try {
            return ResponseEntity.ok(parkingService.park(request.toVehicle(), traceId));
        } finally {
            MDC.clear();
        }
    }
}
```

**Interview script**: "I always design observability in from the start. I'd wrap the service with an `InstrumentedDecorator` for metrics — request rate, error rate, p95 latency. I'd use structured logging with a trace ID at every decision point. When this breaks at 3am, whoever's on-call should be able to diagnose it in minutes, not hours."

---

## 2. Idempotency — Critical for Payment and Booking Systems

**The problem**: Network is unreliable. Clients retry. Without idempotency, a retry can charge the user twice, book two seats, send two emails.

```java
// Pattern: Idempotency Key
public interface IdempotentOperation<Req, Resp> {
    Resp execute(String idempotencyKey, Req request);
}

public class IdempotentBookingService implements IdempotentOperation<BookingRequest, Booking> {
    private final BookingService delegate;
    private final IdempotencyStore store; // Redis or DB table

    public Booking execute(String idempotencyKey, BookingRequest request) {
        // 1. Check if we've seen this key before
        Optional<Booking> cached = store.get(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Duplicate request detected key={} — returning cached response", idempotencyKey);
            return cached.get(); // same response, no side effects
        }

        // 2. Try to claim the key (atomic set-if-absent)
        boolean claimed = store.setIfAbsent(idempotencyKey, "PROCESSING", Duration.ofMinutes(10));
        if (!claimed) {
            // Another request is processing with same key right now
            throw new ConcurrentRequestException("Request with key " + idempotencyKey + " is in progress");
        }

        try {
            // 3. Execute the actual operation
            Booking booking = delegate.createBooking(request);

            // 4. Store result — subsequent retries return this
            store.set(idempotencyKey, booking, Duration.ofDays(7));
            return booking;
        } catch (Exception e) {
            store.delete(idempotencyKey); // allow retry on failure
            throw e;
        }
    }
}

// Usage: client generates idempotency key, includes in request
// Client: UUID generated per "attempt" (regenerated only if user explicitly retries)
// POST /bookings  
//   Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
//   Body: { showId, seatIds, userId }

// Payment idempotency — most critical
public class IdempotentPaymentService {
    public PaymentResult charge(String idempotencyKey, PaymentRequest request) {
        // Same pattern — key maps to completed transaction
        // If key exists → return existing result (don't charge again!)
        // If key missing → process, store result
    }
}
```

---

## 3. Backward Compatibility in API Design

**Senior signal**: When asked to "add X to the system," the first thing to ask is "how do we not break existing clients?"

```java
// BAD: breaking change
public class User {
    private String name; // was "name"
    // Renamed to:
    private String fullName; // BREAKS all clients using "name"
}

// GOOD: non-breaking evolution strategies

// Strategy 1: Add fields, never remove
public class User {
    private String name;     // keep for backward compat
    private String fullName; // new field — optional
    // Clients using "name" still work. New clients use "fullName"
}

// Strategy 2: Versioned APIs
// v1: GET /api/v1/users/{id} → { name, email }
// v2: GET /api/v2/users/{id} → { firstName, lastName, email, phone }
// Both run simultaneously. Clients migrate at their own pace.

// Strategy 3: Tolerant reader pattern
// Consumers ignore unknown fields, use defaults for missing fields
public class UserResponse {
    private String name;
    @JsonIgnoreProperties(ignoreUnknown = true) // Jackson: ignore new fields from server
    private String email;
    private String phone = ""; // default if missing from older server
}

// Strategy 4: Feature flags for new behavior
public class BookingService {
    public Booking createBooking(BookingRequest request) {
        if (featureFlags.isEnabled("NEW_SEAT_LOCK_STRATEGY", request.getUserId())) {
            return createBookingV2(request); // new behavior for % of users
        }
        return createBookingV1(request); // old behavior
    }
}
```

---

## 4. CAP Theorem in LLD Decisions

**When to mention**: Any time you design a distributed or multi-node system.

```
CAP Theorem: In a distributed system, you can guarantee only 2 of 3:
  C = Consistency (all nodes see same data at same time)
  A = Availability (every request gets a response)
  P = Partition Tolerance (system works despite network splits)
  
Since network partitions are unavoidable in distributed systems,
you must choose: CP or AP

CP systems (sacrifice availability on partition):
  - Banking transactions → must be consistent, ok to be unavailable temporarily
  - Inventory reservation → must not double-sell
  - Examples: ZooKeeper, HBase, etcd

AP systems (sacrifice consistency on partition):
  - DNS, shopping cart, social media likes → ok to show slightly stale data
  - Better to be available with eventual consistency than unavailable
  - Examples: Cassandra, DynamoDB (default), CouchDB

In LLD interviews, apply this:
```

```java
// For BookMyShow seat booking → CP: must be consistent
// Two users cannot book same seat — consistency > availability
// Implementation: pessimistic locking or strong serialization
// Trade-off: during DB failover, booking is unavailable (acceptable)

// For Netflix viewing history → AP: eventual consistency ok
// If view history is delayed by 1 second, no real impact
// Implementation: write to Kafka → async Cassandra update
// Trade-off: "Continue Watching" might show slightly stale position (acceptable)

// For a bank transfer → CP: strong consistency required
// Must never show incorrect balance
// Implementation: single DB with ACID transactions
// For distributed banks: 2PC or Saga with compensating transactions

// Interview script:
// "For the seat booking system, I'd choose CP — we cannot double-sell.
//  I'd accept that during a DB partition, the booking service is temporarily unavailable.
//  For the recommendation engine, I'd choose AP — showing slightly stale
//  recommendations is fine. I'd use Cassandra and accept eventual consistency."
```

---

## 5. Designing for Failure — Circuit Breaker Pattern

```java
// Problem: ServiceA calls ServiceB. ServiceB is slow/down.
// Without circuit breaker: all of ServiceA's threads block waiting for B → A goes down too

public enum CircuitState { CLOSED, OPEN, HALF_OPEN }

public class CircuitBreaker {
    private volatile CircuitState state = CircuitState.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile Instant lastFailureTime;

    private final int failureThreshold;       // open after N failures
    private final Duration resetTimeout;      // try again after duration
    private final int successThreshold;       // close after N successes in HALF_OPEN

    private final AtomicInteger successCount = new AtomicInteger(0);

    public <T> T execute(Supplier<T> operation) {
        switch (state) {
            case OPEN -> {
                if (Duration.between(lastFailureTime, Instant.now()).compareTo(resetTimeout) > 0) {
                    state = CircuitState.HALF_OPEN;
                    successCount.set(0);
                } else {
                    throw new CircuitBreakerOpenException("Circuit is OPEN — service unavailable");
                }
            }
            case CLOSED, HALF_OPEN -> {
                // fall through to execute
            }
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        failureCount.set(0);
        if (state == CircuitState.HALF_OPEN) {
            if (successCount.incrementAndGet() >= successThreshold) {
                state = CircuitState.CLOSED;
                log.info("Circuit CLOSED — service recovered");
            }
        }
    }

    private void onFailure() {
        lastFailureTime = Instant.now();
        if (failureCount.incrementAndGet() >= failureThreshold) {
            state = CircuitState.OPEN;
            log.warn("Circuit OPENED — too many failures");
        }
    }
}

// Usage: wrap external service calls
public class PaymentService {
    private final CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30), 2);
    private final ExternalPaymentGateway gateway;

    public PaymentResult charge(PaymentRequest request) {
        return breaker.execute(() -> gateway.charge(request));
        // If gateway is down: circuit opens, calls fail fast, service stays responsive
        // After 30s: tries again (HALF_OPEN), if 2 successes → CLOSED
    }
}
```

**Interview signal**: "I'd wrap all external service calls in circuit breakers. If the payment gateway goes down, we don't want threads piling up waiting — we want fast failures and a fallback response."

---

## 6. Mock Interview: Full 45-Minute Transcript

### Problem: Design a Notification System

**Interviewer**: Design a notification system that can send notifications via email, SMS, and push notifications.

**Candidate**: Before I start, a few questions:
- "Should one notification go to one channel or multiple? For example, send email AND push?"
- "Do we need delivery receipts — know if the notification was received?"
- "Should notifications be sent immediately or can they be batched?"
- "User preferences — should users be able to mute certain notification types?"
- "At what scale — hundreds of users or millions?"

**Interviewer**: Multiple channels per notification based on user preference. Delivery receipts yes. Scale for millions.

**Candidate**: Got it. Let me sketch the core entities first.

*[Draws on whiteboard]*

```
NotificationRequest → what to send, to whom, priority
User → preferences (channels, quiet hours, frequency limits)
NotificationChannel → EMAIL | SMS | PUSH | SLACK
DeliveryRecord → tracks success/failure per channel per notification
NotificationTemplate → pre-defined message formats by type
```

"I see three core interfaces:
1. `NotificationChannel` — sends via one channel
2. `ChannelSelector` — decides which channels to use for a user
3. `NotificationRouter` — orchestrates the whole flow"

*[Starts coding]*

```java
public interface NotificationChannel {
    DeliveryResult send(Notification notification, User recipient);
    ChannelType getType();
    boolean isAvailable(); // health check
}

public interface ChannelSelector {
    List<NotificationChannel> selectChannels(User user, NotificationType type);
}

public class UserPreferenceChannelSelector implements ChannelSelector {
    public List<NotificationChannel> selectChannels(User user, NotificationType type) {
        return user.getChannelPreferences(type).stream()
            .filter(pref -> pref.isEnabled())
            .filter(pref -> !user.isInQuietHours())
            .map(pref -> channelRegistry.get(pref.getChannelType()))
            .filter(NotificationChannel::isAvailable)
            .collect(toList());
    }
}

public class NotificationRouter {
    private final ChannelSelector selector;
    private final DeliveryRecordRepository deliveryRepo;
    private final ExecutorService executor; // async delivery

    public void send(NotificationRequest request) {
        User recipient = userRepo.findById(request.getUserId());
        List<NotificationChannel> channels = selector.selectChannels(recipient, request.getType());

        if (channels.isEmpty()) {
            log.warn("No channels available userId={} type={}", recipient.getId(), request.getType());
            return;
        }

        channels.forEach(channel ->
            executor.submit(() -> sendViaChannel(channel, request, recipient))
        );
    }

    private void sendViaChannel(NotificationChannel channel, NotificationRequest request, User recipient) {
        DeliveryRecord record = new DeliveryRecord(request.getId(), channel.getType(), Instant.now());
        try {
            DeliveryResult result = channel.send(toNotification(request), recipient);
            record.markDelivered(result);
            metrics.increment("notification.delivered", "channel", channel.getType().name());
        } catch (Exception e) {
            record.markFailed(e.getMessage());
            metrics.increment("notification.failed", "channel", channel.getType().name());
            log.error("Delivery failed userId={} channel={}", recipient.getId(), channel.getType(), e);
        } finally {
            deliveryRepo.save(record);
        }
    }
}
```

**Interviewer**: "What if we need to add Slack notifications?"

**Candidate**: "Create `SlackChannel implements NotificationChannel`. Register it in `channelRegistry`. Add `SLACK` to `ChannelType` enum. Add Slack preference to user settings. Zero modification to `NotificationRouter` or any existing channel — Open/Closed."

**Interviewer**: "What about rate limiting — not spam the user?"

**Candidate**: "I'd add a `RateLimitedChannelSelector` decorator around `UserPreferenceChannelSelector`. It checks a `RateLimitStore` — if user received more than N notifications of this type in the last hour, filter out that channel. The underlying selector doesn't know about rate limiting."

**Interviewer**: "Thread safety concerns?"

**Candidate**: "The executor handles async dispatch. `DeliveryRecordRepository` needs to be thread-safe — if it's DB-backed, the DB handles that. The `channels` list returned by selector is immutable. The `UserPreferenceChannelSelector` is stateless — safe. The `metrics` client should be thread-safe — standard metrics libraries are. One area I'd audit: if we cache user preferences, the cache needs ConcurrentHashMap and appropriate TTL."

**Interviewer**: "How would this work at millions of notifications per day?"

**Candidate**: "Three changes for scale: First, instead of synchronous HTTP calls to email/SMS providers, publish to a message queue per channel (Kafka topic per channel type). Workers consume from each topic — EmailWorkers, SMSWorkers, PushWorkers can scale independently. Second, batch SMS and email if the provider supports it — reduces API calls. Third, rate limiting moves to Redis instead of in-memory — works across multiple service instances."

---

## 7. The Words That Separate Levels

| Junior says | Senior/Staff says |
|---|---|
| "I'll use a list here" | "CopyOnWriteArrayList since this is read-heavy with occasional adds" |
| "I'll add logging later" | "I'd wrap this in an InstrumentedDecorator for metrics and add structured logs at each state transition" |
| "I'll handle errors later" | "Let me define the failure modes first: what if the payment succeeds but inventory fails?" |
| "Add a new type here" | "With Strategy + Registry, adding a new type requires zero modification to existing code" |
| "Make it thread-safe" | "This field is accessed by multiple threads — I'd use AtomicInteger here. This map needs ConcurrentHashMap. The occupy() method needs to be synchronized to prevent double-booking" |
| "It might not scale" | "This works for single-node. For scale, I'd shard by userId, use Redis for the rate limiter state, and introduce Kafka for async fan-out" |
| "I'll use a database" | "I'd use a relational DB for transactional seat booking (ACID matters here), Redis for active session state, and Cassandra for time-series event data" |

---

## 8. Final Checklist Before Finishing Any LLD Round

```
□ Did I clarify requirements before coding?
□ Did I identify all entities and their relationships?
□ Did I start with interfaces, not concrete classes?
□ Did I use the right pattern (not just the one I like most)?
□ Did I address thread safety proactively?
□ Did I mention at least one extension and how it'd be added?
□ Did I mention observability (logging, metrics)?
□ Did I think about failure modes?
□ Is the code readable with clear method/variable names?
□ Would adding a new feature require modifying existing classes?
  → If yes: is there a pattern that fixes this?
```

**The final test**: Can someone who wasn't in the interview read your code/diagram in 10 years and understand it without asking you anything?

If yes → you've designed something world-class.
