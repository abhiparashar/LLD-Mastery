# 16 — Pattern Combinations (How Real Systems Are Built)

> Junior engineers know individual patterns. Senior engineers know how patterns *compose*. Staff engineers know which combination to reach for without thinking. This file bridges that gap.

---

## Combination 1: Strategy + Factory + Registry

**Used in**: Plugin systems, payment gateways, notification channels, report generators

**Problem**: You need to select and instantiate the right algorithm at runtime, and new algorithms should be addable without modifying existing code.

```java
// Step 1: Strategy interface
public interface ReportGenerator {
    byte[] generate(ReportData data);
    String getSupportedFormat();
}

// Step 2: Concrete strategies
public class PDFReportGenerator implements ReportGenerator {
    public byte[] generate(ReportData data) { /* PDF generation */ return new byte[0]; }
    public String getSupportedFormat() { return "PDF"; }
}

public class ExcelReportGenerator implements ReportGenerator {
    public byte[] generate(ReportData data) { /* Excel generation */ return new byte[0]; }
    public String getSupportedFormat() { return "XLSX"; }
}

public class CSVReportGenerator implements ReportGenerator {
    public byte[] generate(ReportData data) { /* CSV generation */ return new byte[0]; }
    public String getSupportedFormat() { return "CSV"; }
}

// Step 3: Registry-based Factory — OCP compliant
@Component  // or inject via DI
public class ReportGeneratorFactory {
    private final Map<String, ReportGenerator> registry = new ConcurrentHashMap<>();

    // Self-registration: each generator registers itself on startup
    public void register(ReportGenerator generator) {
        registry.put(generator.getSupportedFormat().toUpperCase(), generator);
    }

    public ReportGenerator getGenerator(String format) {
        return Optional.ofNullable(registry.get(format.toUpperCase()))
            .orElseThrow(() -> new UnsupportedFormatException(format));
    }

    public Set<String> getSupportedFormats() { return Collections.unmodifiableSet(registry.keySet()); }
}

// Step 4: Service uses factory — knows nothing about concrete generators
public class ReportService {
    private final ReportGeneratorFactory factory;

    public byte[] generateReport(ReportRequest request) {
        ReportGenerator generator = factory.getGenerator(request.getFormat());
        ReportData data = dataCollector.collect(request);
        return generator.generate(data);
    }
}

// Adding a new format: create new class, register it. Zero modification to existing code.
```

**Interview signal**: "To add a new report format, we create a new `ReportGenerator` implementation and register it. The factory, service, and all existing generators are untouched."

---

## Combination 2: State + Observer

**Used in**: Order lifecycle, booking systems, workflow engines

**Problem**: As an entity transitions through states, different parts of the system need to react.

```java
// State machine transitions trigger events that observers handle
public interface OrderState {
    OrderState confirm(Order order);
    OrderState ship(Order order);
    OrderState deliver(Order order);
    OrderState cancel(Order order);
}

public class Order {
    private OrderState state = new PendingState();
    private final List<OrderEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(OrderEventListener listener) { listeners.add(listener); }

    public void confirm() {
        OrderState previousState = state;
        state = state.confirm(this);
        fireEvent(new OrderStateChangedEvent(this, previousState, state));
    }

    public void ship() {
        OrderState previousState = state;
        state = state.ship(this);
        fireEvent(new OrderStateChangedEvent(this, previousState, state));
    }

    private void fireEvent(OrderEvent event) {
        listeners.forEach(l -> l.onEvent(event));
    }
}

// Observers react to state changes
public class EmailNotificationListener implements OrderEventListener {
    public void onEvent(OrderEvent event) {
        if (event instanceof OrderStateChangedEvent e) {
            switch (e.newState()) {
                case ConfirmedState ignored -> emailService.sendOrderConfirmation(e.order());
                case ShippedState ignored -> emailService.sendShippingNotification(e.order());
                case DeliveredState ignored -> emailService.sendDeliveryConfirmation(e.order());
                case CancelledState ignored -> emailService.sendCancellationNotification(e.order());
            }
        }
    }
}

public class InventoryListener implements OrderEventListener {
    public void onEvent(OrderEvent event) {
        if (event instanceof OrderStateChangedEvent e) {
            if (e.newState() instanceof CancelledState) {
                inventoryService.releaseReservation(e.order().getId());
            }
        }
    }
}

public class LoyaltyPointsListener implements OrderEventListener {
    public void onEvent(OrderEvent event) {
        if (event instanceof OrderStateChangedEvent e) {
            if (e.newState() instanceof DeliveredState) {
                loyaltyService.awardPoints(e.order().getCustomerId(), e.order().getTotal());
            }
        }
    }
}

// Wiring
order.addListener(new EmailNotificationListener(emailService));
order.addListener(new InventoryListener(inventoryService));
order.addListener(new LoyaltyPointsListener(loyaltyService));
order.confirm(); // triggers: PendingState → ConfirmedState + fires event → email + ... 
```

**Why this matters**: State manages *what's allowed*. Observer manages *what happens as a result*. Separating them means you can add new reactions (new Observer) without touching the state machine.

---

## Combination 3: Builder + Strategy + Validator

**Used in**: Query builders, search systems, rule engines

```java
// Building complex objects with pluggable validation strategies
public interface ValidationRule<T> {
    ValidationResult validate(T value);
    String getRuleName();
}

public class NotNullRule<T> implements ValidationRule<T> {
    public ValidationResult validate(T value) {
        return value != null ? ValidationResult.ok() : ValidationResult.fail("Value is null");
    }
    public String getRuleName() { return "NOT_NULL"; }
}

public class MinLengthRule implements ValidationRule<String> {
    private final int minLength;
    public MinLengthRule(int minLength) { this.minLength = minLength; }
    public ValidationResult validate(String value) {
        return value != null && value.length() >= minLength
            ? ValidationResult.ok()
            : ValidationResult.fail("Minimum length is " + minLength);
    }
    public String getRuleName() { return "MIN_LENGTH_" + minLength; }
}

// Builder with injected validators
public class UserBuilder {
    private String name;
    private String email;
    private String password;
    private final List<ValidationRule<String>> emailRules;
    private final List<ValidationRule<String>> passwordRules;

    public UserBuilder(List<ValidationRule<String>> emailRules,
                       List<ValidationRule<String>> passwordRules) {
        this.emailRules = emailRules;
        this.passwordRules = passwordRules;
    }

    public UserBuilder name(String name) { this.name = name; return this; }
    public UserBuilder email(String email) { this.email = email; return this; }
    public UserBuilder password(String password) { this.password = password; return this; }

    public User build() {
        List<String> errors = new ArrayList<>();

        emailRules.stream()
            .map(r -> r.validate(email))
            .filter(ValidationResult::isFailed)
            .map(ValidationResult::message)
            .forEach(errors::add);

        passwordRules.stream()
            .map(r -> r.validate(password))
            .filter(ValidationResult::isFailed)
            .map(ValidationResult::message)
            .forEach(errors::add);

        if (!errors.isEmpty()) throw new ValidationException(errors);

        return new User(name, email, hashPassword(password));
    }
}
```

---

## Combination 4: Proxy + Decorator (Layered Cross-Cutting Concerns)

**Used in**: Spring AOP, API gateways, service mesh, caching layers

**The confusion**: Proxy and Decorator look identical structurally. The rule:
- **Proxy** = same interface, adds *access control* (auth, rate limit, circuit break)
- **Decorator** = same interface, adds *behavior* (logging, caching, metrics)

Both can be stacked. Here's how it looks in practice:

```java
public interface ProductService {
    Product getProduct(String id);
    List<Product> search(String query);
    Product createProduct(Product product);
}

// Real implementation
public class ProductServiceImpl implements ProductService { /* actual logic */ }

// Decorator 1: Caching
public class CachingProductService implements ProductService {
    private final ProductService delegate;
    private final Cache<String, Product> cache;

    public Product getProduct(String id) {
        return cache.computeIfAbsent(id, k -> delegate.getProduct(k));
    }

    public List<Product> search(String query) {
        return delegate.search(query); // don't cache search
    }

    public Product createProduct(Product product) {
        Product created = delegate.createProduct(product);
        cache.put(created.getId(), created); // update cache on write
        return created;
    }
}

// Decorator 2: Metrics / Logging
public class InstrumentedProductService implements ProductService {
    private final ProductService delegate;
    private final MetricsClient metrics;

    public Product getProduct(String id) {
        long start = System.currentTimeMillis();
        try {
            Product result = delegate.getProduct(id);
            metrics.recordSuccess("getProduct", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            metrics.recordFailure("getProduct", e.getClass().getSimpleName());
            throw e;
        }
    }
    // similar for other methods
}

// Proxy: Authorization
public class AuthorizedProductService implements ProductService {
    private final ProductService delegate;
    private final AuthorizationService authService;

    public Product getProduct(String id) {
        authService.checkPermission("product:read");
        return delegate.getProduct(id);
    }

    public Product createProduct(Product product) {
        authService.checkPermission("product:write");
        return delegate.createProduct(product);
    }

    public List<Product> search(String query) {
        authService.checkPermission("product:read");
        return delegate.search(query);
    }
}

// Composition: Auth → Metrics → Cache → Real
ProductService service = new AuthorizedProductService(
    new InstrumentedProductService(
        new CachingProductService(
            new ProductServiceImpl(repository),
            new LocalCache<>()
        ),
        metricsClient
    ),
    authService
);

// Request flow:
// getProduct("123")
//   → AuthorizedProductService: check permission
//   → InstrumentedProductService: start timer
//   → CachingProductService: check cache, hit → return
//   → InstrumentedProductService: record time
//   → AuthorizedProductService: return to caller
```

**Interview signal**: "I'd layer cross-cutting concerns as decorators/proxies around the core service. This keeps auth, caching, and metrics completely separate from business logic and independently testable."

---

## Combination 5: Command + Composite (Macro Commands)

**Used in**: Text editors, IDEs, transaction batching, workflow engines

```java
// Atomic command
public interface Command {
    void execute();
    void undo();
    boolean isReversible();
}

// Composite command — sequence of commands treated as one
public class MacroCommand implements Command {
    private final String name;
    private final List<Command> commands;
    private final Deque<Command> executed = new ArrayDeque<>();

    public MacroCommand(String name, List<Command> commands) {
        this.name = name;
        this.commands = commands;
    }

    public void execute() {
        for (Command cmd : commands) {
            cmd.execute();
            executed.push(cmd); // track for undo
        }
    }

    public void undo() {
        // Undo in reverse order
        while (!executed.isEmpty()) {
            Command cmd = executed.pop();
            if (cmd.isReversible()) cmd.undo();
        }
    }

    public boolean isReversible() {
        return commands.stream().allMatch(Command::isReversible);
    }
}

// Command history with undo/redo
public class CommandHistory {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final int maxHistorySize;

    public void execute(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        if (undoStack.size() > maxHistorySize) {
            // Remove oldest — ArrayDeque doesn't support removeLast efficiently
            // Use LinkedList for this if needed
        }
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        Command cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        Command cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);
        return true;
    }
}

// Real use case: database transaction as MacroCommand
// "Transfer $100 from Alice to Bob" = [DebitAlice, CreditBob]
// If CreditBob fails → undo DebitAlice automatically
Command transfer = new MacroCommand("TRANSFER", List.of(
    new DebitCommand(aliceAccount, Money.of(100, USD)),
    new CreditCommand(bobAccount, Money.of(100, USD))
));
history.execute(transfer);
```

---

## Combination 6: Chain of Responsibility + Strategy

**Used in**: Middleware pipelines, validation chains, request processing

```java
// Each handler in the chain uses a strategy to decide how to process
public abstract class RequestHandler {
    protected RequestHandler next;

    public RequestHandler setNext(RequestHandler next) {
        this.next = next;
        return next;
    }

    public abstract Response handle(Request request);

    protected Response passToNext(Request request) {
        if (next != null) return next.handle(request);
        return Response.notFound();
    }
}

// Handler with pluggable strategy
public class RateLimitHandler extends RequestHandler {
    private final RateLimiter rateLimiter; // Strategy injected

    public RateLimitHandler(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public Response handle(Request request) {
        if (!rateLimiter.allowRequest(request.getUserId())) {
            return Response.tooManyRequests();
        }
        return passToNext(request);
    }
}

public class AuthHandler extends RequestHandler {
    private final AuthStrategy authStrategy; // Strategy injected

    public AuthHandler(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    public Response handle(Request request) {
        AuthResult result = authStrategy.authenticate(request.getToken());
        if (!result.isAuthenticated()) return Response.unauthorized();
        request.setUserId(result.getUserId()); // enrich request
        return passToNext(request);
    }
}

// Chain: RateLimit → Auth → Validation → Business Logic
RequestHandler pipeline = new RateLimitHandler(tokenBucketLimiter);
pipeline
    .setNext(new AuthHandler(jwtAuthStrategy))
    .setNext(new ValidationHandler(requestValidator))
    .setNext(new BusinessLogicHandler(orderService));

Response result = pipeline.handle(incomingRequest);
```

---

## Combination 7: Observer + Strategy (Event Routing)

**Used in**: Event buses, notification systems, workflow triggers

```java
// Events dispatched to different handlers based on type and routing strategy
public interface EventRouter {
    List<EventHandler> route(DomainEvent event);
}

public class TypeBasedRouter implements EventRouter {
    private final Map<Class<?>, List<EventHandler>> handlers = new HashMap<>();

    public <T extends DomainEvent> void register(Class<T> eventType, EventHandler handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public List<EventHandler> route(DomainEvent event) {
        return handlers.getOrDefault(event.getClass(), Collections.emptyList());
    }
}

public class PriorityRouter implements EventRouter {
    // Routes high-priority events to dedicated handlers
    public List<EventHandler> route(DomainEvent event) {
        if (event instanceof HighPriorityEvent) return highPriorityHandlers;
        return standardHandlers;
    }
}

public class EventBus {
    private final EventRouter router;
    private final Executor executor;

    public void publish(DomainEvent event) {
        List<EventHandler> handlers = router.route(event);
        handlers.forEach(handler ->
            executor.execute(() -> {
                try {
                    handler.handle(event);
                } catch (Exception e) {
                    deadLetterQueue.enqueue(event, e);
                }
            })
        );
    }
}
```

---

## Pattern Combination Decision Table

| Scenario | Combination | Why |
|---|---|---|
| Multiple algorithms, runtime selection, extensible | Strategy + Factory + Registry | OCP for new algorithms |
| Object lifecycle with side effects | State + Observer | Separate transitions from reactions |
| Complex object construction with validation | Builder + Strategy (validators) | Flexible validation rules |
| Cross-cutting concerns (auth, cache, logging) | Proxy + Decorator (layered) | Each concern in own class |
| Undo/redo, batch operations | Command + Composite | Atomic and composite undos |
| Request pipeline with pluggable steps | CoR + Strategy | Each step has swappable impl |
| Event routing with different handlers | Observer + Strategy | Routing logic is swappable |

---

## The "Real System" Acid Test

For any LLD problem, ask yourself:
1. **What can change?** → That's your Strategy
2. **What transitions through states?** → That's your State
3. **What needs to react to changes?** → That's your Observer
4. **What needs to be composed?** → That's your Composite or Decorator
5. **What needs access control or augmentation?** → That's your Proxy or Decorator
6. **What's complex to create?** → That's your Builder or Factory

A real production service will use 3-5 patterns simultaneously. The question isn't "which pattern?" — it's "which patterns work together here?"
