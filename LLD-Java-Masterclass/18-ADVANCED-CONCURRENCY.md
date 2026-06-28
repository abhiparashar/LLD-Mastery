# 18 — Advanced Concurrency Patterns (Staff Engineer Level)

> This is where senior engineers separate from the pack. Everyone knows synchronized. This file covers what you say when an interviewer pushes past the basics.

---

## 1. Lock Striping — ConcurrentHashMap Internals

**Problem**: A single lock on a shared map serializes all operations. With 32 CPU cores, you're wasting 31 of them.

**Solution**: Divide the map into N independent segments, each with its own lock.

```java
// Naive: one lock for everything
public class NaiveCache<K, V> {
    private final Map<K, V> map = new HashMap<>();

    public synchronized V get(K key) { return map.get(key); }
    public synchronized void put(K key, V value) { map.put(key, value); }
    // Problem: all reads and writes serialized. 32 threads = 1 thread performance
}

// Lock striping: multiple locks, each guards a subset
public class StripedCache<K, V> {
    private static final int STRIPE_COUNT = 16; // power of 2
    private final Map<K, V>[] maps;
    private final ReentrantReadWriteLock[] locks;

    @SuppressWarnings("unchecked")
    public StripedCache() {
        maps = new HashMap[STRIPE_COUNT];
        locks = new ReentrantReadWriteLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            maps[i] = new HashMap<>();
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    private int stripeIndex(K key) {
        // Spread hash evenly across stripes
        return (key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT;
    }

    public V get(K key) {
        int idx = stripeIndex(key);
        locks[idx].readLock().lock(); // multiple readers allowed per stripe
        try {
            return maps[idx].get(key);
        } finally {
            locks[idx].readLock().unlock();
        }
    }

    public void put(K key, V value) {
        int idx = stripeIndex(key);
        locks[idx].writeLock().lock(); // exclusive per stripe
        try {
            maps[idx].put(key, value);
        } finally {
            locks[idx].writeLock().unlock();
        }
    }
    // Throughput: approximately STRIPE_COUNT × single-lock throughput
}

// In practice: just use ConcurrentHashMap — it does this internally (Java 8+: per-bucket CAS)
// But knowing WHY it's fast is what separates senior engineers
```

**Interview signal**: "ConcurrentHashMap uses per-bucket CAS operations in Java 8+, which is effectively lock striping at the finest granularity. For custom data structures with the same pattern, I'd use explicit striped locks."

---

## 2. Optimistic Locking — High-Read, Low-Conflict Scenarios

**When**: Many reads, few writes, conflicts are rare. Pessimistic locking wastes time acquiring locks that rarely conflict.

```java
// Optimistic locking with version field
public class InventoryItem {
    private final String skuId;
    private volatile int quantity;
    private volatile long version; // monotonically increasing

    // Optimistic read — no lock
    public int getQuantity() { return quantity; }
    public long getVersion() { return version; }

    // Optimistic write — CAS semantics
    public synchronized boolean decrementIfAvailable(int amount, long expectedVersion) {
        if (version != expectedVersion) return false; // someone modified it — retry
        if (quantity < amount) return false;          // not enough stock
        quantity -= amount;
        version++;
        return true;
    }
}

// Service using optimistic locking with retry
public class InventoryService {
    public boolean reserve(String skuId, int amount) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            InventoryItem item = repository.findBySku(skuId);
            long version = item.getVersion();

            if (item.getQuantity() < amount) return false; // genuinely not enough

            boolean success = item.decrementIfAvailable(amount, version);
            if (success) {
                repository.save(item);
                return true;
            }
            // Version mismatch — another thread updated between read and write
            // Small backoff before retry
            if (attempt < maxRetries - 1) Thread.sleep(10 * (attempt + 1));
        }
        throw new ConcurrentModificationException("Failed after " + maxRetries + " attempts");
    }
}

// DB-level optimistic locking (what you'd actually use in production)
// SQL: UPDATE inventory SET quantity = quantity - ?, version = version + 1
//      WHERE sku_id = ? AND version = ? AND quantity >= ?
// Rows affected = 0 → optimistic lock failed → retry
```

---

## 3. Custom Thread Pool — Understanding What ExecutorService Does

```java
// Know this to answer "how would you build a thread pool from scratch?"
public class CustomThreadPool {
    private final BlockingQueue<Runnable> taskQueue;
    private final List<WorkerThread> workers;
    private volatile boolean isShutdown = false;

    public CustomThreadPool(int corePoolSize, int queueCapacity) {
        this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workers = new ArrayList<>(corePoolSize);

        for (int i = 0; i < corePoolSize; i++) {
            WorkerThread worker = new WorkerThread("pool-worker-" + i);
            workers.add(worker);
            worker.start();
        }
    }

    public void submit(Runnable task) {
        if (isShutdown) throw new RejectedExecutionException("Pool is shutdown");
        try {
            taskQueue.put(task); // blocks if queue full — backpressure
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean trySubmit(Runnable task) {
        if (isShutdown) return false;
        return taskQueue.offer(task); // non-blocking, returns false if full
    }

    public void shutdown() {
        isShutdown = true;
        workers.forEach(WorkerThread::interrupt);
    }

    public void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        for (WorkerThread worker : workers) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;
            worker.join(remaining);
        }
    }

    private class WorkerThread extends Thread {
        WorkerThread(String name) { super(name); setDaemon(false); }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        try {
                            task.run();
                        } catch (Exception e) {
                            // Log but don't crash worker thread
                            System.err.println("Task failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
```

**Real ExecutorService options and when to use each:**

```java
// Fixed thread pool — stable workload, known concurrency
ExecutorService fixed = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

// Cached thread pool — bursty, short-lived tasks (WARNING: unbounded — can OOM)
ExecutorService cached = Executors.newCachedThreadPool();

// Single thread — sequential processing with async dispatch
ExecutorService single = Executors.newSingleThreadExecutor();

// Scheduled — periodic or delayed tasks
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(5);
scheduled.scheduleAtFixedRate(cleanupTask, 0, 30, TimeUnit.SECONDS);
scheduled.schedule(expiryTask, 10, TimeUnit.MINUTES); // one-time delayed

// Work stealing pool (Java 8+) — CPU-bound tasks with fork/join
ExecutorService workStealing = Executors.newWorkStealingPool();
// Uses ForkJoinPool internally — idle threads steal from others' queues

// ALWAYS prefer named threads in production
ThreadFactory namedFactory = new ThreadFactory() {
    private final AtomicInteger count = new AtomicInteger(0);
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "order-processor-" + count.incrementAndGet());
        t.setDaemon(false);
        return t;
    }
};
ExecutorService named = Executors.newFixedThreadPool(10, namedFactory);
```

---

## 4. CountDownLatch vs CyclicBarrier vs Phaser

```java
// CountDownLatch: ONE-TIME gate — wait until N events happen
// Use: parallel initialization, waiting for N services to be ready

CountDownLatch startingGate = new CountDownLatch(1); // all start together
CountDownLatch doneLatch = new CountDownLatch(10);   // wait for all to finish

for (int i = 0; i < 10; i++) {
    executor.submit(() -> {
        try {
            startingGate.await(); // all workers wait here
            doWork();
            doneLatch.countDown(); // signal completion
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
}
startingGate.countDown(); // release all workers simultaneously
doneLatch.await(); // main thread waits for all workers
// CountDownLatch cannot be reset — use CyclicBarrier for reuse

// ─────────────────────────────────────────────────

// CyclicBarrier: REUSABLE gate — all threads wait at a point, then proceed together
// Use: iterative algorithms where each phase needs all threads to sync

CyclicBarrier barrier = new CyclicBarrier(numThreads, () -> {
    System.out.println("Phase complete — all threads ready for next phase");
    aggregateResults(); // runs after all threads reach barrier
});

// Each worker thread:
for (int phase = 0; phase < totalPhases; phase++) {
    processPhase(phase);
    barrier.await(); // wait for all threads to finish this phase
    // After await: all threads proceed to next phase together
}

// ─────────────────────────────────────────────────

// Phaser: FLEXIBLE — variable party count, supports phases, registration/deregistration
// Use: dynamic task graphs, complex multi-phase workflows

Phaser phaser = new Phaser(1); // 1 = main thread registered

for (Task task : tasks) {
    phaser.register(); // register each task
    executor.submit(() -> {
        try {
            task.execute();
        } finally {
            phaser.arriveAndDeregister(); // deregister when done
        }
    });
}

phaser.arriveAndAwaitAdvance(); // main thread waits for all registered parties
```

---

## 5. Disruptor Pattern — High-Performance Alternative to BlockingQueue

> Used by LMAX Exchange for 6M+ transactions/second. Know the concept — shows you're aware of trading system patterns.

```java
// Problem with BlockingQueue:
// 1. Lock contention — producers and consumers share locks
// 2. GC pressure — objects allocated per event
// 3. False sharing — head/tail pointers on same cache line

// Disruptor solution:
// 1. Pre-allocated ring buffer — no GC, events are reused
// 2. Single producer (lock-free CAS) or multi-producer
// 3. Wait strategies instead of blocking
// 4. Sequence numbers instead of head/tail pointers

// Conceptual model (implementation uses LMAX Disruptor library):
/*
  Ring Buffer (pre-allocated):
  ┌───┬───┬───┬───┬───┬───┬───┬───┐
  │ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │
  └───┴───┴───┴───┴───┴───┴───┴───┘
   Producer writes to next sequence
   Consumer reads up to producer's published sequence
   No locks — just sequence comparisons
*/

// In practice, for LLD interviews: use this as a talking point
// "For extremely high throughput (trading systems), I'd consider a Disruptor-based
//  approach over BlockingQueue — it eliminates lock contention and GC pressure
//  by using a pre-allocated ring buffer with CAS sequence updates."
```

---

## 6. Actor Model Basics

> Relevant when interviewer asks about concurrent message-passing systems.

```java
// Actor: unit of computation that:
// 1. Has its own mailbox (message queue)
// 2. Processes one message at a time (no shared state, no locks needed)
// 3. Can create other actors, send messages, change behavior

// Simplified Actor implementation concept:
public abstract class Actor<T> {
    private final BlockingQueue<T> mailbox = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Actor() {
        executor.submit(this::processLoop);
    }

    public void send(T message) {
        mailbox.offer(message); // non-blocking — fire and forget
    }

    private void processLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                T message = mailbox.take();
                receive(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected abstract void receive(T message);
}

// Usage example: order processing actors
public class OrderActor extends Actor<OrderMessage> {
    protected void receive(OrderMessage msg) {
        switch (msg) {
            case PlaceOrderMessage m -> processOrder(m.order());
            case CancelOrderMessage m -> cancelOrder(m.orderId());
            case QueryStatusMessage m -> m.replyTo().send(getStatus(m.orderId()));
        }
    }
}

// No shared mutable state = no locks needed
// Actors communicate only via messages
// In Java: Akka, Vert.x implement this properly
```

---

## 7. Reactive Streams — Backpressure Handling

```java
// Problem: fast producer + slow consumer = memory overflow
// Solution: backpressure — consumer controls how much it receives

// Java 9+ Flow API (Reactive Streams)
public class OrderProcessor implements Flow.Subscriber<Order> {
    private Flow.Subscription subscription;
    private static final int BATCH_SIZE = 10;

    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(BATCH_SIZE); // request only 10 initially
    }

    public void onNext(Order order) {
        processOrder(order);
        // After processing each batch, request more
        // This is backpressure — consumer controls the rate
        if (shouldRequestMore()) subscription.request(BATCH_SIZE);
    }

    public void onError(Throwable throwable) { log.error("Stream error", throwable); }
    public void onComplete() { log.info("Order stream complete"); }
}

// Key insight for interviews:
// "If the downstream consumer can't keep up, instead of buffering infinitely,
//  I'd use reactive streams with backpressure — the consumer signals how many
//  events it can handle. This prevents OOM and provides flow control."
```

---

## 8. Thread Safety Patterns Summary

```java
// Pattern 1: Immutability — safest, zero synchronization needed
public final class Money {  // final class
    private final BigDecimal amount;  // final fields
    private final Currency currency;
    // No setters. All operations return new instance.
    public Money add(Money other) { return new Money(amount.add(other.amount), currency); }
}

// Pattern 2: Thread-local — each thread has its own copy
private final ThreadLocal<SimpleDateFormat> dateFormat =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
// SimpleDateFormat is not thread-safe — ThreadLocal gives each thread its own instance

// Pattern 3: Confinement — object never shared between threads
// Each request creates its own OrderContext — never passed between threads
// If object stays within one thread's stack, no synchronization needed

// Pattern 4: Synchronization — explicit locking when sharing is unavoidable
// Use: synchronized, ReentrantLock, ReadWriteLock
// Minimize lock scope, prefer finer-grained locks

// Pattern 5: Lock-free — CAS operations via Atomic classes
AtomicLong counter = new AtomicLong(0);
AtomicReference<Node> head = new AtomicReference<>(null);
// CAS: compareAndSet(expected, newValue) — atomic without locks

// Thread safety decision tree:
// Can it be immutable? → Make it immutable (best)
// Is it thread-local? → ThreadLocal or confinement
// Read-heavy? → ReadWriteLock or CopyOnWriteArrayList
// Simple counter/flag? → AtomicInteger/AtomicBoolean
// Complex state? → synchronized or ReentrantLock
// High throughput map? → ConcurrentHashMap
// Producer-consumer? → BlockingQueue
```

---

## Interview Script: Addressing Concurrency Proactively

At the 15-minute mark, before the interviewer asks, say:

> "Before I move on — let me address concurrency. In the `ParkingSpot.occupy()` method, two threads could both check availability and both succeed, causing double-booking. I'd synchronize that method, or better, use an `AtomicBoolean` with `compareAndSet` for lock-free occupation.
>
> For the spot finder — it reads the list of spots — I'd use `ConcurrentHashMap<SpotId, SpotStatus>` for thread-safe status tracking without explicit locking.
>
> The ticket counter would use `AtomicLong` for ID generation — that's lock-free and correct.
>
> Should I code any of these in detail, or continue with the business logic?"

This one paragraph earns you a "senior" rating from most interviewers.
