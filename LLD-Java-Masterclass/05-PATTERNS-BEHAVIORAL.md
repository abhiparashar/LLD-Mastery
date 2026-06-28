# 05 — Design Patterns: Behavioral

---

## Strategy

**Intent**: Define a family of algorithms, encapsulate each, and make them interchangeable.

**When to use**: Multiple ways to do the same thing. Switch/if-else on type for behavior.

```java
public interface SortStrategy {
    void sort(int[] data);
}

public class QuickSort implements SortStrategy {
    public void sort(int[] data) { /* quicksort */ }
}

public class MergeSort implements SortStrategy {
    public void sort(int[] data) { /* mergesort */ }
}

public class DataProcessor {
    private SortStrategy strategy;
    
    public DataProcessor(SortStrategy strategy) { this.strategy = strategy; }
    
    // Can switch strategy at runtime
    public void setStrategy(SortStrategy strategy) { this.strategy = strategy; }
    
    public void process(int[] data) {
        strategy.sort(data);
        // rest of processing
    }
}

// Real-world: payment strategies
public interface PaymentStrategy {
    PaymentResult pay(double amount);
}

public class CreditCardStrategy implements PaymentStrategy { ... }
public class UPIStrategy implements PaymentStrategy { ... }
public class WalletStrategy implements PaymentStrategy { ... }
```

### Strategy Traps

**Q: Strategy vs State?**
A: Strategy = algorithm choice (client picks strategy, usually stays). State = object behavior changes as internal state changes (transitions are driven by the object itself). Both look identical structurally — intent differs.

**Q: Strategy vs Template Method?**
A: Strategy uses composition (inject different algo). Template Method uses inheritance (subclass overrides steps). Prefer Strategy — composition over inheritance.

---

## Observer

**Intent**: Define a one-to-many dependency between objects. When one changes, all dependents are notified.

```java
public interface Observer {
    void update(Event event);
}

public interface Observable {
    void subscribe(String eventType, Observer observer);
    void unsubscribe(String eventType, Observer observer);
    void notify(Event event);
}

public class EventBus implements Observable {
    private final Map<String, List<Observer>> listeners = new ConcurrentHashMap<>();
    
    public void subscribe(String eventType, Observer observer) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(observer);
    }
    
    public void unsubscribe(String eventType, Observer observer) {
        listeners.getOrDefault(eventType, Collections.emptyList()).remove(observer);
    }
    
    public void notify(Event event) {
        listeners.getOrDefault(event.getType(), Collections.emptyList())
                 .forEach(obs -> obs.update(event));
    }
}

// Observers
public class EmailNotifier implements Observer {
    public void update(Event event) { sendEmail(event); }
}

public class AuditLogger implements Observer {
    public void update(Event event) { logToAudit(event); }
}

public class InventoryUpdater implements Observer {
    public void update(Event event) { updateStock(event); }
}

// When order is placed — all observers notified
eventBus.subscribe("ORDER_PLACED", new EmailNotifier());
eventBus.subscribe("ORDER_PLACED", new AuditLogger());
eventBus.subscribe("ORDER_PLACED", new InventoryUpdater());
```

### Observer Traps

**Q: What's the risk of Observer in multithreaded code?**
A: Three risks:
1. `ConcurrentModificationException` if observers modify the list during iteration — use `CopyOnWriteArrayList`
2. Deadlocks if observer calls back into subject
3. Memory leaks — observers not unsubscribed hold references

**Q: Observer vs Event-Driven / Pub-Sub?**
A: Observer — observers know the subject. Pub-Sub — publisher and subscribers are decoupled via a message broker/bus. Pub-Sub is Observer at scale.

**Q: What's "lapsed listener" problem?**
A: An observer is registered but never unsubscribed, preventing GC. Common in UI code. Fix: use weak references or explicit unsubscribe lifecycle.

---

## State

**Intent**: Allow an object to alter its behavior when its internal state changes.

```java
public interface OrderState {
    void confirm(OrderContext ctx);
    void ship(OrderContext ctx);
    void deliver(OrderContext ctx);
    void cancel(OrderContext ctx);
    String getStateName();
}

public class OrderContext {
    private OrderState state = new PendingState();
    private final String orderId;
    
    public void setState(OrderState state) { this.state = state; }
    public void confirm() { state.confirm(this); }
    public void ship() { state.ship(this); }
    public void deliver() { state.deliver(this); }
    public void cancel() { state.cancel(this); }
    public String getStatus() { return state.getStateName(); }
}

public class PendingState implements OrderState {
    public void confirm(OrderContext ctx) { ctx.setState(new ConfirmedState()); }
    public void ship(OrderContext ctx) { throw new IllegalStateException("Confirm first"); }
    public void deliver(OrderContext ctx) { throw new IllegalStateException("Ship first"); }
    public void cancel(OrderContext ctx) { ctx.setState(new CancelledState()); }
    public String getStateName() { return "PENDING"; }
}

public class ConfirmedState implements OrderState {
    public void confirm(OrderContext ctx) { throw new IllegalStateException("Already confirmed"); }
    public void ship(OrderContext ctx) { ctx.setState(new ShippedState()); }
    public void deliver(OrderContext ctx) { throw new IllegalStateException("Ship first"); }
    public void cancel(OrderContext ctx) { ctx.setState(new CancelledState()); }
    public String getStateName() { return "CONFIRMED"; }
}

public class ShippedState implements OrderState {
    public void confirm(OrderContext ctx) { throw new IllegalStateException("Already confirmed"); }
    public void ship(OrderContext ctx) { throw new IllegalStateException("Already shipped"); }
    public void deliver(OrderContext ctx) { ctx.setState(new DeliveredState()); }
    public void cancel(OrderContext ctx) { throw new IllegalStateException("Already shipped, cannot cancel"); }
    public String getStateName() { return "SHIPPED"; }
}
```

### State Traps

**Q: State vs Strategy?**
A: Same structure, different intent. State manages lifecycle/transitions. Strategy is algorithm swapping. In State, the context itself (or the state) drives transitions. In Strategy, the client picks the strategy.

**Q: When would you use a state machine library vs hand-rolling State pattern?**
A: State pattern for simple (< 6 states), known transitions. Spring State Machine / Stateless4j for complex workflows (billing, order management, document approval).

---

## Command

**Intent**: Encapsulate a request as an object, allowing parameterization, queuing, logging, and undoable operations.

```java
public interface Command {
    void execute();
    void undo();
}

public class TextEditor {
    private StringBuilder text = new StringBuilder();
    
    public void insertText(int position, String content) {
        text.insert(position, content);
    }
    
    public void deleteText(int position, int length) {
        text.delete(position, position + length);
    }
    
    public String getText() { return text.toString(); }
}

public class InsertCommand implements Command {
    private final TextEditor editor;
    private final int position;
    private final String content;
    
    public InsertCommand(TextEditor editor, int position, String content) {
        this.editor = editor; this.position = position; this.content = content;
    }
    
    public void execute() { editor.insertText(position, content); }
    public void undo() { editor.deleteText(position, content.length()); }
}

// Command history for undo/redo
public class CommandHistory {
    private final Deque<Command> history = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    
    public void execute(Command command) {
        command.execute();
        history.push(command);
        redoStack.clear(); // new action clears redo history
    }
    
    public void undo() {
        if (!history.isEmpty()) {
            Command cmd = history.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
    }
    
    public void redo() {
        if (!redoStack.isEmpty()) {
            Command cmd = redoStack.pop();
            cmd.execute();
            history.push(cmd);
        }
    }
}
```

### Command Traps

**Q: How do you implement macro commands?**
```java
public class MacroCommand implements Command {
    private final List<Command> commands;
    public void execute() { commands.forEach(Command::execute); }
    public void undo() { 
        ListIterator<Command> it = commands.listIterator(commands.size());
        while (it.hasPrevious()) it.previous().undo(); // reverse order!
    }
}
```

**Q: Command vs Strategy?**
A: Command encapsulates an action (what to do + when + context). Strategy encapsulates an algorithm (how to do something). Command can be queued, logged, undone. Strategy is about replacing algorithms.

---

## Chain of Responsibility

**Intent**: Pass a request along a chain of handlers. Each handler decides to process or pass it on.

```java
public abstract class SupportHandler {
    protected SupportHandler next;
    
    public SupportHandler setNext(SupportHandler next) {
        this.next = next;
        return next; // enables chaining: a.setNext(b).setNext(c)
    }
    
    public abstract void handle(SupportTicket ticket);
    
    protected void passToNext(SupportTicket ticket) {
        if (next != null) next.handle(ticket);
        else System.out.println("Ticket " + ticket.id() + " unresolved — escalate to management");
    }
}

public class Level1Support extends SupportHandler {
    public void handle(SupportTicket ticket) {
        if (ticket.priority() == Priority.LOW) {
            System.out.println("L1 resolved: " + ticket.id());
        } else {
            passToNext(ticket);
        }
    }
}

public class Level2Support extends SupportHandler {
    public void handle(SupportTicket ticket) {
        if (ticket.priority() == Priority.MEDIUM) {
            System.out.println("L2 resolved: " + ticket.id());
        } else {
            passToNext(ticket);
        }
    }
}

public class Level3Support extends SupportHandler {
    public void handle(SupportTicket ticket) {
        System.out.println("L3 engineering resolved: " + ticket.id());
    }
}

// Chain setup
SupportHandler l1 = new Level1Support();
SupportHandler l2 = new Level2Support();
SupportHandler l3 = new Level3Support();
l1.setNext(l2).setNext(l3);

l1.handle(new SupportTicket("T001", Priority.HIGH));
```

### CoR Real Use Cases
- Servlet filters (`javax.servlet.Filter`)
- Spring Security filter chain
- Middleware in web frameworks
- Logging levels (DEBUG → INFO → WARN → ERROR)
- Approval workflows

### CoR Traps

**Q: What if no handler processes the request?**
A: Design a catch-all terminal handler (like `Level3Support` above). Or throw an exception. Never silently drop the request.

**Q: CoR vs Observer?**
A: CoR — only one handler processes (or it continues). Observer — ALL observers are notified.

---

## Template Method

**Intent**: Define the skeleton of an algorithm in a base class, deferring some steps to subclasses.

```java
public abstract class DataMigration {
    // Template method — algorithm skeleton, final = can't override
    public final void migrate() {
        connect();
        List<Record> data = extract();
        List<Record> transformed = transform(data);
        load(transformed);
        disconnect();
        sendReport(transformed.size());
    }
    
    protected abstract void connect();
    protected abstract List<Record> extract();
    protected abstract List<Record> transform(List<Record> data);
    protected abstract void load(List<Record> data);
    
    // Hook — optional override
    protected void disconnect() { System.out.println("Default disconnect"); }
    
    // Concrete step — shared by all subclasses
    private void sendReport(int count) {
        System.out.println("Migrated " + count + " records");
    }
}

public class MySQLToPostgresMigration extends DataMigration {
    protected void connect() { /* connect to MySQL + Postgres */ }
    protected List<Record> extract() { return mysqlQuery("SELECT * FROM users"); }
    protected List<Record> transform(List<Record> data) { return convertTypes(data); }
    protected void load(List<Record> data) { postgresInsert(data); }
}
```

### Template Method Traps

**Q: Template Method vs Strategy?**
A: Template = inheritance (subclass overrides steps). Strategy = composition (inject different algorithm). Modern Java prefers Strategy for testability. Template Method is fine when base class logic is stable and shared.

**Q: What's a "hook" method?**
A: An optional override point in Template Method. Has a default (empty or no-op) implementation. Subclasses override only if they need to customize that step.

---

## Mediator

**Intent**: Reduce chaotic dependencies by centralizing communication through a mediator.

```java
// Without Mediator — components reference each other directly (M*N dependencies)
// With Mediator — all components talk to mediator (M+N dependencies)

public interface ChatMediator {
    void sendMessage(String message, User sender);
    void addUser(User user);
}

public class ChatRoom implements ChatMediator {
    private final List<User> users = new ArrayList<>();
    
    public void addUser(User user) { users.add(user); }
    
    public void sendMessage(String message, User sender) {
        users.stream()
             .filter(u -> u != sender)
             .forEach(u -> u.receive(message, sender.getName()));
    }
}

public class User {
    private final String name;
    private final ChatMediator mediator;
    
    public User(String name, ChatMediator mediator) {
        this.name = name;
        this.mediator = mediator;
    }
    
    public void send(String message) { mediator.sendMessage(message, this); }
    public void receive(String message, String from) {
        System.out.println(name + " received from " + from + ": " + message);
    }
    public String getName() { return name; }
}
```

### Mediator Traps

**Q: Mediator vs Observer?**
A: Observer = broadcast (one-to-many). Mediator = routing (many-to-many, mediator decides who gets what). ATC (Air Traffic Control) is Mediator — planes don't talk to each other, they talk to ATC.

**Q: Mediator vs Event Bus?**
A: Event Bus is a mediator implementation. Distinction is in coupling — Event Bus is async and decoupled; Mediator can be synchronous and aware of participants.

---

## Iterator

```java
public interface Iterator<T> {
    boolean hasNext();
    T next();
}

// Custom collection with iterator
public class UserCollection {
    private final User[] users;
    private int size = 0;
    
    public UserCollection(int capacity) { users = new User[capacity]; }
    
    public void add(User user) { users[size++] = user; }
    
    public Iterator<User> iterator() {
        return new Iterator<>() {
            private int index = 0;
            public boolean hasNext() { return index < size; }
            public User next() {
                if (!hasNext()) throw new NoSuchElementException();
                return users[index++];
            }
        };
    }
}
```

---

## Behavioral Patterns Summary

| Pattern | Intent | Key Signal |
|---|---|---|
| Strategy | Swap algorithms | Multiple ways to do X |
| Observer | Notify dependents | Event-driven, pub-sub |
| State | Change behavior with state | Order lifecycle, ATM states |
| Command | Encapsulate action | Undo/redo, queuing, macro |
| Chain of Responsibility | Pass through handlers | Filters, approval chains |
| Template Method | Algorithm skeleton | Steps vary by subclass |
| Mediator | Central communication hub | Chat, ATC, event routing |
| Iterator | Sequential access | Custom collections |
