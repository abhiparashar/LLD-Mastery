# 23 — Mock Interview Transcripts: Splitwise, Elevator, Recovery

> Three full mock transcripts. Read out loud. Internalize the pacing, clarification questions, extension answers. The third shows how to recover when you go blank — the most realistic scenario of all.

---

# MOCK 1: Splitwise (Google / Razorpay)

**Difficulty**: Hard | **Time**: 45 min

---

**Interviewer**: Design Splitwise.

**Candidate**: "Before I start — a few clarifications:
- Split types: equal only, or exact/percentage/shares too?
- Do we need group expenses?
- Debt simplification — minimize transactions to settle?
- Single currency for now?"

**Interviewer**: All split types, groups yes, debt simplification yes, single currency.

**Candidate**: "Got it. Core entities first."

```
User         — id, name, email
Group        — id, name, members
Expense      — id, paidBy, amount, splitStrategy, participants, description
Balance      — net amount between two users (positive = owed to you)
Settlement   — who pays whom how much after simplification
```

"I see three behaviors: add expense, view balances, simplify debts. Let me start with split strategies — this is where Strategy pattern fits cleanly."

```java
public interface SplitStrategy {
    Map<User, Money> split(Money totalAmount, List<User> participants, List<Double> values);
}

public class EqualSplit implements SplitStrategy {
    public Map<User, Money> split(Money total, List<User> participants, List<Double> ignored) {
        Money share = total.divide(participants.size());
        return participants.stream().collect(toMap(u -> u, u -> share));
    }
}

public class PercentSplit implements SplitStrategy {
    public Map<User, Money> split(Money total, List<User> participants, List<Double> percents) {
        if (Math.abs(percents.stream().mapToDouble(d -> d).sum() - 100.0) > 0.001)
            throw new InvalidSplitException("Percentages must sum to 100");
        Map<User, Money> result = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++)
            result.put(participants.get(i), total.multiply(percents.get(i) / 100.0));
        return result;
    }
}

public class ExactSplit implements SplitStrategy {
    public Map<User, Money> split(Money total, List<User> participants, List<Double> amounts) {
        Money sum = amounts.stream().map(a -> Money.of(a, INR)).reduce(Money.zero(INR), Money::add);
        if (!sum.equals(total)) throw new InvalidSplitException("Exact amounts must sum to total");
        Map<User, Money> result = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++)
            result.put(participants.get(i), Money.of(amounts.get(i), INR));
        return result;
    }
}
```

"Now balance calculation and debt simplification."

```java
public class BalanceService {
    public Map<User, Double> calculateNetBalances(List<Expense> expenses) {
        Map<String, Double> balances = new HashMap<>();

        for (Expense expense : expenses) {
            User payer = expense.getPaidBy();
            expense.getOwedAmounts().forEach((debtor, amount) -> {
                if (!debtor.equals(payer)) {
                    balances.merge(payer.getId(),   amount.toDouble(), Double::sum);
                    balances.merge(debtor.getId(), -amount.toDouble(), Double::sum);
                }
            });
        }

        return balances.entrySet().stream()
            .collect(toMap(e -> userRepo.findById(e.getKey()), Map.Entry::getValue));
    }
}

// Debt simplification: greedy Min-Cash-Flow
public class DebtSimplifier {
    public List<Settlement> simplify(Map<User, Double> netBalances) {
        PriorityQueue<Map.Entry<User, Double>> creditors =
            new PriorityQueue<>((a, b) -> Double.compare(b.getValue(), a.getValue()));
        PriorityQueue<Map.Entry<User, Double>> debtors =
            new PriorityQueue<>((a, b) -> Double.compare(a.getValue(), b.getValue()));

        netBalances.forEach((user, bal) -> {
            if (bal >  0.001) creditors.offer(Map.entry(user, bal));
            if (bal < -0.001) debtors.offer(Map.entry(user, bal));
        });

        List<Settlement> settlements = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            var cr = creditors.poll();
            var db = debtors.poll();
            double amount = Math.min(cr.getValue(), -db.getValue());
            settlements.add(new Settlement(db.getKey(), cr.getKey(), Money.of(amount, INR)));

            if (cr.getValue() - amount > 0.001) creditors.offer(Map.entry(cr.getKey(), cr.getValue() - amount));
            if (db.getValue() + amount < -0.001) debtors.offer(Map.entry(db.getKey(), db.getValue() + amount));
        }
        return settlements;
    }
}
```

**Interviewer**: "Floating point precision?"

**Candidate**: "Store all amounts as `long` paise — 1 INR = 100 paise. No doubles in Money internally. EqualSplit divides using integer arithmetic; remainder paise added to first participant. This eliminates floating point drift entirely."

**Interviewer**: "Multi-currency?"

**Candidate**: "Money already carries a Currency field. BalanceService gets a CurrencyConverter strategy — converts all amounts to a base currency before aggregating. Settlements expressed in debtor's preferred currency with live conversion at settlement time."

**Interviewer**: "Thread safety?"

**Candidate**: "Two shared mutable points: expense list per group — CopyOnWriteArrayList since reads dominate. Balance cache — ConcurrentHashMap, invalidated on every expense mutation. Or just compute on-demand for small groups — it's O(n) and fast enough."

---

# MOCK 2: Elevator System (Google / Uber)

**Difficulty**: Medium-Hard | **Time**: 45 min

---

**Interviewer**: Design an elevator system.

**Candidate**: "Clarifications:
- How many elevators, how many floors?
- Scheduling algorithm preference — nearest, SCAN?
- Emergency mode, maintenance mode?
- Weight limit enforcement?"

**Interviewer**: Multiple elevators, nearest-first, maintenance mode yes, skip weight.

**Candidate**: "Core entities, then State for elevator lifecycle, Strategy for scheduling."

```java
public enum Direction { UP, DOWN, IDLE }
public enum ElevatorState { IDLE, MOVING, DOOR_OPEN, MAINTENANCE }

public class Elevator {
    private final String id;
    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private ElevatorState state = ElevatorState.IDLE;

    // SCAN queues: upQueue ascending, downQueue descending
    private final PriorityQueue<Integer> upQueue   = new PriorityQueue<>();
    private final PriorityQueue<Integer> downQueue = new PriorityQueue<>(Comparator.reverseOrder());

    public synchronized void addRequest(int floor) {
        if (state == ElevatorState.MAINTENANCE) return;
        if (floor >= currentFloor) upQueue.offer(floor);
        else downQueue.offer(floor);
    }

    public synchronized void step() {
        if (state == ElevatorState.MAINTENANCE || state == ElevatorState.DOOR_OPEN) return;

        Integer next = getNextFloor();
        if (next == null) { direction = Direction.IDLE; state = ElevatorState.IDLE; return; }

        if (next > currentFloor) { currentFloor++; direction = Direction.UP; state = ElevatorState.MOVING; }
        else if (next < currentFloor) { currentFloor--; direction = Direction.DOWN; state = ElevatorState.MOVING; }

        if (currentFloor == next) {
            (direction == Direction.UP ? upQueue : downQueue).poll();
            openDoor();
        }
    }

    private Integer getNextFloor() {
        if (direction != Direction.DOWN && !upQueue.isEmpty()) return upQueue.peek();
        if (!downQueue.isEmpty()) { direction = Direction.DOWN; return downQueue.peek(); }
        if (!upQueue.isEmpty())   { direction = Direction.UP;   return upQueue.peek(); }
        return null;
    }

    public void openDoor()  { state = ElevatorState.DOOR_OPEN;  System.out.println("Doors open at " + currentFloor); }
    public void closeDoor() { if (state == ElevatorState.DOOR_OPEN) state = ElevatorState.IDLE; }
    public void setMaintenance(boolean m) { state = m ? ElevatorState.MAINTENANCE : ElevatorState.IDLE; }

    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }
    public ElevatorState getState() { return state; }
    public String getId() { return id; }
}

public interface ElevatorScheduler {
    Optional<Elevator> select(List<Elevator> elevators, int sourceFloor, Direction direction);
}

public class NearestElevatorScheduler implements ElevatorScheduler {
    public Optional<Elevator> select(List<Elevator> elevators, int floor, Direction dir) {
        return elevators.stream()
            .filter(e -> e.getState() != ElevatorState.MAINTENANCE)
            .min(Comparator.comparingInt(e -> score(e, floor, dir)));
    }

    private int score(Elevator e, int floor, Direction requested) {
        int dist = Math.abs(e.getCurrentFloor() - floor);
        if (e.getState() == ElevatorState.IDLE) return dist + 10;
        if (e.getDirection() == requested) {
            boolean approaching = (requested == Direction.UP   && e.getCurrentFloor() < floor)
                               || (requested == Direction.DOWN && e.getCurrentFloor() > floor);
            if (approaching) return dist; // best case
        }
        return dist + 20; // wrong direction or moving away
    }
}

public class ElevatorController {
    private final List<Elevator> elevators;
    private final ElevatorScheduler scheduler;

    public void requestElevator(int floor, Direction direction) {
        scheduler.select(elevators, floor, direction)
            .orElseThrow(NoElevatorAvailableException::new)
            .addRequest(floor);
    }

    public void requestFloor(String elevatorId, int floor) {
        elevators.stream().filter(e -> e.getId().equals(elevatorId))
            .findFirst().ifPresent(e -> e.addRequest(floor));
    }

    public void setMaintenance(String elevatorId, boolean maintenance) {
        elevators.stream().filter(e -> e.getId().equals(elevatorId))
            .findFirst().ifPresent(e -> e.setMaintenance(maintenance));
    }
}
```

**Interviewer**: "Two simultaneous requests for same elevator?"

**Candidate**: "addRequest() is synchronized on the elevator instance — the two threads serialize. PriorityQueue operations inside are protected by the same lock. No data corruption possible."

**Interviewer**: "Why SCAN over pure nearest-floor?"

**Candidate**: "Pure nearest can starve far floors — if nearby requests keep arriving, a floor far away waits forever. SCAN guarantees every floor is served in bounded time. It's the same algorithm disk schedulers use — sweep one direction, reverse, sweep back. My up/down queues implement exactly this. Moving up, serve all floors in upQueue ascending. At the peak, drain downQueue descending. Reverse. Repeat."

**Interviewer**: "Add emergency mode — all elevators go to ground?"

**Candidate**: "Add EMERGENCY to ElevatorState. emergencyMode() on controller: clear all queues on all elevators, add floor 1 to every elevator's queue, set direction DOWN. Reject all new external requests until mode is cleared. One new state, one new controller method — existing code untouched."

---

# MOCK 3: Recovery — When You Go Blank

**Problem**: Notification System | **Scenario**: Candidate freezes mid-design

---

**Interviewer**: Design a notification system.

**Candidate**: "Clarifications: email, SMS, push? User channel preferences? Delivery receipts? Scale?"

**Interviewer**: Email, SMS, push — user preferences — receipts yes — millions of users.

**Candidate**: "Let me sketch entities..."

*[5 minutes pass. Candidate has User and Notification on the board but is stuck.]*

**Candidate** *(recovery technique 1 — narrate out loud)*:
"Let me think through this out loud. A notification needs: what to send, to whom, and via which channel. Channel selection depends on user preferences. So I need something that takes a user + notification type and returns the right channels. Let me define that interface first."

```java
public interface NotificationChannel {
    DeliveryResult send(Notification notification, User recipient);
    ChannelType getType();
}

public interface ChannelSelector {
    List<NotificationChannel> selectFor(User user, NotificationType type);
}
```

*[Candidate pauses — unsure how to connect them to delivery]*

**Candidate** *(recovery technique 2 — ask scoping question to buy time)*:
"Quick design question — synchronous or async delivery? A slow SMS provider shouldn't block email delivery."

**Interviewer**: "What would you recommend?"

**Candidate** *(uses answer to regain momentum)*:
"Async — dispatch each channel independently. ExecutorService per channel type, or a shared pool with enough threads. Failures isolated per channel, retries independent."

```java
public class NotificationRouter {
    private final ChannelSelector selector;
    private final ExecutorService executor;
    private final DeliveryRecordRepository deliveryRepo;
    private final MetricsClient metrics;

    public void send(NotificationRequest request) {
        User recipient = userRepo.findById(request.getUserId());
        List<NotificationChannel> channels = selector.selectFor(recipient, request.getType());

        if (channels.isEmpty()) {
            log.warn("No channels available userId={} type={}", recipient.getId(), request.getType());
            return;
        }

        channels.forEach(channel -> executor.submit(() -> deliver(channel, request, recipient)));
    }

    private void deliver(NotificationChannel channel, NotificationRequest req, User recipient) {
        DeliveryRecord record = new DeliveryRecord(req.getId(), channel.getType(), Instant.now());
        try {
            channel.send(toNotification(req), recipient);
            record.markDelivered();
            metrics.increment("notification.delivered", "channel", channel.getType().name());
        } catch (Exception e) {
            record.markFailed(e.getMessage());
            metrics.increment("notification.failed", "channel", channel.getType().name());
            log.error("Delivery failed channel={} userId={}", channel.getType(), recipient.getId(), e);
            retryQueue.enqueue(req, channel.getType(), backoffPolicy.nextDelay(record.getAttempts()));
        } finally {
            deliveryRepo.save(record);
        }
    }
}
```

**Interviewer**: "Add Slack channel?"

**Candidate** *(now fully recovered)*: "SlackChannel implements NotificationChannel. Register in ChannelRegistry. Add SLACK to ChannelType enum. Add Slack option to user preferences. NotificationRouter, ChannelSelector, all existing channels — completely untouched. Open/Closed."

**Interviewer**: "Rate limiting — don't spam users?"

**Candidate**: "RateLimitedChannelSelector wraps UserPreferenceChannelSelector. Checks a RateLimitStore — if user received > N notifications of this type in the last hour, filter that channel out. Store in Redis for distributed deployments. The underlying selector doesn't know about rate limiting."

---

## Recovery Techniques — Memorize These

**Blank on entities**: "Let me name the nouns in the problem. A notification has... a recipient, a message, a channel. Start there."

**Blank on pattern**: "What can change here? If it's an algorithm — Strategy. If something reacts to changes — Observer. Let me define the interface first and see what emerges."

**Unsure about approach**: "I see two options. Option A is simpler but doesn't handle X. Option B handles X but adds complexity. Which should I implement?" *(Interviewer picks — you proceed with confidence)*

**Don't know the answer**: "I haven't implemented that specifically, but reasoning from first principles — [think out loud]. Does that direction sound right?" *(Reasoning > memorization)*

**Caught your own mistake**: "I'm realizing this design has a problem — [name it]. Let me step back and refactor before going deeper. Cleaner to [new approach] because..." *(Self-correction is a green flag)*

---

## The 5 Signals That Get You a Strong Hire

1. Clarified requirements before writing a single line of code
2. Named the pattern AND explained why before using it
3. Addressed thread safety proactively — before being asked
4. When pushed on extensions — showed zero-modification addition
5. Caught their own design flaw and corrected gracefully

## The 5 Signals That Get You a No Hire

1. Started coding before clarifying scope
2. All concrete classes — no interfaces
3. "I'd add a new if-else" when asked about extensions
4. Never mentioned thread safety on shared mutable state
5. Defensive when corrected instead of adapting

---

## Final Words

The top 1% is not about knowing more patterns than everyone else.

It is about:
- **Communication** — narrate your thinking. Silence is the fastest way to fail.
- **Structure** — clarify → entities → interfaces → implement → extend. Every time.
- **Honesty** — "I'm not certain, but here's how I'd reason through it" beats a confident wrong answer.
- **Adaptability** — when the interviewer pivots, pivot with them. The best candidates make pivots feel natural.
- **Taste** — knowing when a pattern is overkill is as important as knowing the pattern.

Master these 23 files. Code every problem from scratch once. Then again without looking. After that — you are unbeatable.
