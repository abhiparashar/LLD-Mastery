# 09 — Solved Problems: Hard

---

## HARD: Splitwise

**Asked at**: Google, Amazon, Razorpay, Groww

### Requirements
- Add expenses between users
- Split equally, unequally, by percentage, by shares
- Show who owes whom
- Debt simplification (minimize transactions)
- Groups

### Key Design

```java
enum SplitType { EQUAL, EXACT, PERCENT, SHARES }

interface SplitStrategy {
    Map<User, Double> split(double totalAmount, List<User> participants, List<Double> values);
}

class EqualSplit implements SplitStrategy {
    public Map<User, Double> split(double total, List<User> users, List<Double> ignored) {
        double share = total / users.size();
        return users.stream().collect(toMap(u -> u, u -> share));
    }
}

class PercentSplit implements SplitStrategy {
    public Map<User, Double> split(double total, List<User> users, List<Double> percents) {
        // percents must sum to 100
        if (percents.stream().mapToDouble(d->d).sum() != 100) throw new InvalidSplitException();
        Map<User,Double> result = new HashMap<>();
        for (int i = 0; i < users.size(); i++) {
            result.put(users.get(i), total * percents.get(i) / 100);
        }
        return result;
    }
}

class Expense {
    String id;
    User paidBy;
    double amount;
    List<User> participants;
    SplitStrategy splitStrategy;
    String description;
    Group group; // optional
    
    Map<User, Double> getOwedAmounts() {
        return splitStrategy.split(amount, participants, splitValues);
    }
}

// Balance calculation
class BalanceService {
    // Returns net balance for each user (positive = owed to them, negative = they owe)
    Map<User, Double> calculateBalances(List<Expense> expenses) {
        Map<User, Double> balances = new HashMap<>();
        
        for (Expense expense : expenses) {
            User payer = expense.getPaidBy();
            Map<User, Double> owed = expense.getOwedAmounts();
            
            owed.forEach((user, amount) -> {
                if (!user.equals(payer)) {
                    // payer gets credited, debtor gets debited
                    balances.merge(payer, amount, Double::sum);
                    balances.merge(user, -amount, Double::sum);
                }
            });
        }
        return balances;
    }
}
```

### Debt Simplification (The Hard Part)

```
Problem: 
  Alice owes Bob $10
  Bob owes Charlie $10
  Charlie owes Alice $10
  → 3 transactions needed (naive)
  → 0 transactions needed (simplified — circular debt)

Algorithm: Min-Cash-Flow (Greedy)
1. Compute net balance for each person
2. Find max creditor (most positive) and max debtor (most negative)
3. Transfer min(|creditor|, |debtor|) from debtor to creditor
4. Repeat until all balances zero

Time complexity: O(n²) transactions, O(n log n) with heap
```

```java
class DebtSimplifier {
    List<Transaction> simplify(Map<User, Double> balances) {
        List<Transaction> transactions = new ArrayList<>();
        PriorityQueue<Map.Entry<User,Double>> creditors = new PriorityQueue<>(
            (a, b) -> Double.compare(b.getValue(), a.getValue())); // max heap
        PriorityQueue<Map.Entry<User,Double>> debtors = new PriorityQueue<>(
            (a, b) -> Double.compare(a.getValue(), b.getValue())); // min heap
        
        balances.forEach((user, balance) -> {
            if (balance > 0.001) creditors.add(Map.entry(user, balance));
            else if (balance < -0.001) debtors.add(Map.entry(user, balance));
        });
        
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            var creditor = creditors.poll();
            var debtor = debtors.poll();
            double amount = Math.min(creditor.getValue(), Math.abs(debtor.getValue()));
            transactions.add(new Transaction(debtor.getKey(), creditor.getKey(), amount));
            
            double remainingCredit = creditor.getValue() - amount;
            double remainingDebt = debtor.getValue() + amount;
            if (remainingCredit > 0.001) creditors.add(Map.entry(creditor.getKey(), remainingCredit));
            if (remainingDebt < -0.001) debtors.add(Map.entry(debtor.getKey(), remainingDebt));
        }
        return transactions;
    }
}
```

### Traps & Follow-ups
- **"How do you handle floating point precision?"** → Use `BigDecimal`, or store paise/cents as `long`
- **"Group expenses?"** → `Group` entity, expenses belong to group, settle within group
- **"Currency conversion?"** → `CurrencyConverter` service, store in base currency
- **"Activity feed?"** → Observer pattern on expense creation

---

## HARD: Rate Limiter

**Asked at**: Google, Meta, Stripe, Amazon, Cloudflare

### Requirements
- Limit requests per user per time window
- Support multiple algorithms
- Distributed (across multiple servers)
- Low latency

### Algorithms

#### 1. Fixed Window Counter
```
Window: 1 minute
Limit: 100 requests/minute
Problem: Burst at boundary — 100 at 0:59 + 100 at 1:01 = 200 in 2 seconds
```

#### 2. Sliding Window Log
```
Store timestamp of each request
Count requests in [now - windowSize, now]
Accurate but memory-heavy (O(n) per user)
```

#### 3. Sliding Window Counter (Recommended)
```
Blend of fixed windows with weighted approximation:
current_count = prev_window_count * (overlap%) + current_window_count
```

#### 4. Token Bucket (Most Elegant)
```
Bucket holds N tokens (capacity)
Refill at rate R tokens/second
Each request consumes 1 token
Allow burst up to N, steady rate R
```

#### 5. Leaky Bucket
```
Requests queue in bucket
Processed at fixed rate (leak rate)
Excess dropped
Smooths burst traffic
```

### Implementation: Token Bucket

```java
public interface RateLimiter {
    boolean allowRequest(String userId);
    boolean allowRequest(String userId, int tokens); // for variable-cost APIs
}

public class TokenBucketRateLimiter implements RateLimiter {
    private final int capacity;         // max tokens
    private final double refillRate;    // tokens per second
    
    // Per-user state
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String userId) {
        TokenBucket bucket = buckets.computeIfAbsent(userId, 
            k -> new TokenBucket(capacity, refillRate));
        return bucket.tryConsume(1);
    }
    
    private static class TokenBucket {
        private double tokens;
        private final double capacity;
        private final double refillRate;
        private long lastRefillTime;
        
        TokenBucket(double capacity, double refillRate) {
            this.tokens = capacity;
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.lastRefillTime = System.currentTimeMillis();
        }
        
        synchronized boolean tryConsume(int requested) {
            refill();
            if (tokens >= requested) {
                tokens -= requested;
                return true;
            }
            return false;
        }
        
        private void refill() {
            long now = System.currentTimeMillis();
            double elapsed = (now - lastRefillTime) / 1000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillRate);
            lastRefillTime = now;
        }
    }
}

// Sliding Window implementation
public class SlidingWindowRateLimiter implements RateLimiter {
    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String userId) {
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        
        Deque<Long> log = requestLogs.computeIfAbsent(userId, k -> new ArrayDeque<>());
        
        synchronized (log) {
            // Remove expired entries
            while (!log.isEmpty() && log.peekFirst() < windowStart) {
                log.pollFirst();
            }
            
            if (log.size() < limit) {
                log.addLast(now);
                return true;
            }
            return false;
        }
    }
}
```

### Distributed Rate Limiting (Redis-based)

```java
// Use Redis INCR + EXPIRE for distributed counter
public class RedisRateLimiter implements RateLimiter {
    private final RedisClient redis;
    private final int limit;
    private final Duration window;
    
    public boolean allowRequest(String userId) {
        String key = "rate:" + userId + ":" + getCurrentWindow();
        
        // Lua script for atomic increment + check
        String luaScript = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;
        
        long count = redis.eval(luaScript, key, String.valueOf(window.getSeconds()));
        return count <= limit;
    }
    
    private String getCurrentWindow() {
        return String.valueOf(System.currentTimeMillis() / window.toMillis());
    }
}
```

### Traps & Follow-ups
- **"Which algorithm is best?"** → Token Bucket for API rate limiting (allows burst). Leaky Bucket for network traffic shaping (smooth). Sliding window for strict accuracy.
- **"Race condition in Token Bucket?"** → `synchronized` on `tryConsume()` per user bucket. ConcurrentHashMap for user→bucket mapping.
- **"Distributed rate limiting across servers?"** → Redis with Lua script (atomic). Or sticky sessions. Or approximate with each server having proportional limit.
- **"Different limits for different users?"** → `RateLimitConfig` per tier (Free: 100/min, Pro: 1000/min, Enterprise: unlimited)

---

## HARD: Stock Exchange Order Matching Engine

**Asked at**: Goldman Sachs, Morgan Stanley, Zerodha, CRED

### Requirements
- Place buy/sell orders (Market, Limit)
- Match orders: highest buy price vs lowest sell price
- Partial fills
- Order book display

### Key Design

```java
enum OrderType { MARKET, LIMIT }
enum OrderSide { BUY, SELL }
enum OrderStatus { OPEN, PARTIALLY_FILLED, FILLED, CANCELLED }

class Order {
    String orderId;
    String stockSymbol;
    OrderSide side;
    OrderType type;
    double price;       // for LIMIT orders
    int quantity;
    int filledQuantity;
    OrderStatus status;
    Instant timestamp;
    
    int getRemainingQuantity() { return quantity - filledQuantity; }
}

class OrderBook {
    String symbol;
    // Buy orders: highest price first (max heap)
    PriorityQueue<Order> buyOrders = new PriorityQueue<>(
        Comparator.comparingDouble(Order::getPrice).reversed()
                  .thenComparing(Order::getTimestamp));
    // Sell orders: lowest price first (min heap)
    PriorityQueue<Order> sellOrders = new PriorityQueue<>(
        Comparator.comparingDouble(Order::getPrice)
                  .thenComparing(Order::getTimestamp));
    
    synchronized void addOrder(Order order) {
        if (order.getSide() == OrderSide.BUY) buyOrders.add(order);
        else sellOrders.add(order);
    }
    
    synchronized List<Trade> match() {
        List<Trade> trades = new ArrayList<>();
        
        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order bestBuy = buyOrders.peek();
            Order bestSell = sellOrders.peek();
            
            // Match condition: best buy price >= best sell price
            if (bestBuy.getPrice() >= bestSell.getPrice()) {
                int matchQty = Math.min(bestBuy.getRemainingQuantity(), 
                                        bestSell.getRemainingQuantity());
                double tradePrice = bestSell.getPrice(); // price of resting order
                
                Trade trade = new Trade(bestBuy, bestSell, matchQty, tradePrice);
                trades.add(trade);
                
                bestBuy.fill(matchQty);
                bestSell.fill(matchQty);
                
                if (bestBuy.getStatus() == OrderStatus.FILLED) buyOrders.poll();
                if (bestSell.getStatus() == OrderStatus.FILLED) sellOrders.poll();
            } else {
                break; // No more matches possible
            }
        }
        return trades;
    }
}
```

### Traps & Follow-ups
- **"Market orders?"** → Match at best available price immediately (no price condition)
- **"Partial fills?"** → `filledQuantity` tracked, order stays in book with reduced qty
- **"Price-time priority?"** → PriorityQueue ordering: price first, then timestamp (earlier gets priority)
- **"Thread safety — multiple order submissions?"** → `OrderBook.match()` and `addOrder()` synchronized
- **"Performance at scale?"** → Lock-free algorithms (CAS), off-heap memory, LMAX Disruptor pattern

---

## HARD: LRU Cache

**Asked at**: Google, Amazon, Facebook — literally every company

### Requirements
- `get(key)` → value or -1
- `put(key, value)` → evict LRU if at capacity
- Both operations O(1)

### Design: HashMap + Doubly Linked List

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0); // dummy head
    private final Node tail = new Node(0, 0); // dummy tail
    
    LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }
    
    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        moveToFront(node); // mark as recently used
        return node.val;
    }
    
    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.val = value;
            moveToFront(node);
        } else {
            if (map.size() == capacity) {
                Node lru = tail.prev; // least recently used is before tail
                removeNode(lru);
                map.remove(lru.key);
            }
            Node newNode = new Node(key, value);
            insertAfterHead(newNode);
            map.put(key, newNode);
        }
    }
    
    private void moveToFront(Node node) { removeNode(node); insertAfterHead(node); }
    
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void insertAfterHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
    
    // Thread-safe version
    public synchronized int getThreadSafe(int key) { return get(key); }
    public synchronized void putThreadSafe(int key, int value) { put(key, value); }
    
    private static class Node {
        int key, val;
        Node prev, next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }
}
```

### Traps
- **"Why dummy head and tail?"** → Eliminates null checks in insertAfterHead/removeNode
- **"Thread safety?"** → Synchronize get and put. For high concurrency, use `LinkedHashMap` with `accessOrder=true` + `synchronizedMap`
- **"LFU instead of LRU?"** → Least Frequently Used. Requires frequency map + frequency buckets (doubly linked list per frequency). `get` and `put` still O(1) with careful design.
- **"Java shortcut?"** → `LinkedHashMap` with `accessOrder=true` and override `removeEldestEntry()`
