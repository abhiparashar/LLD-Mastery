# 03 — Design Patterns: Creational

---

## Factory Method

**Intent**: Define an interface for creating an object, but let subclasses decide which class to instantiate.

**When to use**: Object creation logic is complex or varies by context. Caller shouldn't know concrete types.

```java
public interface Notification {
    void send(String recipient, String message);
}

public class EmailNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("Email to " + recipient + ": " + message);
    }
}

public class SMSNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("SMS to " + recipient + ": " + message);
    }
}

public class PushNotification implements Notification {
    public void send(String recipient, String message) {
        System.out.println("Push to " + recipient + ": " + message);
    }
}

// Registry-based factory — extensible without modifying factory
public class NotificationFactory {
    private static final Map<String, Supplier<Notification>> registry = new HashMap<>();
    
    static {
        registry.put("EMAIL", EmailNotification::new);
        registry.put("SMS", SMSNotification::new);
        registry.put("PUSH", PushNotification::new);
    }
    
    public static void register(String type, Supplier<Notification> supplier) {
        registry.put(type.toUpperCase(), supplier); // open for extension
    }
    
    public static Notification create(String type) {
        Supplier<Notification> supplier = registry.get(type.toUpperCase());
        if (supplier == null) throw new IllegalArgumentException("Unknown type: " + type);
        return supplier.get();
    }
}
```

### Factory Traps

**Q: Factory Method vs Static Factory vs Abstract Factory?**

| | Factory Method | Static Factory | Abstract Factory |
|---|---|---|---|
| What | Subclass decides | Static method creates | Family of related objects |
| OOP | Inheritance | No subclassing | Composition |
| Example | `NotificationFactory.create()` | `Optional.of()`, `List.of()` | UI toolkit (Button+Dialog for Win vs Mac) |

**Q: Why use a registry-based factory over if-else?**
A: Registry is OCP-compliant. New types register themselves. If-else requires modifying the factory. In a plugin system, registries are essential.

**Q: When NOT to use Factory?**
A: When object creation is trivial (`new User(name)`). Don't abstract for the sake of it.

---

## Abstract Factory

**Intent**: Create families of related objects without specifying concrete classes.

```java
// Family 1: Light theme
// Family 2: Dark theme
// Both families have Button + Dialog

public interface Button { void render(); void onClick(Runnable action); }
public interface Dialog { void show(String title, String message); }

public interface UIFactory {
    Button createButton();
    Dialog createDialog();
}

public class LightThemeFactory implements UIFactory {
    public Button createButton() { return new LightButton(); }
    public Dialog createDialog() { return new LightDialog(); }
}

public class DarkThemeFactory implements UIFactory {
    public Button createButton() { return new DarkButton(); }
    public Dialog createDialog() { return new DarkDialog(); }
}

// App uses UIFactory — works with any theme
public class Application {
    private final UIFactory uiFactory;
    
    public Application(UIFactory uiFactory) {
        this.uiFactory = uiFactory;
    }
    
    public void buildUI() {
        Button btn = uiFactory.createButton();
        Dialog dlg = uiFactory.createDialog();
        btn.render();
        dlg.show("Welcome", "Hello");
    }
}
```

### Abstract Factory Traps

**Q: Adding a new component (e.g., TextField) to Abstract Factory?**
A: You must modify the `UIFactory` interface AND all implementors. This is the cost — Abstract Factory is closed to new products but open to new families.

**Q: Abstract Factory vs Builder?**
A: Abstract Factory creates families of related objects. Builder constructs a single complex object step by step.

---

## Builder

**Intent**: Construct complex objects step by step. Separate construction from representation.

**When to use**: Object has many optional parameters. Telescoping constructors are unreadable.

```java
// WITHOUT Builder — telescoping constructor hell
public User(String name, String email, String phone, String address, 
            String city, String country, boolean premium) { ... }

// WITH Builder
public class User {
    private final String name;        // required
    private final String email;       // required
    private final String phone;       // optional
    private final String address;     // optional
    private final boolean premium;    // optional, default false
    
    private User(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.phone = builder.phone;
        this.address = builder.address;
        this.premium = builder.premium;
    }
    
    public static class Builder {
        private final String name;
        private final String email;
        private String phone;
        private String address;
        private boolean premium = false;
        
        public Builder(String name, String email) {  // required fields in constructor
            this.name = Objects.requireNonNull(name, "Name required");
            this.email = Objects.requireNonNull(email, "Email required");
        }
        
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder premium(boolean premium) { this.premium = premium; return this; }
        
        public User build() {
            validate();
            return new User(this);
        }
        
        private void validate() {
            if (!email.contains("@")) throw new IllegalStateException("Invalid email");
        }
    }
}

// Usage — readable, order-independent
User user = new User.Builder("Alice", "alice@example.com")
    .phone("+91-9999999999")
    .premium(true)
    .build();
```

### Builder Traps

**Q: Why are required fields in the Builder constructor, not as methods?**
A: Enforces that required fields are always set. If they're methods, the caller can forget them and `build()` fails at runtime instead of compile time.

**Q: Builder vs Lombok `@Builder` — when to use each?**
A: Lombok is fine for data objects. Hand-write Builder when you need validation in `build()`, step-specific ordering, or a fluent "step builder" (where next method depends on current choice).

**Q: What's a Step Builder? When would you use it?**
A: Forces a specific build sequence at compile time.
```java
UserBuilder.name("Alice").email("alice@x.com").build(); // forced order
// .build() is only available after .email() is called
```
Use when order matters (wizard-style forms, multi-stage configuration).

---

## Singleton

**Intent**: Ensure a class has only one instance, and provide a global access point.

**When to use**: Configuration, logging, thread pools, connection pools.

```java
// WRONG — not thread-safe
public class Config {
    private static Config instance;
    public static Config getInstance() {
        if (instance == null) instance = new Config(); // race condition!
        return instance;
    }
}

// WRONG — synchronized but slow
public class Config {
    private static Config instance;
    public static synchronized Config getInstance() { // every call locks
        if (instance == null) instance = new Config();
        return instance;
    }
}

// RIGHT — Double-Checked Locking (DCL)
public class Config {
    private static volatile Config instance; // volatile is MANDATORY
    
    private Config() { loadFromFile(); }
    
    public static Config getInstance() {
        if (instance == null) {                    // first check (no lock)
            synchronized (Config.class) {
                if (instance == null) {            // second check (with lock)
                    instance = new Config();
                }
            }
        }
        return instance;
    }
}

// BEST — Enum Singleton (Bill Pugh / Joshua Bloch)
public enum Config {
    INSTANCE;
    
    private final Properties props = new Properties();
    
    Config() { loadFromFile(); }
    
    public String get(String key) { return props.getProperty(key); }
}
// Thread-safe by JVM, serialization-safe, reflection-safe, zero boilerplate
```

### Singleton Traps

**Q: Why is `volatile` mandatory in DCL?**
A: Without `volatile`, the JVM can reorder instructions. Another thread may see a partially constructed object (the reference is set before the constructor completes). `volatile` prevents reordering.

**Q: How does Enum Singleton handle serialization?**
A: Enum instances are inherently serialization-safe — JVM guarantees only one instance even after deserialization. Regular Singleton breaks serialization without implementing `readResolve()`.

**Q: How do you break a Singleton?**
A: Three ways: Reflection (`setAccessible(true)` on constructor), serialization/deserialization (creates new instance), cloning (`Cloneable`). Enum Singleton is immune to all three.

**Q: Is Singleton an anti-pattern?**
A: It can be. Global state makes testing hard (can't inject mocks). Use it for genuinely stateless or safely shared state (config, logging). Avoid for mutable shared state — use a proper dependency injection scope instead.

---

## Prototype

**Intent**: Create new objects by copying an existing object (clone).

**When to use**: Object creation is expensive. New objects are similar to existing ones.

```java
public abstract class Shape implements Cloneable {
    protected String color;
    public abstract double area();
    
    @Override
    public Shape clone() {
        try {
            return (Shape) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}

public class Circle extends Shape {
    private double radius;
    
    public Circle(double radius, String color) {
        this.radius = radius;
        this.color = color;
    }
    
    // Deep clone if needed
    @Override
    public Circle clone() {
        Circle clone = (Circle) super.clone();
        // If Circle had mutable fields (like List), deep copy them here
        return clone;
    }
    
    public double area() { return Math.PI * radius * radius; }
}

// Prototype Registry
public class ShapeRegistry {
    private final Map<String, Shape> prototypes = new HashMap<>();
    
    public void register(String key, Shape prototype) {
        prototypes.put(key, prototype);
    }
    
    public Shape get(String key) {
        Shape prototype = prototypes.get(key);
        if (prototype == null) throw new IllegalArgumentException("Unknown shape: " + key);
        return prototype.clone(); // return a copy, not the original
    }
}
```

### Prototype Traps

**Q: Shallow clone vs deep clone — when does it matter?**
A: Shallow clone copies primitive fields and references (both original and clone point to same objects). Deep clone copies the entire object graph. If your object contains mutable collections or nested objects, always deep clone.

**Q: Why not just use `new`?**
A: Clone skips expensive initialization. E.g., cloning a pre-loaded `DocumentTemplate` is faster than reading from DB again.

---

## Pattern Summary Table

| Pattern | Problem Solved | Key Signal in Interview |
|---|---|---|
| Factory Method | "Create objects without specifying concrete class" | Multiple types, one creation point |
| Abstract Factory | "Create families of related objects" | UI themes, cross-platform, DB providers |
| Builder | "Construct complex objects step by step" | Many optional params, complex validation |
| Singleton | "One instance globally" | Config, logging, thread pool |
| Prototype | "Copy expensive objects" | Template cloning, game object spawning |
