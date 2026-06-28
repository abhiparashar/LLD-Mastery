# 01 — OOP Foundations

---

## Why OOP Matters in LLD Rounds

Interviewers test OOP not to see if you know the vocabulary — they test whether you **make the right decisions**:
- When to use inheritance vs composition
- When to expose vs hide state
- How to model real-world entities cleanly

---

## 1. Encapsulation

**Definition**: Hide internal state. Expose only what the consumer needs.

```java
// BAD — exposes internals
public class BankAccount {
    public double balance;
    public List<Transaction> transactions;
}

// GOOD — controlled access
public class BankAccount {
    private double balance;
    private final List<Transaction> transactions = new ArrayList<>();

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        balance += amount;
        transactions.add(new Transaction(TransactionType.CREDIT, amount));
    }

    public void withdraw(double amount) {
        if (amount > balance) throw new InsufficientFundsException();
        balance -= amount;
        transactions.add(new Transaction(TransactionType.DEBIT, amount));
    }

    public double getBalance() { return balance; }
    public List<Transaction> getTransactions() { return Collections.unmodifiableList(transactions); }
}
```

### Interview Traps on Encapsulation

**Q: Why return `unmodifiableList` instead of a copy?**
A: Unmodifiable view is O(1), copy is O(n). For large lists the performance difference matters. Downside: if the underlying list changes, the view reflects it. Use copy when you need a true snapshot.

**Q: Is a getter always a violation of encapsulation?**
A: Yes if it returns mutable internal state. A getter returning a primitive or an immutable object is fine. Returning `List<T>` directly breaks encapsulation because callers can mutate it.

**Q: What's the difference between encapsulation and information hiding?**
A: Information hiding is the principle (what to hide). Encapsulation is the mechanism (how to hide it via access modifiers + bundling). Encapsulation is the Java implementation of information hiding.

---

## 2. Abstraction

**Definition**: Expose what an object does, not how it does it.

```java
// Abstraction via interface
public interface PaymentGateway {
    PaymentResult charge(CreditCard card, Money amount);
    RefundResult refund(String transactionId, Money amount);
}

// Multiple implementations hidden behind the interface
public class StripeGateway implements PaymentGateway { ... }
public class PayPalGateway implements PaymentGateway { ... }
public class RazorpayGateway implements PaymentGateway { ... }

// Consumer doesn't know or care which gateway
public class CheckoutService {
    private final PaymentGateway gateway;
    
    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
    
    public Order checkout(Cart cart, CreditCard card) {
        PaymentResult result = gateway.charge(card, cart.total());
        if (!result.isSuccess()) throw new PaymentFailedException(result.errorCode());
        return createOrder(cart, result.transactionId());
    }
}
```

### Interview Traps on Abstraction

**Q: When do you use abstract class vs interface?**

| Interface | Abstract Class |
|---|---|
| Pure contract, no state | Shared state + partial implementation |
| Multiple inheritance needed | Single inheritance is fine |
| Unrelated classes share behavior | Related classes share behavior |
| `Comparable`, `Serializable`, `PaymentGateway` | `AbstractVehicle`, `BaseRepository` |

**Real answer**: Default to interface. Use abstract class only when you have shared implementation that doesn't make sense to duplicate.

**Q: Can you over-abstract?**
A: Yes. YAGNI — You Ain't Gonna Need It. Don't create `AbstractSingletonProxyFactoryBean`. Abstract when you have 2+ concrete implementations or a clear extension point.

---

## 3. Inheritance

```java
// Inheritance done right — IS-A relationship
public abstract class Vehicle {
    protected final String id;
    protected final String registrationNumber;
    
    public Vehicle(String id, String registrationNumber) {
        this.id = id;
        this.registrationNumber = registrationNumber;
    }
    
    public abstract VehicleType getType();
    public abstract int getWheelCount();
    
    // Template method — defines algorithm, subclasses fill steps
    public final ParkingFee calculateFee(Duration duration) {
        double baseRate = getBaseRate();
        double hours = duration.toMinutes() / 60.0;
        return new ParkingFee(baseRate * hours * getMultiplier());
    }
    
    protected abstract double getBaseRate();
    protected double getMultiplier() { return 1.0; } // Optional override
}

public class Car extends Vehicle {
    public Car(String id, String reg) { super(id, reg); }
    public VehicleType getType() { return VehicleType.CAR; }
    public int getWheelCount() { return 4; }
    protected double getBaseRate() { return 20.0; }
}

public class Truck extends Vehicle {
    public Truck(String id, String reg) { super(id, reg); }
    public VehicleType getType() { return VehicleType.TRUCK; }
    public int getWheelCount() { return 6; }
    protected double getBaseRate() { return 50.0; }
    protected double getMultiplier() { return 1.5; } // Trucks cost more
}
```

### Composition vs Inheritance — The Most Common Trap

```java
// WRONG — inheritance for code reuse, not IS-A
public class Stack<T> extends ArrayList<T> {
    // Now Stack has add(), remove(), get() etc — exposes wrong API
}

// RIGHT — composition
public class Stack<T> {
    private final Deque<T> storage = new ArrayDeque<>();
    
    public void push(T item) { storage.push(item); }
    public T pop() { return storage.pop(); }
    public T peek() { return storage.peek(); }
    public boolean isEmpty() { return storage.isEmpty(); }
    // No ArrayList leakage!
}
```

**Rule**: Inherit when it IS-A relationship. Compose when it HAS-A relationship or you just want code reuse.

**Counter-question interviewers ask**: "Java's Stack extends Vector — is that good design?"
**Answer**: No. It's a historical mistake. Stack IS-NOT-A Vector. It leaks `elementAt()`, `insertElementAt()` etc. The correct design is composition. This is why `Deque` is preferred today.

---

## 4. Polymorphism

### Runtime Polymorphism (Override)

```java
public abstract class Notification {
    protected final String recipient;
    protected final String message;
    
    public abstract void send();
    
    // Polymorphic dispatch
    public static void sendAll(List<Notification> notifications) {
        notifications.forEach(Notification::send); // each calls its own send()
    }
}

public class EmailNotification extends Notification {
    public void send() { emailClient.send(recipient, message); }
}

public class SMSNotification extends Notification {
    public void send() { smsClient.send(recipient, message); }
}

public class PushNotification extends Notification {
    public void send() { fcmClient.push(recipient, message); }
}
```

### Polymorphism via Interface (better than inheritance)

```java
@FunctionalInterface
public interface Discountable {
    double applyDiscount(double originalPrice);
}

// Multiple unrelated types can be Discountable
public class SeasonalDiscount implements Discountable { ... }
public class LoyaltyDiscount implements Discountable { ... }
public class CouponDiscount implements Discountable { ... }

// Chain discounts
public double finalPrice(double price, List<Discountable> discounts) {
    double result = price;
    for (Discountable d : discounts) result = d.applyDiscount(result);
    return result;
}
```

### Interview Traps on Polymorphism

**Q: What's covariant return type?**
A: Overriding method can return a subtype of the parent's return type.
```java
class Animal { Animal create() { return new Animal(); } }
class Dog extends Animal { Dog create() { return new Dog(); } } // Covariant — valid
```

**Q: Can constructors be polymorphic?**
A: No. Constructors aren't inherited and can't be overridden. This is why the Factory pattern exists — to give polymorphic object creation.

**Q: What happens when you call an overridable method from a constructor?**
A: Dangerous. The subclass's version is called before the subclass is fully initialized.
```java
class Parent {
    Parent() { init(); } // Calls Child.init() before Child is ready!
    void init() {}
}
class Child extends Parent {
    private String name = "child";
    void init() { System.out.println(name.toUpperCase()); } // NullPointerException!
}
```

---

## 5. Entity vs Value Object

This distinction is critical in LLD modeling.

| Entity | Value Object |
|---|---|
| Has unique identity (ID) | Defined by its values |
| Mutable over time | Immutable |
| `User`, `Order`, `Vehicle` | `Money`, `Address`, `DateRange` |
| `equals()` by ID | `equals()` by all fields |

```java
// Value Object — immutable, equals by value
public final class Money {
    private final BigDecimal amount;
    private final Currency currency;
    
    public Money(BigDecimal amount, Currency currency) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Negative money");
        this.amount = amount;
        this.currency = currency;
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new CurrencyMismatchException();
        return new Money(this.amount.add(other.amount), this.currency);
    }
    
    @Override public boolean equals(Object o) {
        if (!(o instanceof Money)) return false;
        Money m = (Money) o;
        return amount.equals(m.amount) && currency.equals(m.currency);
    }
    
    @Override public int hashCode() { return Objects.hash(amount, currency); }
}
```

**Interview trap**: "Should `Address` be an entity or value object?"
**Answer**: Depends on context. In e-commerce, `Address` is a value object — two orders with the same address are the same address. In a government system tracking property records, `Address` might be an entity with its own lifecycle.

---

## Summary — OOP Decision Tree

```
Need to model something?
├── Does it have a unique identity that persists? → Entity
└── Defined purely by its values? → Value Object

Sharing behavior between classes?
├── IS-A relationship + shared implementation? → Abstract class
├── Contract only, multiple implementations? → Interface
└── HAS-A or just reuse? → Composition

Hiding complexity?
├── What it does? → Abstraction (interface)
└── How it stores it? → Encapsulation (private fields)
```
