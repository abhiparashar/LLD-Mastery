# 02 — SOLID Principles

---

## S — Single Responsibility Principle

**One class = one reason to change.**

```java
// VIOLATION
public class UserService {
    public void createUser(User user) { /* DB logic */ }
    public void sendWelcomeEmail(User user) { /* Email logic */ }
    public void generateReport(List<User> users) { /* Report logic */ }
    public String formatUserAsJson(User user) { /* Serialization */ }
}

// FIX — each class has one job
public class UserRepository { public void save(User user) { } }
public class UserEmailService { public void sendWelcome(User user) { } }
public class UserReportService { public Report generate(List<User> users) { } }

public class UserService {  // Orchestrator only
    private final UserRepository repository;
    private final UserEmailService emailService;
    
    public void createUser(User user) {
        repository.save(user);
        emailService.sendWelcome(user);
    }
}
```

### SRP Traps & Counter Questions

**Q: How small is too small?**
A: SRP is about *cohesion*, not size. A class with 10 methods is fine if they all change for the same reason. A `UserValidator` with 10 validation rules is SRP-compliant.

**Q: What's the difference between SRP and Separation of Concerns?**
A: SoC is the architectural principle. SRP is the class-level implementation.

**Q: Isn't having many small classes harder to navigate?**
A: Yes — navigation cost is real. Tradeoff is reduced regression risk. Apply SRP where change is frequent.

---

## O — Open/Closed Principle

**Open for extension, closed for modification.**

```java
// VIOLATION — every new discount type = modify this class
public class DiscountCalculator {
    public double calculate(Order order, String type) {
        if (type.equals("SEASONAL")) return order.total() * 0.1;
        if (type.equals("LOYALTY")) return order.total() * 0.15;
        // touching existing, working code for every new type = bugs
        return 0;
    }
}

// FIX
public interface DiscountStrategy {
    double calculate(Order order);
    boolean isApplicable(Order order);
}

public class SeasonalDiscount implements DiscountStrategy {
    public double calculate(Order order) { return order.total() * 0.1; }
    public boolean isApplicable(Order order) { return isSeason(); }
}

public class DiscountCalculator {
    private final List<DiscountStrategy> strategies;
    
    public double calculate(Order order) {
        return strategies.stream()
            .filter(s -> s.isApplicable(order))
            .mapToDouble(s -> s.calculate(order))
            .sum();
    }
}
// Adding BlackFridayDiscount = new class only. Zero modification.
```

### OCP Traps

**Q: Can you ever modify a class?**
A: Yes — bug fixes and refactoring. OCP is about *features*. New features should extend, not modify.

**Interviewer trap**: "Everything can be extended. Where do you draw the line?"
**Answer**: You predict extension points from requirements. If spec says "support multiple payment methods," that's an extension point. Don't pre-abstract everything — abstract what you *know* will change.

---

## L — Liskov Substitution Principle

**Subtypes must be substitutable for their base types without breaking correctness.**

```java
// VIOLATION — Square extends Rectangle but breaks behavior
public class Rectangle {
    protected int width, height;
    public void setWidth(int w) { this.width = w; }
    public void setHeight(int h) { this.height = h; }
    public int area() { return width * height; }
}

public class Square extends Rectangle {
    @Override public void setWidth(int w) { this.width = this.height = w; }
    @Override public void setHeight(int h) { this.width = this.height = h; }
}

void resize(Rectangle r) {
    r.setWidth(5);
    r.setHeight(10);
    assert r.area() == 50; // FAILS for Square — area is 100!
}

// FIX — don't force an IS-A that isn't real
public interface Shape { int area(); }
public final class Rectangle implements Shape { ... } // immutable
public final class Square implements Shape { ... }    // immutable
```

### LSP Rules (Formal)
1. Preconditions cannot be strengthened in subtype
2. Postconditions cannot be weakened in subtype  
3. Invariants of supertype must be preserved
4. Subtype cannot throw new checked exceptions

### LSP Traps

**Q: Can you give a real-world LSP violation from Java itself?**
A: `java.sql.Timestamp extends java.util.Date` — Timestamp has nanosecond precision, Date doesn't. Mixing in comparisons produces wrong results. Classic real violation.

**Q: `Collections.unmodifiableList()` — LSP violation?**
A: Yes technically. It returns a `List` where `add()` throws `UnsupportedOperationException`. Code expecting `List.add()` to work will break.

**Q: How is LSP related to OCP?**
A: LSP enables OCP. If subtypes are truly substitutable, you can write code against abstractions without worrying which subtype is used.

---

## I — Interface Segregation Principle

**Clients should not be forced to depend on methods they don't use.**

```java
// VIOLATION — fat interface
public interface Worker {
    void work();
    void eat();
    void sleep();
}

public class Robot implements Worker {
    public void work() { }
    public void eat() { throw new UnsupportedOperationException(); } // WRONG
    public void sleep() { throw new UnsupportedOperationException(); }
}

// FIX
public interface Workable { void work(); }
public interface Eatable { void eat(); }
public interface Sleepable { void sleep(); }

public class Human implements Workable, Eatable, Sleepable { ... }
public class Robot implements Workable { ... } // Only what it needs
```

### Real ISP Example — Repository

```java
// BAD — one giant interface forces all clients to know about everything
public interface UserRepository {
    User findById(String id);
    void save(User user);
    void delete(String id);
    List<User> findPremiumUsers();
    int countByRegion(String region);
}

// GOOD — segregated by consumer
public interface UserReadRepository {
    Optional<User> findById(String id);
    Optional<User> findByEmail(String email);
}

public interface UserWriteRepository {
    void save(User user);
    void delete(String id);
}

public interface UserAnalyticsRepository {
    List<User> findPremiumUsers();
    int countByRegion(String region);
}
```

### ISP Traps

**Q: ISP vs SRP — what's the difference?**
A: SRP is about classes having one reason to change. ISP is about interfaces not forcing unnecessary dependencies. A class can violate ISP (fat interface) while being SRP-compliant.

**Q: Should every interface have just one method?**
A: No. Group methods by *cohesion of usage*. If consumers always use A, B, C together — they belong in one interface.

---

## D — Dependency Inversion Principle

**High-level modules should not depend on low-level modules. Both should depend on abstractions.**

```java
// VIOLATION — high-level OrderService depends on low-level MySQLRepository
public class OrderService {
    private MySQLOrderRepository repository = new MySQLOrderRepository(); // concrete!
    
    public void placeOrder(Order order) {
        repository.save(order); // tightly coupled to MySQL
    }
}

// FIX — depend on abstraction
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String id);
}

public class MySQLOrderRepository implements OrderRepository { ... }
public class InMemoryOrderRepository implements OrderRepository { ... } // for tests!
public class MongoOrderRepository implements OrderRepository { ... }

public class OrderService {
    private final OrderRepository repository; // depends on abstraction
    
    public OrderService(OrderRepository repository) { // injected
        this.repository = repository;
    }
    
    public void placeOrder(Order order) {
        repository.save(order); // works with any impl
    }
}
```

### DIP Traps

**Q: Is DIP the same as Dependency Injection?**
A: No. DIP is the principle (depend on abstractions). DI is a *technique* to achieve DIP (inject dependencies rather than constructing them). You can follow DIP without a DI framework.

**Q: Should EVERYTHING be injected?**
A: No. Value objects, utilities (`Math`, `Collections`), and stable low-level details don't need abstraction. Apply DIP where you need to swap implementations (DB, email, payment, etc.)

**Q: What's the difference between DIP and IoC?**
A: IoC (Inversion of Control) is the broader concept — control of flow given to a framework. DIP is specifically about dependency direction. Spring IoC container uses DIP to wire dependencies.

---

## SOLID Cheat Sheet

| Principle | One-liner | Violation smell | Fix |
|---|---|---|---|
| SRP | One reason to change | God class, "And" in class name | Split by responsibility |
| OCP | Extend, don't modify | Switch/if on type everywhere | Strategy/Polymorphism |
| LSP | Subtypes are substitutable | Subclass throws, or overrides break expectations | Redesign hierarchy |
| ISP | No forced dependencies | `UnsupportedOperationException` in interface impl | Segregate interfaces |
| DIP | Depend on abstractions | `new ConcreteClass()` inside high-level class | Inject via constructor |

---

## The Meta-Trap — Over-Applying SOLID

**Interviewer gotcha**: "You've added 15 interfaces for a simple feature. Is that good design?"

**Answer**: SOLID is a guideline, not a law. Over-engineering is as dangerous as under-engineering. Apply SOLID where:
- The codebase needs to evolve (OCP, SRP)
- Multiple implementations are expected (DIP, ISP)
- A hierarchy exists (LSP)

Don't apply it to throwaway scripts, prototypes, or code that will never change.
