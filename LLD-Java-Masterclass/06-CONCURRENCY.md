# 06 — Concurrency Essentials for LLD

---

## Why Concurrency in LLD Rounds

Every FAANG interviewer will ask: **"What if two users do X simultaneously?"**
If you say "I haven't thought about that" — round over. You must proactively mention thread safety.

---

## 1. The Core Problem: Race Conditions

```java
// BROKEN — race condition
public class TicketBookingService {
    private int availableSeats = 100;
    
    public boolean bookSeat() {
        if (availableSeats > 0) {         // Thread A checks: 1 seat
            // Thread B also checks: 1 seat — BOTH pass!
            availableSeats--;              // Both decrement → -1 seats!
            return true;
        }
        return false;
    }
}

// FIX 1 — synchronized (simple but coarse)
public synchronized boolean bookSeat() {
    if (availableSeats > 0) {
        availableSeats--;
        return true;
    }
    return false;
}

// FIX 2 — AtomicInteger (lock-free, faster for simple ops)
private final AtomicInteger availableSeats = new AtomicInteger(100);

public boolean bookSeat() {
    return availableSeats.getAndDecrement() > 0;
}

// FIX 3 — ReentrantLock (more control: tryLock, timeout, fairness)
private final ReentrantLock lock = new ReentrantLock();
private int availableSeats = 100;

public boolean bookSeat() {
    lock.lock();
    try {
        if (availableSeats > 0) {
            availableSeats--;
            return true;
        }
        return false;
    } finally {
        lock.unlock(); // ALWAYS in finally
    }
}
```

---

## 2. Visibility: volatile

```java
// BROKEN — without volatile, thread may cache stale value
private boolean running = true;

public void stop() { running = false; }

public void run() {
    while (running) { } // may never stop — cached old value!
}

// FIX
private volatile boolean running = true;
// volatile guarantees: writes are visible to all threads immediately
// Does NOT guarantee atomicity (use Atomic* for that)
```

---

## 3. Thread-Safe Collections

```java
// NOT thread-safe
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();

// Thread-safe options
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
List<String> cowList = new CopyOnWriteArrayList<>(); // best for read-heavy
Map<String, Integer> concMap = new ConcurrentHashMap<>(); // always prefer over Hashtable
Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();
BlockingQueue<Task> boundedQueue = new ArrayBlockingQueue<>(100);
```

### When to use which:

| Need | Use |
|---|---|
| Read-heavy list, rare writes | `CopyOnWriteArrayList` |
| High-concurrency map | `ConcurrentHashMap` |
| Producer-consumer | `BlockingQueue` (ArrayBlockingQueue, LinkedBlockingQueue) |
| Priority queue with concurrency | `PriorityBlockingQueue` |
| Bounded, blocking | `ArrayBlockingQueue` |
| Unbounded, non-blocking | `ConcurrentLinkedQueue` |

---

## 4. Producer-Consumer Pattern

```java
public class TaskQueue {
    private final BlockingQueue<Task> queue;
    
    public TaskQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }
    
    // Producer — blocks if queue full
    public void submit(Task task) throws InterruptedException {
        queue.put(task); // blocks until space available
    }
    
    // Consumer — blocks if queue empty
    public Task take() throws InterruptedException {
        return queue.take(); // blocks until item available
    }
    
    // Non-blocking alternatives
    public boolean trySubmit(Task task) { return queue.offer(task); }
    public Task poll() { return queue.poll(); } // returns null if empty
}

// Worker thread pool
public class WorkerPool {
    private final TaskQueue taskQueue;
    private final List<Thread> workers = new ArrayList<>();
    
    public WorkerPool(int threadCount, int queueCapacity) {
        this.taskQueue = new TaskQueue(queueCapacity);
        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(this::workerLoop, "Worker-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }
    
    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = taskQueue.take();
                task.execute();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag
                break;
            }
        }
    }
    
    public void submit(Task task) throws InterruptedException {
        taskQueue.submit(task);
    }
}
```

---

## 5. ReadWriteLock — For Read-Heavy Workloads

```java
public class UserCache {
    private final Map<String, User> cache = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    
    // Multiple readers allowed simultaneously
    public Optional<User> get(String userId) {
        readLock.lock();
        try {
            return Optional.ofNullable(cache.get(userId));
        } finally {
            readLock.unlock();
        }
    }
    
    // Only one writer, exclusive
    public void put(String userId, User user) {
        writeLock.lock();
        try {
            cache.put(userId, user);
        } finally {
            writeLock.unlock();
        }
    }
}
```

---

## 6. Deadlock — Recognition and Prevention

```java
// DEADLOCK — classic example
Object lockA = new Object();
Object lockB = new Object();

// Thread 1
synchronized(lockA) {
    synchronized(lockB) { /* work */ }  // holds A, waits for B
}

// Thread 2
synchronized(lockB) {
    synchronized(lockA) { /* work */ }  // holds B, waits for A → DEADLOCK
}

// Prevention strategies:
// 1. Consistent lock ordering — always acquire A before B
synchronized(lockA) {
    synchronized(lockB) { /* both threads acquire in same order */ }
}

// 2. tryLock with timeout
if (lockA.tryLock(1, TimeUnit.SECONDS)) {
    try {
        if (lockB.tryLock(1, TimeUnit.SECONDS)) {
            try { /* work */ }
            finally { lockB.unlock(); }
        }
    } finally { lockA.unlock(); }
}

// 3. Use higher-level abstractions (avoid manual locking)
```

---

## 7. CountDownLatch and CyclicBarrier

```java
// CountDownLatch — wait for N events to complete
public void runParallelDataLoad() throws InterruptedException {
    int loaderCount = 5;
    CountDownLatch latch = new CountDownLatch(loaderCount);
    
    for (int i = 0; i < loaderCount; i++) {
        final int shardId = i;
        executor.submit(() -> {
            try {
                loadShard(shardId);
            } finally {
                latch.countDown(); // signal completion
            }
        });
    }
    
    latch.await(); // wait until all loaders finish
    System.out.println("All shards loaded — starting processing");
}

// CyclicBarrier — all threads wait at barrier, then proceed together
CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("All ready!"));

// Each thread calls barrier.await() — all wait until all 3 have arrived
```

---

## 8. Semaphore — Rate Limiting / Connection Pool

```java
public class ConnectionPool {
    private final Semaphore semaphore;
    private final Queue<Connection> connections;
    
    public ConnectionPool(int maxConnections) {
        this.semaphore = new Semaphore(maxConnections, true); // fair
        this.connections = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < maxConnections; i++) {
            connections.offer(createConnection());
        }
    }
    
    public Connection acquire() throws InterruptedException {
        semaphore.acquire(); // blocks if no connections available
        return connections.poll();
    }
    
    public void release(Connection connection) {
        connections.offer(connection);
        semaphore.release();
    }
}
```

---

## 9. CompletableFuture — Async Operations

```java
public class OrderService {
    public CompletableFuture<OrderResult> processOrder(Order order) {
        return CompletableFuture
            .supplyAsync(() -> validateOrder(order))           // async validate
            .thenApplyAsync(valid -> reserveInventory(order))  // then reserve
            .thenApplyAsync(reserved -> processPayment(order)) // then pay
            .thenApplyAsync(paid -> createShipment(order))    // then ship
            .exceptionally(ex -> {
                rollback(order);
                return OrderResult.failed(ex.getMessage());
            });
    }
    
    // Parallel calls
    public CompletableFuture<OrderSummary> getOrderSummary(String orderId) {
        CompletableFuture<Order> orderFuture = fetchOrder(orderId);
        CompletableFuture<List<Item>> itemsFuture = fetchItems(orderId);
        CompletableFuture<Customer> customerFuture = fetchCustomer(orderId);
        
        return CompletableFuture.allOf(orderFuture, itemsFuture, customerFuture)
            .thenApply(v -> new OrderSummary(
                orderFuture.join(),
                itemsFuture.join(),
                customerFuture.join()
            ));
    }
}
```

---

## 10. Thread-Safe Singleton — All Approaches

```java
// Approach 1: Enum (recommended)
public enum Config { INSTANCE; }

// Approach 2: DCL with volatile
public class Config {
    private static volatile Config instance;
    public static Config getInstance() {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) instance = new Config();
            }
        }
        return instance;
    }
}

// Approach 3: Initialization-on-demand (elegant, no sync overhead)
public class Config {
    private Config() {}
    
    private static class Holder {
        static final Config INSTANCE = new Config();
        // Loaded lazily when Holder is first accessed
        // JVM guarantees class loading is thread-safe
    }
    
    public static Config getInstance() { return Holder.INSTANCE; }
}
```

---

## Interview Concurrency Checklist

When presenting any LLD design, mention these proactively:

```
1. What shared state exists?
2. Is it accessed by multiple threads?
3. What's the right synchronization primitive?
   - Simple counter → AtomicInteger
   - Read-heavy map → ConcurrentHashMap + ReadWriteLock
   - Producer-consumer → BlockingQueue
   - One-time coordination → CountDownLatch
   - Rate limiting → Semaphore
4. Can deadlock occur? (multiple locks + different order)
5. Is volatile needed anywhere?
6. Can I use higher-level abstractions instead of raw synchronized?
```
