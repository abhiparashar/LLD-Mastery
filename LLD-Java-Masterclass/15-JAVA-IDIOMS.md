# 15 — Java-Specific LLD Idioms (Junior → Senior → Staff)

> The difference between a candidate who "knows Java" and one who writes *production-grade* LLD code in Java. Every idiom here has been asked or rewarded in real FAANG interviews.

---

## 1. Enums With Behavior — Eliminate Switch-Case Forever

### Naive (rejected at FAANG)
```java
public double getFee(String vehicleType, int hours) {
    switch (vehicleType) {
        case "CAR": return 20.0 * hours;
        case "TRUCK": return 50.0 * hours;
        case "BIKE": return 10.0 * hours;
        default: throw new IllegalArgumentException();
    }
}
```

### FAANG-level: Enum carries its own behavior
```java
public enum VehicleType {
    MOTORCYCLE(1, 10.0, "BIKE"),
    CAR(4, 20.0, "CAR"),
    BUS(6, 50.0, "BUS"),
    ELECTRIC_CAR(4, 15.0, "EV") { // Enum with overridden method
        @Override
        public double calculateFee(int hours) {
            return super.calculateFee(hours) * 0.8; // 20% discount
        }
    };

    private final int wheelCount;
    private final double hourlyRate;
    private final String displayName;

    VehicleType(int wheelCount, double hourlyRate, String displayName) {
        this.wheelCount = wheelCount;
        this.hourlyRate = hourlyRate;
        this.displayName = displayName;
    }

    public double calculateFee(int hours) {
        return hourlyRate * hours;
    }

    public int getWheelCount() { return wheelCount; }
    public String getDisplayName() { return displayName; }

    // Reverse lookup by display name
    private static final Map<String, VehicleType> BY_NAME = Arrays.stream(values())
        .collect(Collectors.toMap(VehicleType::getDisplayName, v -> v));

    public static VehicleType fromDisplayName(String name) {
        return Optional.ofNullable(BY_NAME.get(name))
            .orElseThrow(() -> new IllegalArgumentException("Unknown vehicle: " + name));
    }
}

// Usage — zero switch-case anywhere
double fee = vehicle.getType().calculateFee(3); // polymorphic dispatch via enum
```

### Enum as State Machine
```java
public enum OrderStatus {
    PENDING {
        @Override public Set<OrderStatus> allowedTransitions() {
            return Set.of(CONFIRMED, CANCELLED);
        }
    },
    CONFIRMED {
        @Override public Set<OrderStatus> allowedTransitions() {
            return Set.of(SHIPPED, CANCELLED);
        }
    },
    SHIPPED {
        @Override public Set<OrderStatus> allowedTransitions() {
            return Set.of(DELIVERED);
        }
    },
    DELIVERED {
        @Override public Set<OrderStatus> allowedTransitions() {
            return Set.of(); // terminal
        }
    },
    CANCELLED {
        @Override public Set<OrderStatus> allowedTransitions() {
            return Set.of(); // terminal
        }
    };

    public abstract Set<OrderStatus> allowedTransitions();

    public OrderStatus transitionTo(OrderStatus next) {
        if (!allowedTransitions().contains(next)) {
            throw new IllegalStateException(
                "Cannot transition from " + this + " to " + next);
        }
        return next;
    }
}

// Usage
order.setStatus(order.getStatus().transitionTo(OrderStatus.SHIPPED));
// Throws if invalid transition — no if-else, no switch
```

---

## 2. Records — Value Objects Done Right (Java 16+)

```java
// Before records: 40 lines of boilerplate for a simple value object
// After records: clean, immutable, equals/hashCode/toString for free

public record Money(BigDecimal amount, Currency currency) {
    // Compact constructor for validation
    public Money {
        Objects.requireNonNull(amount, "Amount required");
        Objects.requireNonNull(currency, "Currency required");
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount cannot be negative");
        amount = amount.setScale(2, RoundingMode.HALF_UP); // normalize
    }

    // Custom methods on records
    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(double factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency))
            throw new CurrencyMismatchException(this.currency, other.currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money of(double amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    @Override public String toString() {
        return currency.getSymbol() + amount.toPlainString();
    }
}

// Other useful records in LLD
public record UserId(String value) {
    public UserId { Objects.requireNonNull(value); }
}

public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        if (end.isBefore(start)) throw new IllegalArgumentException("End before start");
    }
    public boolean overlaps(DateRange other) {
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start);
    }
    public long days() { return ChronoUnit.DAYS.between(start, end); }
}

public record Coordinates(double latitude, double longitude) {
    public double distanceTo(Coordinates other) {
        // Haversine formula
        double R = 6371;
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(this.latitude)) *
                   Math.cos(Math.toRadians(other.latitude)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
```

---

## 3. Sealed Classes — Type-Safe Alternatives to instanceof Chains (Java 17+)

```java
// Classic problem: payment result has multiple shapes
// Old way: instanceof chains + unchecked casts = fragile

// SEALED approach: compiler enforces exhaustiveness

public sealed interface PaymentResult
    permits PaymentResult.Success, PaymentResult.Failure, PaymentResult.Pending {

    record Success(String transactionId, Money amount, Instant processedAt)
        implements PaymentResult {}

    record Failure(String errorCode, String message, boolean retryable)
        implements PaymentResult {}

    record Pending(String referenceId, Instant estimatedCompletionTime)
        implements PaymentResult {}
}

// Exhaustive pattern matching — compiler catches missing cases
public String getDisplayMessage(PaymentResult result) {
    return switch (result) {
        case PaymentResult.Success s ->
            "Payment successful: " + s.transactionId() + " for " + s.amount();
        case PaymentResult.Failure f ->
            "Payment failed: " + f.message() + (f.retryable() ? " (retryable)" : "");
        case PaymentResult.Pending p ->
            "Payment pending, expected by: " + p.estimatedCompletionTime();
        // No default needed — compiler ensures exhaustiveness
        // If you add a new PaymentResult subtype, this switch won't compile → safe!
    };
}

// More sealed class examples for LLD
public sealed interface Shape permits Circle, Rectangle, Triangle {
    double area();
    double perimeter();
}

public record Circle(double radius) implements Shape {
    public double area() { return Math.PI * radius * radius; }
    public double perimeter() { return 2 * Math.PI * radius; }
}

public record Rectangle(double width, double height) implements Shape {
    public double area() { return width * height; }
    public double perimeter() { return 2 * (width + height); }
}

// Use case: notification channels — exhaustive handling guaranteed
public sealed interface NotificationChannel
    permits EmailChannel, SMSChannel, PushChannel, SlackChannel {}

public record EmailChannel(String address) implements NotificationChannel {}
public record SMSChannel(String phoneNumber) implements NotificationChannel {}
public record PushChannel(String deviceToken, String platform) implements NotificationChannel {}
public record SlackChannel(String webhookUrl, String channelName) implements NotificationChannel {}
```

---

## 4. Optional — Proper Usage Patterns

```java
// WRONG usages (seen constantly in interviews — instant red flag)
Optional<User> opt = Optional.of(null);           // NullPointerException
if (opt.isPresent()) return opt.get();             // equivalent to null check, defeats purpose
Optional.ofNullable(x).get();                      // defeats the whole point

// RIGHT: treat Optional as a mini-stream

// Pattern 1: map + orElseThrow (most common in services)
public String getUserEmail(String userId) {
    return userRepository.findById(userId)
        .map(User::getEmail)
        .orElseThrow(() -> new UserNotFoundException(userId));
}

// Pattern 2: filter + map + orElse
public String getDisplayName(String userId) {
    return userRepository.findById(userId)
        .filter(User::isActive)
        .map(User::getDisplayName)
        .orElse("Anonymous");
}

// Pattern 3: ifPresentOrElse (Java 9+)
userRepository.findById(userId)
    .ifPresentOrElse(
        user -> sendWelcomeEmail(user),
        () -> log.warn("User {} not found, skipping welcome email", userId)
    );

// Pattern 4: or() — fallback Optional (Java 9+)
public Optional<User> findUserByIdOrEmail(String id, String email) {
    return userRepository.findById(id)
        .or(() -> userRepository.findByEmail(email));
}

// Pattern 5: flatMap for nested Optionals
public Optional<Address> getUserPrimaryAddress(String userId) {
    return userRepository.findById(userId)
        .flatMap(user -> addressRepository.findPrimary(user.getId()));
    // NOT: .map(...).map(...) which gives Optional<Optional<Address>>
}

// Anti-pattern: Optional as field / parameter
// Optional should ONLY be used as return type
public class User {
    private Optional<String> middleName; // WRONG — use nullable field + Optional getter
    private String middleName;           // RIGHT
    public Optional<String> getMiddleName() { return Optional.ofNullable(middleName); }
}
```

---

## 5. Functional Interfaces — Replace Verbose Strategy Boilerplate

```java
// Before: full Strategy interface + implementation class
public interface SortStrategy { void sort(List<Integer> data); }
public class QuickSortStrategy implements SortStrategy { ... }

// After: just use existing functional interfaces for simple cases
@FunctionalInterface public interface Validator<T> { ValidationResult validate(T value); }
@FunctionalInterface public interface Transformer<I, O> { O transform(I input); }
@FunctionalInterface public interface Predicate<T> { boolean test(T value); }

// Real LLD use case: composable validators
public class OrderValidator {
    private final List<Validator<Order>> validators;

    public OrderValidator() {
        this.validators = List.of(
            order -> order.getItems().isEmpty()
                ? ValidationResult.failure("Order has no items") : ValidationResult.success(),
            order -> order.getTotalAmount().isGreaterThan(Money.zero(USD))
                ? ValidationResult.success() : ValidationResult.failure("Zero amount"),
            order -> order.getCustomerId() != null
                ? ValidationResult.success() : ValidationResult.failure("No customer")
        );
    }

    public List<ValidationResult> validate(Order order) {
        return validators.stream()
            .map(v -> v.validate(order))
            .filter(ValidationResult::isFailure)
            .collect(toList());
    }
}

// Composable predicates for filtering
Predicate<Product> inStock = p -> p.getStock() > 0;
Predicate<Product> inBudget = p -> p.getPrice().isLessThan(maxBudget);
Predicate<Product> inCategory = p -> p.getCategory().equals(targetCategory);

List<Product> results = products.stream()
    .filter(inStock.and(inBudget).and(inCategory))
    .sorted(Comparator.comparing(Product::getPrice))
    .collect(toList());
```

---

## 6. Builder Pattern — Step Builder for Forced Ordering

```java
// Regular builder: caller can forget required steps
// Step builder: compiler enforces correct sequence

// Use case: complex multi-step configuration
public class QueryBuilder {

    // Step interfaces — each returns the next step
    public interface SelectStep {
        FromStep select(String... columns);
    }

    public interface FromStep {
        WhereStep from(String table);
    }

    public interface WhereStep {
        OrderStep where(String condition);    // optional — has default
        OrderStep noWhere();
    }

    public interface OrderStep {
        BuildStep orderBy(String column);
        BuildStep noOrder();
    }

    public interface BuildStep {
        BuildStep limit(int n);
        String build();
    }

    // Implementation
    private static class Builder implements SelectStep, FromStep, WhereStep, OrderStep, BuildStep {
        private String[] columns;
        private String table;
        private String condition;
        private String orderColumn;
        private int limit = -1;

        public FromStep select(String... columns) { this.columns = columns; return this; }
        public WhereStep from(String table) { this.table = table; return this; }
        public OrderStep where(String condition) { this.condition = condition; return this; }
        public OrderStep noWhere() { return this; }
        public BuildStep orderBy(String col) { this.orderColumn = col; return this; }
        public BuildStep noOrder() { return this; }
        public BuildStep limit(int n) { this.limit = n; return this; }

        public String build() {
            StringBuilder sb = new StringBuilder("SELECT ")
                .append(String.join(", ", columns))
                .append(" FROM ").append(table);
            if (condition != null) sb.append(" WHERE ").append(condition);
            if (orderColumn != null) sb.append(" ORDER BY ").append(orderColumn);
            if (limit > 0) sb.append(" LIMIT ").append(limit);
            return sb.toString();
        }
    }

    public static SelectStep query() { return new Builder(); }
}

// Usage — impossible to skip steps, compile-time enforcement
String sql = QueryBuilder.query()
    .select("id", "name", "email")
    .from("users")
    .where("active = true")
    .orderBy("name")
    .limit(10)
    .build();
```

---

## 7. Java 21 Pattern Matching — Modern Switch

```java
// Java 21 pattern matching in switch — use this in interviews to signal currency

public void processEvent(Object event) {
    switch (event) {
        case OrderPlaced o when o.getAmount().isGreaterThan(Money.of(10000, USD)) ->
            highValueOrderHandler.handle(o);
        case OrderPlaced o ->
            standardOrderHandler.handle(o);
        case OrderCancelled c ->
            cancellationHandler.handle(c);
        case PaymentReceived p ->
            paymentHandler.handle(p);
        case null ->
            log.warn("Null event received");
        default ->
            log.debug("Unknown event type: {}", event.getClass().getSimpleName());
    }
}

// Deconstruction patterns (Java 21)
public String describeShape(Shape shape) {
    return switch (shape) {
        case Circle(double r) -> "Circle with radius " + r;
        case Rectangle(double w, double h) -> "Rectangle " + w + "x" + h;
        case Triangle t -> "Triangle with area " + t.area();
    };
}
```

---

## 8. Generic Design — Type-Safe Repositories and Services

```java
// Generic repository — write once, use everywhere
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    T save(T entity);
    void delete(ID id);
    boolean existsById(ID id);
}

// Specific repository inherits type safety
public interface UserRepository extends Repository<User, UserId> {
    Optional<User> findByEmail(String email);
    List<User> findByStatus(UserStatus status);
}

// Generic result wrapper — better than throwing exceptions everywhere
public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(String errorCode, String message) implements Result<T> {}

    static <T> Result<T> ok(T value) { return new Ok<>(value); }
    static <T> Result<T> err(String code, String msg) { return new Err<>(code, msg); }

    default boolean isOk() { return this instanceof Ok; }

    default <U> Result<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(mapper.apply(ok.value()));
            case Err<T> err -> Result.err(err.errorCode(), err.message());
        };
    }

    default T getOrElse(T defaultValue) {
        return switch (this) {
            case Ok<T> ok -> ok.value();
            case Err<T> ignored -> defaultValue;
        };
    }
}

// Usage in service
public Result<Order> placeOrder(PlaceOrderRequest request) {
    if (request.items().isEmpty())
        return Result.err("EMPTY_ORDER", "Order must have at least one item");

    return inventoryService.reserve(request.items())
        .map(reserved -> createOrder(request, reserved));
}
```

---

## 9. Streams for Clean LLD Logic

```java
// Anti-pattern: imperative code in service methods
public List<String> getActiveUserEmails(List<User> users) {
    List<String> emails = new ArrayList<>();
    for (User user : users) {
        if (user.isActive() && user.getEmail() != null) {
            emails.add(user.getEmail().toLowerCase());
        }
    }
    Collections.sort(emails);
    return emails;
}

// FAANG-level: stream pipeline — each stage has one clear responsibility
public List<String> getActiveUserEmails(List<User> users) {
    return users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .filter(Objects::nonNull)
        .map(String::toLowerCase)
        .sorted()
        .distinct()
        .collect(toList());
}

// Grouping — common in analytics, reporting
public Map<OrderStatus, List<Order>> groupOrdersByStatus(List<Order> orders) {
    return orders.stream().collect(groupingBy(Order::getStatus));
}

public Map<String, Long> countOrdersPerCustomer(List<Order> orders) {
    return orders.stream().collect(groupingBy(Order::getCustomerId, counting()));
}

public Map<String, Double> totalRevenuePerCategory(List<OrderItem> items) {
    return items.stream().collect(
        groupingBy(OrderItem::getCategory,
            summingDouble(item -> item.getPrice().amount().doubleValue())));
}

// Custom collector for domain-specific aggregation
public record OrderSummary(int count, Money totalAmount, Money averageAmount) {}

public OrderSummary summarizeOrders(List<Order> orders) {
    return orders.stream()
        .collect(Collector.of(
            () -> new double[]{0, 0},
            (acc, order) -> { acc[0]++; acc[1] += order.getTotal().amount().doubleValue(); },
            (a, b) -> new double[]{a[0]+b[0], a[1]+b[1]},
            acc -> new OrderSummary(
                (int) acc[0],
                Money.of(acc[1], USD),
                acc[0] > 0 ? Money.of(acc[1]/acc[0], USD) : Money.zero(USD))
        ));
}
```

---

## 10. CompletableFuture — Async LLD Design

```java
// Senior engineers mention async design proactively

public class OrderOrchestrationService {

    // Sequential async pipeline
    public CompletableFuture<OrderConfirmation> processOrder(Order order) {
        return CompletableFuture
            .supplyAsync(() -> validateOrder(order), validationExecutor)
            .thenComposeAsync(v -> inventoryService.reserve(order.getItems()), inventoryExecutor)
            .thenComposeAsync(reservation -> paymentService.charge(order.getPayment()), paymentExecutor)
            .thenApplyAsync(payment -> createConfirmation(order, payment), confirmationExecutor)
            .exceptionally(ex -> handleOrderFailure(order, ex));
    }

    // Parallel async calls — enriching response
    public CompletableFuture<ProductDetail> getProductDetail(String productId) {
        CompletableFuture<Product> productFuture =
            CompletableFuture.supplyAsync(() -> productRepo.findById(productId));
        CompletableFuture<List<Review>> reviewsFuture =
            CompletableFuture.supplyAsync(() -> reviewService.getReviews(productId));
        CompletableFuture<InventoryLevel> inventoryFuture =
            CompletableFuture.supplyAsync(() -> inventoryService.getLevel(productId));
        CompletableFuture<List<String>> imagesFuture =
            CompletableFuture.supplyAsync(() -> mediaService.getImages(productId));

        return CompletableFuture.allOf(productFuture, reviewsFuture, inventoryFuture, imagesFuture)
            .thenApply(v -> ProductDetail.builder()
                .product(productFuture.join())
                .reviews(reviewsFuture.join())
                .inventory(inventoryFuture.join())
                .images(imagesFuture.join())
                .build());
    }

    // anyOf — first successful response (for redundant services)
    public CompletableFuture<Price> getBestPrice(String productId) {
        return CompletableFuture.anyOf(
            CompletableFuture.supplyAsync(() -> priceServiceA.getPrice(productId)),
            CompletableFuture.supplyAsync(() -> priceServiceB.getPrice(productId))
        ).thenApply(price -> (Price) price);
    }
}
```

---

## Quick Reference: Java Idiom → When to Use

| Idiom | Use When |
|---|---|
| Enum with behavior | Multiple types need different calculations/logic |
| Enum as state machine | Object has constrained state transitions |
| Records | Value objects (Money, DateRange, Coordinates) |
| Sealed classes | Exhaustive type hierarchies (Result, PaymentResult) |
| Optional properly | Return type for may-or-may-not-exist queries |
| Step Builder | Multi-step construction with required ordering |
| Pattern matching switch | Handling sealed class variants |
| Generic Repository | Type-safe data access layer |
| Result<T> monad | Error handling without exceptions |
| CompletableFuture | Async orchestration, parallel enrichment |
