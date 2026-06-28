# 22 — Clean Architecture & Hexagonal Architecture in Java

> Theory without code is useless. This file shows exactly how a real OrderService looks when Domain has zero infrastructure imports — the code Staff-level candidates write that juniors cannot.

---

## The Core Rule

```
Dependencies point INWARD only.

  [ Infrastructure (DB, HTTP, Kafka) ]
    [ Application (Use Cases) ]
      [ Domain (Entities, Interfaces, Rules) ]

Domain knows NOTHING about Application or Infrastructure.
Infrastructure implements interfaces defined in Domain.
```

---

## Full Example: Order Management

### Domain Layer (Zero External Dependencies)

```java
// Value Objects
public record OrderId(String value) {
    public OrderId { Objects.requireNonNull(value); }
    public static OrderId generate() { return new OrderId(UUID.randomUUID().toString()); }
}

public record Money(BigDecimal amount, String currency) {
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount), this.currency);
    }
    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }
}

// Entity — business rules here, no DB/HTTP imports
public class Order {
    private final OrderId id;
    private final String customerId;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public Order(OrderId id, String customerId, List<OrderLine> lines) {
        if (lines.isEmpty()) throw new IllegalArgumentException("Order must have lines");
        this.id = id;
        this.customerId = customerId;
        this.lines = new ArrayList<>(lines);
        this.status = OrderStatus.PENDING;
        domainEvents.add(new OrderCreatedEvent(id, customerId));
    }

    public void confirm() {
        if (status != OrderStatus.PENDING)
            throw new InvalidOrderStateException("Cannot confirm order in state: " + status);
        this.status = OrderStatus.CONFIRMED;
        domainEvents.add(new OrderConfirmedEvent(id));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
            throw new InvalidOrderStateException("Cannot cancel shipped/delivered order");
        this.status = OrderStatus.CANCELLED;
        domainEvents.add(new OrderCancelledEvent(id, reason));
    }

    public Money calculateTotal() {
        return lines.stream()
            .map(OrderLine::subtotal)
            .reduce(Money.of(0, "INR"), Money::add);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    public OrderId getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
}

public record OrderLine(String productId, int quantity, Money unitPrice) {
    public Money subtotal() {
        return new Money(unitPrice.amount().multiply(BigDecimal.valueOf(quantity)), unitPrice.currency());
    }
}

public enum OrderStatus { PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED }

// Domain Events
public sealed interface DomainEvent permits OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent, OrderShippedEvent {
    Instant occurredAt();
}
public record OrderCreatedEvent(OrderId orderId, String customerId) implements DomainEvent {
    public Instant occurredAt() { return Instant.now(); }
}
public record OrderConfirmedEvent(OrderId orderId) implements DomainEvent {
    public Instant occurredAt() { return Instant.now(); }
}
public record OrderCancelledEvent(OrderId orderId, String reason) implements DomainEvent {
    public Instant occurredAt() { return Instant.now(); }
}
public record OrderShippedEvent(OrderId orderId, String trackingNumber) implements DomainEvent {
    public Instant occurredAt() { return Instant.now(); }
}

// Domain Interfaces (Ports) — defined IN domain, implemented in infrastructure
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(String customerId);
}

public interface InventoryPort {
    boolean isAvailable(String productId, int quantity);
    void reserve(String productId, int quantity, OrderId orderId);
    void release(String productId, int quantity, OrderId orderId);
}

public interface DomainEventPublisher {
    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) { super(message); }
}
```

### Application Layer (Use Cases — depends only on Domain)

```java
// Request/Response DTOs
public record PlaceOrderCommand(String customerId, List<OrderLineRequest> lines) {}
public record OrderLineRequest(String productId, int quantity, double unitPrice, String currency) {}
public record PlaceOrderResult(String orderId, String status, double total) {}
public record CancelOrderCommand(String orderId, String reason) {}

// Use Case — one class per business operation
public class PlaceOrderUseCase {
    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final DomainEventPublisher eventPublisher;

    public PlaceOrderUseCase(OrderRepository orderRepository,
                              InventoryPort inventoryPort,
                              DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.eventPublisher = eventPublisher;
    }

    public PlaceOrderResult execute(PlaceOrderCommand command) {
        // Check inventory
        command.lines().forEach(line -> {
            if (!inventoryPort.isAvailable(line.productId(), line.quantity()))
                throw new InsufficientInventoryException(line.productId(), line.quantity());
        });

        // Build domain object
        List<OrderLine> lines = command.lines().stream()
            .map(l -> new OrderLine(l.productId(), l.quantity(), Money.of(l.unitPrice(), l.currency())))
            .collect(toList());

        Order order = new Order(OrderId.generate(), command.customerId(), lines);

        // Reserve inventory
        command.lines().forEach(line ->
            inventoryPort.reserve(line.productId(), line.quantity(), order.getId()));

        // Apply business rule
        order.confirm();

        // Persist + publish
        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents());

        return new PlaceOrderResult(order.getId().value(), order.getStatus().name(),
            order.calculateTotal().amount().doubleValue());
    }
}

public class CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final InventoryPort inventoryPort;
    private final DomainEventPublisher eventPublisher;

    public CancelOrderUseCase(OrderRepository orderRepository,
                               InventoryPort inventoryPort,
                               DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.eventPublisher = eventPublisher;
    }

    public void execute(CancelOrderCommand command) {
        Order order = orderRepository.findById(new OrderId(command.orderId()))
            .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        order.getLines().forEach(line ->
            inventoryPort.release(line.productId(), line.quantity(), order.getId()));

        order.cancel(command.reason());

        orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents());
    }
}

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String id) { super("Order not found: " + id); }
}

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String productId, int qty) {
        super("Insufficient inventory: " + productId + " qty: " + qty);
    }
}
```

### Infrastructure Layer (Adapters — implements domain interfaces)

```java
// Driving Adapter: REST -> calls Use Cases
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(PlaceOrderUseCase placeOrder, CancelOrderUseCase cancelOrder) {
        this.placeOrderUseCase = placeOrder;
        this.cancelOrderUseCase = cancelOrder;
    }

    @PostMapping
    public ResponseEntity<PlaceOrderResult> placeOrder(@RequestBody PlaceOrderCommand cmd) {
        return ResponseEntity.status(201).body(placeOrderUseCase.execute(cmd));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id, @RequestParam String reason) {
        cancelOrderUseCase.execute(new CancelOrderCommand(id, reason));
        return ResponseEntity.noContent().build();
    }
}

// Driven Adapter: JPA implementation of OrderRepository
@Repository
public class JpaOrderRepository implements OrderRepository {
    private final JpaOrderEntityRepo jpaRepo;
    private final OrderMapper mapper;

    public JpaOrderRepository(JpaOrderEntityRepo jpaRepo, OrderMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    public void save(Order order) { jpaRepo.save(mapper.toEntity(order)); }
    public Optional<Order> findById(OrderId id) {
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }
    public List<Order> findByCustomer(String customerId) {
        return jpaRepo.findByCustomerId(customerId).stream()
            .map(mapper::toDomain).collect(toList());
    }
}

// Driven Adapter: Kafka implementation of DomainEventPublisher
@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void publish(DomainEvent event) {
        try {
            String topic = switch (event) {
                case OrderCreatedEvent e -> "orders.created";
                case OrderConfirmedEvent e -> "orders.confirmed";
                case OrderCancelledEvent e -> "orders.cancelled";
                case OrderShippedEvent e -> "orders.shipped";
            };
            kafka.send(topic, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    public void publishAll(List<DomainEvent> events) { events.forEach(this::publish); }
}

// Test Adapter: In-Memory (no DB, no framework, runs in milliseconds)
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> store = new ConcurrentHashMap<>();
    public void save(Order order) { store.put(order.getId().value(), order); }
    public Optional<Order> findById(OrderId id) { return Optional.ofNullable(store.get(id.value())); }
    public List<Order> findByCustomer(String customerId) {
        return store.values().stream()
            .filter(o -> o.getCustomerId().equals(customerId))
            .collect(toList());
    }
}

public class InMemoryEventPublisher implements DomainEventPublisher {
    private final List<DomainEvent> published = new ArrayList<>();
    public void publish(DomainEvent event) { published.add(event); }
    public void publishAll(List<DomainEvent> events) { published.addAll(events); }
    public <T> boolean hasEvent(Class<T> type) { return published.stream().anyMatch(type::isInstance); }
    public List<DomainEvent> getPublished() { return Collections.unmodifiableList(published); }
}
```

### Tests — The Payoff

```java
// Domain test — pure Java, zero mocks, zero framework, runs in <10ms
class OrderDomainTest {
    @Test
    void cannotCancelShippedOrder() {
        Order order = buildOrder();
        order.confirm();
        // simulate ship
        assertThrows(InvalidOrderStateException.class, () -> order.cancel("Changed mind after ship"));
    }

    @Test
    void totalIsCorrect() {
        Order order = new Order(OrderId.generate(), "cust1", List.of(
            new OrderLine("p1", 2, Money.of(100, "INR")),
            new OrderLine("p2", 1, Money.of(250, "INR"))
        ));
        assertEquals(Money.of(450, "INR"), order.calculateTotal());
    }
}

// Use case test — in-memory adapters, no Spring context needed
class PlaceOrderUseCaseTest {
    private final InMemoryOrderRepository orderRepo = new InMemoryOrderRepository();
    private final InMemoryEventPublisher events = new InMemoryEventPublisher();
    private final FakeInventoryPort inventory = new FakeInventoryPort(true);
    private final PlaceOrderUseCase useCase = new PlaceOrderUseCase(orderRepo, inventory, events);

    @Test
    void placeOrder_success_publishesEvents() {
        PlaceOrderCommand cmd = new PlaceOrderCommand("cust1",
            List.of(new OrderLineRequest("prod1", 2, 100.0, "INR")));

        PlaceOrderResult result = useCase.execute(cmd);

        assertEquals("CONFIRMED", result.status());
        assertEquals(200.0, result.total());
        assertTrue(events.hasEvent(OrderCreatedEvent.class));
        assertTrue(events.hasEvent(OrderConfirmedEvent.class));
    }

    @Test
    void placeOrder_outOfStock_throws() {
        FakeInventoryPort noStock = new FakeInventoryPort(false);
        PlaceOrderUseCase uc = new PlaceOrderUseCase(orderRepo, noStock, events);
        assertThrows(InsufficientInventoryException.class, () ->
            uc.execute(new PlaceOrderCommand("cust1",
                List.of(new OrderLineRequest("prod1", 100, 100.0, "INR")))));
    }
}

class FakeInventoryPort implements InventoryPort {
    private final boolean available;
    FakeInventoryPort(boolean available) { this.available = available; }
    public boolean isAvailable(String p, int q) { return available; }
    public void reserve(String p, int q, OrderId o) {}
    public void release(String p, int q, OrderId o) {}
}
```

---

## What This Achieves

| Scenario | Impact |
|---|---|
| Swap MySQL → MongoDB | Change only `JpaOrderRepository`. Zero domain changes. |
| Swap Kafka → SQS | Change only `KafkaDomainEventPublisher`. Zero domain changes. |
| Add REST endpoint | Add controller. Zero domain changes. |
| Unit test domain | Pure Java. No Spring context. Runs in milliseconds. |
| Unit test use case | In-memory adapters. No DB. No network. |
| Integration test | Real adapters. Full stack. Separate test suite. |

---

## Interview Script

**Junior round**: "I'd define `OrderRepository` as an interface. The service depends on the interface, not the concrete MySQL class."

**Senior round**: "I'd put `OrderRepository` in the domain layer — the domain defines what it needs. Infrastructure provides what domain asks for. Swapping MySQL for MongoDB means changing only the infrastructure adapter."

**Staff round**: "I'd use Clean Architecture. Domain has zero external dependencies — entities, value objects, domain events, and port interfaces all live there. Use cases in the application layer orchestrate domain objects and ports. Infrastructure implements ports — JPA, Kafka, HTTP clients. Domain tests are pure Java running in milliseconds. Use case tests use in-memory adapters with no framework. Integration tests run the full stack. Any infrastructure swap touches exactly one class."

That last answer is what gets you "Strong Hire" at Staff level.
