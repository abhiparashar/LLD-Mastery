# 19 — More Solved Problems: Uber, Rule Engine, Unix Find, Vending Machine

---

## Uber/Ola Ride Sharing

**Asked at**: Uber, Ola, Lyft, Amazon, Flipkart

### Requirements to Clarify
- Rider requests ride with source and destination
- Match nearest available driver
- Real-time location tracking
- Fare calculation (distance + time + surge)
- Rating system
- Trip states: requested → accepted → started → completed/cancelled

### Core Entities
```java
enum DriverStatus { AVAILABLE, ON_TRIP, OFFLINE }
enum TripStatus { REQUESTED, ACCEPTED, DRIVER_ARRIVING, STARTED, COMPLETED, CANCELLED }
enum VehicleCategory { BIKE, AUTO, MINI, SEDAN, SUV, PREMIER }

record Location(double lat, double lon) {
    double distanceTo(Location other) { /* Haversine */ }
}

class Driver {
    String id; String name;
    DriverStatus status;
    Location currentLocation;
    Vehicle vehicle;
    double rating;
    int totalTrips;
}

class Rider {
    String id; String name;
    String phone;
    double rating;
}

class Trip {
    String id;
    Rider rider;
    Driver driver;
    Location source;
    Location destination;
    TripStatus status;
    Instant requestedAt;
    Instant startedAt;
    Instant completedAt;
    Money fare;
    int riderRating;    // driver rates rider
    int driverRating;   // rider rates driver
}

class Vehicle {
    String registrationNumber;
    VehicleCategory category;
    String model;
    String color;
}
```

### Core Design

```java
// Strategy: Driver Matching
public interface DriverMatchingStrategy {
    Optional<Driver> findBestDriver(Location riderLocation, VehicleCategory category,
                                    List<Driver> availableDrivers);
}

public class NearestDriverStrategy implements DriverMatchingStrategy {
    public Optional<Driver> findBestDriver(Location riderLoc, VehicleCategory category,
                                            List<Driver> availableDrivers) {
        return availableDrivers.stream()
            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
            .filter(d -> d.getVehicle().getCategory() == category)
            .filter(d -> d.getCurrentLocation().distanceTo(riderLoc) <= MAX_SEARCH_RADIUS_KM)
            .min(Comparator.comparingDouble(d -> d.getCurrentLocation().distanceTo(riderLoc)));
    }
}

public class RatingWeightedStrategy implements DriverMatchingStrategy {
    // Weight = rating * 0.4 + (1/distance) * 0.6
    public Optional<Driver> findBestDriver(Location riderLoc, VehicleCategory category,
                                            List<Driver> drivers) {
        return drivers.stream()
            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
            .filter(d -> d.getVehicle().getCategory() == category)
            .max(Comparator.comparingDouble(d -> score(d, riderLoc)));
    }

    private double score(Driver d, Location riderLoc) {
        double distance = d.getCurrentLocation().distanceTo(riderLoc);
        return d.getRating() * 0.4 + (1.0 / (distance + 0.1)) * 0.6;
    }
}

// Strategy: Pricing
public interface PricingStrategy {
    Money calculateFare(double distanceKm, Duration duration, VehicleCategory category);
}

public class StandardPricingStrategy implements PricingStrategy {
    private static final Map<VehicleCategory, Double> BASE_RATES = Map.of(
        VehicleCategory.MINI, 8.0,
        VehicleCategory.SEDAN, 12.0,
        VehicleCategory.SUV, 18.0,
        VehicleCategory.PREMIER, 25.0
    );

    public Money calculateFare(double distanceKm, Duration duration, VehicleCategory category) {
        double base = BASE_RATES.getOrDefault(category, 10.0);
        double distanceCharge = distanceKm * base;
        double timeCharge = duration.toMinutes() * 0.5;
        double total = Math.max(distanceCharge + timeCharge, getMinimumFare(category));
        return Money.of(total, Currency.INR);
    }
}

// Decorator: Surge Pricing
public class SurgePricingDecorator implements PricingStrategy {
    private final PricingStrategy base;
    private final SurgeCalculator surgeCalculator;

    public Money calculateFare(double distanceKm, Duration duration, VehicleCategory category) {
        Money baseFare = base.calculateFare(distanceKm, duration, category);
        double surgeMultiplier = surgeCalculator.getSurgeMultiplier(category);
        return baseFare.multiply(surgeMultiplier);
    }
}

// Core Trip Service
public class TripService {
    private final DriverMatchingStrategy matchingStrategy;
    private final PricingStrategy pricingStrategy;
    private final LocationTracker locationTracker;
    private final NotificationService notificationService;

    public Trip requestTrip(Rider rider, Location source, Location destination,
                             VehicleCategory category) {
        List<Driver> nearbyDrivers = locationTracker.getDriversNear(source, 5.0);

        Driver driver = matchingStrategy.findBestDriver(source, category, nearbyDrivers)
            .orElseThrow(() -> new NoDriverAvailableException(source, category));

        Trip trip = new Trip(generateId(), rider, driver, source, destination, TripStatus.REQUESTED);

        driver.setStatus(DriverStatus.ON_TRIP);
        notificationService.notifyDriver(driver, trip);
        notificationService.notifyRider(rider, driver, trip);

        return tripRepository.save(trip);
    }

    public Trip completeTrip(String tripId) {
        Trip trip = tripRepository.findById(tripId);
        double distanceKm = trip.getSource().distanceTo(trip.getDestination());
        Duration duration = Duration.between(trip.getStartedAt(), Instant.now());

        Money fare = pricingStrategy.calculateFare(distanceKm, duration,
                        trip.getDriver().getVehicle().getCategory());

        trip.setFare(fare);
        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        trip.getDriver().setStatus(DriverStatus.AVAILABLE);

        return tripRepository.save(trip);
    }
}
```

### Traps & Follow-ups
- **"Two riders match same driver simultaneously?"** → `driver.setStatus(AVAILABLE → ON_TRIP)` in synchronized block or optimistic lock. Second rider gets no-driver-available
- **"Driver cancels trip?"** → Re-match rider with next available driver. Increment driver's cancellation count
- **"Surge pricing calculation?"** → Demand/supply ratio in geo-cell. `surgeMultiplier = Math.min(demand/supply, maxSurge)`
- **"Driver location updates?"** → Location events every 5 seconds via WebSocket → stored in Redis geospatial index (GEOADD/GEORADIUS)
- **"ETA calculation?"** → Google Maps API in production. In LLD: `eta = distanceToRider / avgSpeedKmH`

---

## Rule Engine

**Asked at**: Amazon, Razorpay, Paytm, Flipkart — increasingly common 2024–2026

### Requirements to Clarify
- Define rules that evaluate a context and produce an action
- Rules have conditions (AND/OR/NOT combinations) and actions
- Rules have priority
- Rules can be added/modified at runtime without code change
- Evaluate all rules or stop at first match

### Design: Composite Pattern for Rules

```java
// The core: Rule as Composite
public interface Rule {
    boolean evaluate(RuleContext context);
    String getName();
}

// Leaf: Atomic condition
public class AtomicRule implements Rule {
    private final String field;
    private final Operator operator;
    private final Object value;

    public enum Operator { EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, IN, NOT_IN }

    public boolean evaluate(RuleContext context) {
        Object fieldValue = context.get(field);
        return switch (operator) {
            case EQUALS -> Objects.equals(fieldValue, value);
            case NOT_EQUALS -> !Objects.equals(fieldValue, value);
            case GREATER_THAN -> compare(fieldValue, value) > 0;
            case LESS_THAN -> compare(fieldValue, value) < 0;
            case CONTAINS -> fieldValue != null && fieldValue.toString().contains(value.toString());
            case IN -> ((Collection<?>) value).contains(fieldValue);
            case NOT_IN -> !((Collection<?>) value).contains(fieldValue);
        };
    }
}

// Composite: AND condition
public class AndRule implements Rule {
    private final List<Rule> rules;
    private final String name;

    public boolean evaluate(RuleContext context) {
        return rules.stream().allMatch(r -> r.evaluate(context)); // short-circuit AND
    }
}

// Composite: OR condition
public class OrRule implements Rule {
    private final List<Rule> rules;

    public boolean evaluate(RuleContext context) {
        return rules.stream().anyMatch(r -> r.evaluate(context)); // short-circuit OR
    }
}

// Composite: NOT condition
public class NotRule implements Rule {
    private final Rule inner;

    public boolean evaluate(RuleContext context) {
        return !inner.evaluate(context);
    }
}

// Action: what happens when rule fires
public interface Action {
    void execute(RuleContext context);
}

public class BusinessRule {
    private final String id;
    private final Rule condition;
    private final Action action;
    private final int priority;
    private final boolean stopOnMatch;
}

// Engine: evaluates rules in priority order
public class RuleEngine {
    private final List<BusinessRule> rules; // sorted by priority desc

    public RuleEngine(List<BusinessRule> rules) {
        this.rules = rules.stream()
            .sorted(Comparator.comparingInt(BusinessRule::getPriority).reversed())
            .collect(toList());
    }

    public List<Action> evaluate(RuleContext context) {
        List<Action> firedActions = new ArrayList<>();
        for (BusinessRule rule : rules) {
            if (rule.getCondition().evaluate(context)) {
                firedActions.add(rule.getAction());
                rule.getAction().execute(context); // side effect
                if (rule.isStopOnMatch()) break;
            }
        }
        return firedActions;
    }
}

// Context: carries all data for evaluation
public class RuleContext {
    private final Map<String, Object> attributes = new HashMap<>();

    public void set(String key, Object value) { attributes.put(key, value); }
    public Object get(String key) { return attributes.get(key); }
    public <T> T get(String key, Class<T> type) { return type.cast(attributes.get(key)); }
}

// Example usage: fraud detection rules
RuleEngine fraudEngine = new RuleEngine(List.of(
    new BusinessRule("HIGH_AMOUNT_NEW_USER",
        new AndRule(List.of(
            new AtomicRule("transactionAmount", GREATER_THAN, 50000),
            new AtomicRule("userAccountAgeDays", LESS_THAN, 30)
        )),
        new FlagForReviewAction(),
        priority: 100, stopOnMatch: false),

    new BusinessRule("INTERNATIONAL_AFTER_MIDNIGHT",
        new AndRule(List.of(
            new AtomicRule("isInternational", EQUALS, true),
            new AtomicRule("hourOfDay", GREATER_THAN, 23)
        )),
        new BlockTransactionAction(),
        priority: 90, stopOnMatch: true)
));

RuleContext ctx = new RuleContext();
ctx.set("transactionAmount", 75000.0);
ctx.set("userAccountAgeDays", 10);
ctx.set("isInternational", false);
ctx.set("hourOfDay", 14);

fraudEngine.evaluate(ctx); // → fires HIGH_AMOUNT_NEW_USER
```

### Traps & Follow-ups
- **"Runtime rule changes?"** → Rules stored in DB as JSON. `RuleLoader` deserializes and rebuilds. `RuleEngine` reloaded on change event
- **"Rule conflicts?"** → Priority resolves conflicts. Explicit "stop-on-match" flag for exclusive rules
- **"Performance with 1000 rules?"** → Index rules by top-level field. Only evaluate rules where first condition's field matches the context. Rete algorithm for complex rule sets
- **"Rule testing?"** → Each rule is independently testable. RuleContext is just a map — easy to stub

---

## Unix `find` Command

**Asked at**: Amazon, Google — high frequency recently

### Requirements
- `find /path -name "*.java" -type f -size +1MB -mtime -7`
- Traverse directory tree recursively
- Apply multiple filter criteria (name pattern, type, size, modified time)
- Extensible: add new filter criteria without modifying traversal

### Design: Composite + Strategy

```java
// Strategy: Filters
public interface FileFilter {
    boolean matches(FileInfo file);
}

public class NameFilter implements FileFilter {
    private final Pattern pattern;

    public NameFilter(String glob) {
        // Convert glob to regex: *.java → .*\.java
        this.pattern = Pattern.compile(globToRegex(glob));
    }

    public boolean matches(FileInfo file) {
        return pattern.matcher(file.getName()).matches();
    }
}

public class TypeFilter implements FileFilter {
    private final FileType type; // FILE or DIRECTORY

    public boolean matches(FileInfo file) {
        return type == FileType.FILE ? !file.isDirectory() : file.isDirectory();
    }
}

public class SizeFilter implements FileFilter {
    private final long sizeBytes;
    private final Comparator comparator; // GT, LT, EQ

    public boolean matches(FileInfo file) {
        return switch (comparator) {
            case GT -> file.getSizeBytes() > sizeBytes;
            case LT -> file.getSizeBytes() < sizeBytes;
            case EQ -> file.getSizeBytes() == sizeBytes;
        };
    }
}

public class ModifiedTimeFilter implements FileFilter {
    private final int days; // negative = within last N days

    public boolean matches(FileInfo file) {
        long daysAgo = ChronoUnit.DAYS.between(file.getLastModified(), LocalDate.now());
        return days < 0 ? daysAgo <= Math.abs(days) : daysAgo >= days;
    }
}

// Composite: combine filters with AND (default in find)
public class CompositeFilter implements FileFilter {
    private final List<FileFilter> filters;

    public CompositeFilter(List<FileFilter> filters) { this.filters = filters; }

    public boolean matches(FileInfo file) {
        return filters.stream().allMatch(f -> f.matches(file));
    }
}

// Composite: OR filter (for -o flag in find)
public class OrFilter implements FileFilter {
    private final List<FileFilter> filters;

    public boolean matches(FileInfo file) {
        return filters.stream().anyMatch(f -> f.matches(file));
    }
}

// Traversal with filter
public class FindCommand {
    private final FileSystem fileSystem;

    public List<FileInfo> find(String rootPath, FileFilter filter) {
        List<FileInfo> results = new ArrayList<>();
        traverse(fileSystem.getRoot(rootPath), filter, results);
        return results;
    }

    private void traverse(FileInfo current, FileFilter filter, List<FileInfo> results) {
        if (filter.matches(current)) results.add(current);

        if (current.isDirectory()) {
            for (FileInfo child : fileSystem.getChildren(current)) {
                traverse(child, filter, results); // recursive DFS
            }
        }
    }
}

// Usage
FindCommand find = new FindCommand(fileSystem);

FileFilter filter = new CompositeFilter(List.of(
    new NameFilter("*.java"),
    new TypeFilter(FileType.FILE),
    new SizeFilter(1_048_576, GT), // > 1MB
    new ModifiedTimeFilter(-7)      // modified within 7 days
));

List<FileInfo> results = find.find("/home/user", filter);
```

### Traps & Follow-ups
- **"Adding new filter type?"** → New class implementing `FileFilter`. Zero modification to `FindCommand` or existing filters. OCP compliant
- **"Symbolic links?"** → `FileInfo.isSymlink()` flag. Add `-follow` option to traverse or skip
- **"Max depth limit?"** → Pass `depth` to `traverse()`. Stop recursion at `maxDepth`
- **"Parallel traversal?"** → Use `ForkJoinPool` — each directory subtree is a separate task. `RecursiveTask<List<FileInfo>>`

---

## Vending Machine (Full Design)

**Asked at**: Amazon, Microsoft, Goldman Sachs

### Requirements
- Select item, insert money, dispense item, return change
- Multiple items, multiple quantities
- Multiple coin/note denominations
- State-based: idle → item selected → money inserted → dispensing
- Maintenance mode for refilling

### State Machine Design

```java
public interface VendingMachineState {
    void selectItem(VendingMachine machine, String itemCode);
    void insertMoney(VendingMachine machine, Money amount);
    void dispense(VendingMachine machine);
    void cancel(VendingMachine machine);
    String getStateName();
}

public class IdleState implements VendingMachineState {
    public void selectItem(VendingMachine machine, String itemCode) {
        Item item = machine.getItem(itemCode)
            .orElseThrow(() -> new ItemNotFoundException(itemCode));

        if (item.getQuantity() == 0) throw new OutOfStockException(itemCode);

        machine.setSelectedItem(item);
        machine.setState(new ItemSelectedState());
        System.out.println("Item selected: " + item.getName() + " — Price: " + item.getPrice());
    }

    public void insertMoney(VendingMachine machine, Money amount) {
        throw new InvalidOperationException("Please select an item first");
    }

    public void dispense(VendingMachine machine) {
        throw new InvalidOperationException("No item selected");
    }

    public void cancel(VendingMachine machine) {
        System.out.println("Nothing to cancel");
    }

    public String getStateName() { return "IDLE"; }
}

public class ItemSelectedState implements VendingMachineState {
    public void selectItem(VendingMachine machine, String itemCode) {
        throw new InvalidOperationException("Item already selected. Cancel first.");
    }

    public void insertMoney(VendingMachine machine, Money amount) {
        machine.addInsertedMoney(amount);
        Money inserted = machine.getInsertedMoney();
        Money price = machine.getSelectedItem().getPrice();

        System.out.println("Inserted: " + inserted + " | Required: " + price);

        if (inserted.isGreaterThanOrEqual(price)) {
            machine.setState(new HasSufficientMoneyState());
        }
    }

    public void dispense(VendingMachine machine) {
        throw new InvalidOperationException("Please insert sufficient money");
    }

    public void cancel(VendingMachine machine) {
        Money refund = machine.getInsertedMoney();
        machine.resetInsertedMoney();
        machine.setSelectedItem(null);
        machine.setState(new IdleState());
        System.out.println("Cancelled. Refund: " + refund);
    }

    public String getStateName() { return "ITEM_SELECTED"; }
}

public class HasSufficientMoneyState implements VendingMachineState {
    public void selectItem(VendingMachine machine, String itemCode) {
        throw new InvalidOperationException("Already has item selected and paid");
    }

    public void insertMoney(VendingMachine machine, Money amount) {
        machine.addInsertedMoney(amount); // accept more — larger change back
    }

    public void dispense(VendingMachine machine) {
        Item item = machine.getSelectedItem();
        Money inserted = machine.getInsertedMoney();
        Money price = item.getPrice();
        Money change = inserted.subtract(price);

        item.decrementQuantity();
        machine.resetInsertedMoney();
        machine.setSelectedItem(null);

        System.out.println("Dispensing: " + item.getName());
        if (change.isPositive()) {
            System.out.println("Change: " + change);
            machine.returnChange(change);
        }

        machine.setState(new IdleState());
    }

    public void cancel(VendingMachine machine) {
        Money refund = machine.getInsertedMoney();
        machine.resetInsertedMoney();
        machine.setSelectedItem(null);
        machine.setState(new IdleState());
        System.out.println("Cancelled. Refund: " + refund);
    }

    public String getStateName() { return "SUFFICIENT_MONEY"; }
}

// Machine context
public class VendingMachine {
    private VendingMachineState state = new IdleState();
    private final Map<String, Item> inventory = new ConcurrentHashMap<>();
    private Item selectedItem;
    private Money insertedMoney = Money.zero(Currency.INR);
    private final ChangeDispenser changeDispenser;

    public void selectItem(String itemCode) { state.selectItem(this, itemCode); }
    public void insertMoney(Money amount) { state.insertMoney(this, amount); }
    public void dispense() { state.dispense(this); }
    public void cancel() { state.cancel(this); }

    public void returnChange(Money amount) { changeDispenser.dispense(amount); }

    // Maintenance operations
    public void refill(String itemCode, int quantity) {
        inventory.computeIfPresent(itemCode, (k, item) -> {
            item.addQuantity(quantity);
            return item;
        });
    }

    public void addItem(Item item) { inventory.put(item.getCode(), item); }
}
```

### Traps & Follow-ups
- **"Change dispenser algorithm?"** → Greedy change: largest denomination first. `while (amount > 0): find largest coin ≤ amount, dispense, subtract`
- **"What if machine can't make exact change?"** → Before dispensing: check if change is possible. If not, reject transaction, refund
- **"Multiple simultaneous users?"** → Physical vending machines are single-user (mechanical). If software simulation: synchronized state methods
- **"Adding new payment method (UPI)?"** → `PaymentStrategy` interface. `CashPayment`, `UPIPayment`. State machine stays the same, payment strategy injected
