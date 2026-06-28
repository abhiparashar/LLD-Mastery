# 10 — Counter Questions, Traps & Anti-Patterns

---

## Section A: Counter Questions by Category

### OOP & Design

**Q: Why use interface over abstract class?**
> Interfaces allow multiple inheritance, force a contract without implementation leakage, and are easier to mock in tests. Use abstract class only when you need shared state or partial implementation.

**Q: Composition vs Inheritance — when each?**
> Inheritance = IS-A (real, stable hierarchy). Composition = HAS-A or "I want this behavior". Composition is more flexible — you can change composed objects at runtime. Rule: default to composition.

**Q: What if I have 10 concrete implementations of one interface — is that too many?**
> No. That's OCP working correctly. The question is whether each is *meaningfully different*. If 9 of them are copy-paste with one line changed, extract common behavior into an abstract class.

**Q: What's the cost of too many abstractions?**
> Navigation complexity, indirection makes debugging harder, and onboarding new developers takes longer. Apply abstractions where there are real extension points, not speculatively.

---

### SOLID Deep Dives

**Q: Give me a case where SOLID conflicts with itself.**
> SRP says extract email sending from UserService. DIP says inject EmailService. But now you've added a new dependency — every caller of UserService must now provide an EmailService. Sometimes a pragmatic god class is simpler for small systems.

**Q: Can a class violate SRP but follow OCP?**
> Yes. `OrderProcessor` might handle both pricing and notification (SRP violation) but use Strategy patterns for both (OCP compliant). SOLID principles are orthogonal.

**Q: Real-world example of LSP violation in production code?**
> Java's `Stack extends Vector`. Stack IS-NOT-A Vector. You can call `stack.add(0, element)` (Vector method) which inserts at position 0 — destroying Stack semantics. The fix was `Deque`, which supersedes Stack.

---

### Concurrency

**Q: What's the difference between synchronized method and synchronized block?**
> Synchronized method locks `this` (or the class for static). Synchronized block lets you choose a specific lock object. Block is more granular — lock only what you need.

**Q: Why is ConcurrentHashMap faster than Hashtable?**
> Hashtable synchronizes every operation on the entire map. ConcurrentHashMap uses segment-level locking (pre-Java 8) or bucket-level CAS (Java 8+) — multiple threads read/write different segments simultaneously.

**Q: What is false sharing in multithreading?**
> Two threads update different variables that happen to live on the same CPU cache line. Each update invalidates the other thread's cache, causing constant cache misses. Fix: pad data to fill a cache line (Java 8: `@Contended`).

**Q: volatile vs AtomicInteger — when each?**
> `volatile` for visibility of a single write (boolean flag, single value). `AtomicInteger` for compound operations (check-then-act, increment). `volatile` does NOT make compound operations atomic.

**Q: Can you deadlock with ReentrantLock?**
> Yes — same rules as synchronized. Deadlock requires: mutual exclusion, hold-and-wait, no preemption, circular wait. Prevent by: lock ordering, `tryLock` with timeout, or eliminating the need for multiple locks.

---

### Design Pattern Deep Dives

**Q: Strategy pattern vs passing a lambda — what's the difference?**
> Functionally, a lambda IS a Strategy (single-method functional interface). Use explicit Strategy class when: it has state, needs multiple methods, or needs its own test class. Use lambda for simple, stateless algorithms.

**Q: Observer — push vs pull model?**
> Push: Subject sends data to observers. Observer gets everything even if irrelevant.
> Pull: Subject notifies observers, observers pull what they need.
> Pull is better when observers need different data from the same event.

**Q: When would Decorator become a problem?**
> Deep decorator stacks: `A(B(C(D(E(source)))))` — debugging is a nightmare. Stack traces are confusing. Every operation traverses the entire chain. Consider a pipeline/chain approach with explicit ordering instead.

**Q: Factory vs new — is new always bad?**
> No. Creating value objects (`new Money(100, USD)`, `new UserId("abc")`) directly is fine. Factory is needed when: creation logic is complex, type varies, caller shouldn't know the concrete type.

---

### System Design Extensions

**Q: How would you make your Parking Lot work across 100 locations?**
> - `ParkingLotRegistry` maps location ID to `ParkingLotService`
> - `ParkingLotRouter` selects nearest available lot by GPS
> - Shared `TicketRepository` (DB or distributed cache)
> - Event-driven: lot capacity events published to message bus

**Q: How would BookMyShow handle 1M concurrent seat bookings for a popular release?**
> - Queue-based access: users get a virtual queue token
> - Distributed locking on seats (Redis SETNX)
> - CQRS: separate read model (seat availability) from write model (bookings)
> - DB sharding by show_id

**Q: How would you add undo/redo to any system?**
> Command pattern. Every action is a `Command` object with `execute()` and `undo()`. Maintain `undoStack` and `redoStack`. `undo()` pops from undoStack, calls `undo()`, pushes to redoStack.

---

## Section B: The 10 Instant-Reject Anti-Patterns

### 1. The God Class

```java
// REJECT
public class SystemManager {
    public void createUser() { }
    public void processOrder() { }
    public void sendEmail() { }
    public void generatePDF() { }
    public void calculateTax() { }
    // 2000 lines
}
```
Fix: Split by responsibility.

---

### 2. Switch-Case on Type

```java
// REJECT
public double getFee(String type) {
    switch (type) {
        case "CAR": return 20;
        case "TRUCK": return 50;
        case "BIKE": return 10;
        // Adding new type = modify this = regression risk
    }
}
```
Fix: Polymorphism or Strategy pattern.

---

### 3. No Interfaces — Only Concrete Classes

```java
// REJECT
public class OrderService {
    private MySQLOrderRepo repo = new MySQLOrderRepo(); // hardcoded!
    private SMTPEmailSender email = new SMTPEmailSender(); // hardcoded!
}
```
Fix: Depend on interfaces, inject via constructor.

---

### 4. Mutable Public Fields

```java
// REJECT
public class ParkingSpot {
    public boolean isOccupied; // anyone can write!
    public Vehicle vehicle;
}
```
Fix: Private fields, controlled setters with validation.

---

### 5. Ignoring Thread Safety on Shared Mutable State

```java
// REJECT
public class TicketCounter {
    private int count = 0;
    public int next() { return ++count; } // race condition!
}
```
Fix: `AtomicInteger`, `synchronized`, or `ReentrantLock`.

---

### 6. Returning Mutable Internal Collections

```java
// REJECT
public List<ParkingSpot> getSpots() {
    return spots; // caller can call spots.clear()!
}
```
Fix: `Collections.unmodifiableList(spots)` or `new ArrayList<>(spots)`.

---

### 7. Exceptions for Flow Control

```java
// REJECT
public Optional<User> findUser(String id) {
    try {
        return Optional.of(userRepo.findById(id));
    } catch (UserNotFoundException e) {
        return Optional.empty(); // using exceptions for normal flow
    }
}
```
Fix: `Optional<User> findById(String id)` that returns empty without throwing.

---

### 8. Primitive Obsession

```java
// REJECT
public void bookSeat(String userId, String showId, String seatId, double price, String currency) { }

// Also reject
public class Order {
    private double price; // is this USD? INR? cents?
    private String status; // should be an enum!
}
```
Fix: Value objects (`Money`, `UserId`, `ShowId`), enums for status.

---

### 9. Null Returns

```java
// REJECT
public User findUser(String id) {
    if (cache.containsKey(id)) return cache.get(id);
    return null; // caller must remember to null-check. Always forgotten.
}
```
Fix: `Optional<User>`, or throw a specific exception if not-found is truly exceptional.

---

### 10. Over-Engineering

```java
// REJECT for a simple TODO app
public abstract class AbstractTaskFactory implements TaskCreationStrategyProvider {
    protected abstract TaskBuilderFactoryBean getTaskBuilderFactoryBean();
    public abstract TaskCreationStrategy getStrategy(TaskType type);
}
```
Fix: `new Task(title, dueDate)`. YAGNI. Build for what you have, not what you imagine.

---

## Section C: Modern Java Idioms for LLD

### Use Records for Value Objects (Java 16+)
```java
// Instead of boilerplate class with equals/hashCode
public record Money(BigDecimal amount, Currency currency) {
    // Compact constructor for validation
    public Money {
        Objects.requireNonNull(amount);
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException();
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new CurrencyMismatchException();
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

### Use Sealed Classes for State/Type Hierarchies (Java 17+)
```java
public sealed interface PaymentResult permits PaymentSuccess, PaymentFailure, PaymentPending { }

public record PaymentSuccess(String transactionId, Money amount) implements PaymentResult { }
public record PaymentFailure(String errorCode, String message) implements PaymentResult { }
public record PaymentPending(String referenceId) implements PaymentResult { }

// Pattern matching in switch (Java 21)
String message = switch (result) {
    case PaymentSuccess s -> "Paid: " + s.transactionId();
    case PaymentFailure f -> "Failed: " + f.errorCode();
    case PaymentPending p -> "Pending: " + p.referenceId();
};
```

### Use Optional Properly
```java
// WRONG uses of Optional
Optional<String> opt = Optional.of(null);  // throws NPE
if (opt.isPresent()) return opt.get();     // same as null check, pointless

// RIGHT
return userRepo.findById(id)
    .map(User::getEmail)
    .filter(email -> email.contains("@"))
    .orElseThrow(() -> new UserNotFoundException(id));
```

### Use Enums with Behavior
```java
public enum VehicleType {
    MOTORCYCLE(1, 10.0),
    CAR(4, 20.0),
    BUS(6, 50.0);
    
    private final int wheelCount;
    private final double hourlyRate;
    
    VehicleType(int wheelCount, double hourlyRate) {
        this.wheelCount = wheelCount;
        this.hourlyRate = hourlyRate;
    }
    
    public double calculateFee(int hours) { return hourlyRate * hours; }
}
// No switch-case needed anywhere — behavior is in the enum
```
