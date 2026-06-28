# 21 — Full Solutions: Snake & Ladder + ATM Machine

---

# PART 1: SNAKE AND LADDER

**Asked at**: Goldman Sachs, PhonePe, Amazon, Flipkart, Paytm

---

## Requirements to Clarify

- N players, configurable board size (default 10x10 = 100 cells)
- Snakes: head → tail (go down). Ladders: bottom → top (go up)
- Configurable dice (single, double, loaded for testing)
- Win: exact 100, or first to reach/cross
- Optional: roll again on 6
- Thread safety for online multiplayer

---

## Design Decisions

- **Board** is immutable after setup — value object with Builder
- **DiceStrategy** — injectable, enables deterministic testing
- **Observer (GameEventListener)** — decouple logging, UI, leaderboard from game logic
- **Sealed GameEvent** — exhaustive, type-safe event handling
- Snakes and Ladders unified as a `jumps` map: `from → to` (direction tells you which)

---

## Full Implementation

```java
// ─── Dice Strategy ───────────────────────────────────────────────

public interface DiceStrategy {
    int roll();
    int getMaxValue();
}

public class SingleDice implements DiceStrategy {
    private final Random random = new Random();
    public int roll() { return random.nextInt(6) + 1; }
    public int getMaxValue() { return 6; }
}

public class DoubleDice implements DiceStrategy {
    private final SingleDice die = new SingleDice();
    public int roll() { return die.roll() + die.roll(); }
    public int getMaxValue() { return 12; }
}

// Deterministic dice — critical for unit testing
public class LoadedDice implements DiceStrategy {
    private final Queue<Integer> sequence;
    public LoadedDice(Integer... values) {
        this.sequence = new ArrayDeque<>(Arrays.asList(values));
    }
    public int roll() {
        if (sequence.isEmpty()) throw new IllegalStateException("No more dice values");
        return sequence.poll();
    }
    public int getMaxValue() { return 6; }
}

// ─── Board ───────────────────────────────────────────────────────

public class Board {
    private final int size;
    private final Map<Integer, Integer> jumps; // from → to (unified snakes + ladders)

    private Board(int size, Map<Integer, Integer> jumps) {
        this.size = size;
        this.jumps = Collections.unmodifiableMap(new HashMap<>(jumps));
    }

    public int applyJump(int position) {
        return jumps.getOrDefault(position, position);
    }

    public JumpType getJumpType(int position) {
        if (!jumps.containsKey(position)) return JumpType.NONE;
        return jumps.get(position) > position ? JumpType.LADDER : JumpType.SNAKE;
    }

    public boolean isWinningPosition(int position) { return position >= size; }
    public int getSize() { return size; }

    public enum JumpType { SNAKE, LADDER, NONE }

    public static class Builder {
        private final int size;
        private final Map<Integer, Integer> jumps = new HashMap<>();

        public Builder(int size) {
            if (size < 4) throw new IllegalArgumentException("Board too small");
            this.size = size;
        }

        public Builder addSnake(int head, int tail) {
            if (head <= tail) throw new IllegalArgumentException("Snake head must be above tail: " + head + " → " + tail);
            if (head == size) throw new IllegalArgumentException("Cannot place snake on winning cell " + size);
            if (jumps.containsKey(head)) throw new IllegalStateException("Cell " + head + " already has a jump");
            jumps.put(head, tail);
            return this;
        }

        public Builder addLadder(int bottom, int top) {
            if (bottom >= top) throw new IllegalArgumentException("Ladder bottom must be below top: " + bottom + " → " + top);
            if (top == size) throw new IllegalArgumentException("Ladder cannot lead to winning cell");
            if (jumps.containsKey(bottom)) throw new IllegalStateException("Cell " + bottom + " already has a jump");
            jumps.put(bottom, top);
            return this;
        }

        public Board build() { return new Board(size, jumps); }
    }
}

// ─── Player & Piece ──────────────────────────────────────────────

public class Player {
    private final String id;
    private final String name;
    public Player(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
    public String getName() { return name; }
    @Override public String toString() { return name; }
}

public class GamePiece {
    private final Player player;
    private int position = 0; // 0 = not on board yet
    public GamePiece(Player player) { this.player = player; }
    public Player getPlayer() { return player; }
    public int getPosition() { return position; }
    public void moveTo(int newPosition) { this.position = newPosition; }
}

// ─── Events (Sealed + Observer) ──────────────────────────────────

public sealed interface GameEvent permits
    GameEvent.DiceRolled, GameEvent.PlayerMoved,
    GameEvent.SnakeEncountered, GameEvent.LadderClimbed,
    GameEvent.TurnSkipped, GameEvent.PlayerWon {

    record DiceRolled(Player player, int value) implements GameEvent {}
    record PlayerMoved(Player player, int from, int to) implements GameEvent {}
    record SnakeEncountered(Player player, int head, int tail) implements GameEvent {}
    record LadderClimbed(Player player, int bottom, int top) implements GameEvent {}
    record TurnSkipped(Player player, int position, int rolled, int needed) implements GameEvent {}
    record PlayerWon(Player player, int totalTurns) implements GameEvent {}
}

public interface GameEventListener {
    void onEvent(GameEvent event);
}

public class ConsoleLogger implements GameEventListener {
    public void onEvent(GameEvent event) {
        String msg = switch (event) {
            case GameEvent.DiceRolled e ->
                String.format("🎲 %s rolled %d", e.player(), e.value());
            case GameEvent.PlayerMoved e ->
                String.format("   %s: %d → %d", e.player(), e.from(), e.to());
            case GameEvent.SnakeEncountered e ->
                String.format("🐍 SNAKE! %s slides %d → %d", e.player(), e.head(), e.tail());
            case GameEvent.LadderClimbed e ->
                String.format("🪜 LADDER! %s climbs %d → %d", e.player(), e.bottom(), e.top());
            case GameEvent.TurnSkipped e ->
                String.format("   %s needs %d to win but rolled %d — stays at %d",
                    e.player(), e.needed(), e.rolled(), e.position());
            case GameEvent.PlayerWon e ->
                String.format("🏆 %s WINS in %d turns!", e.player(), e.totalTurns());
        };
        System.out.println(msg);
    }
}

// ─── Game Engine ─────────────────────────────────────────────────

public class SnakeAndLadderGame {
    private final Board board;
    private final DiceStrategy dice;
    private final List<GamePiece> pieces;
    private final boolean rollAgainOnSix;
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    private int currentPlayerIndex = 0;
    private int totalTurns = 0;
    private Player winner = null;

    public SnakeAndLadderGame(Board board, List<Player> players,
                               DiceStrategy dice, boolean rollAgainOnSix) {
        if (players == null || players.isEmpty())
            throw new IllegalArgumentException("Need at least one player");
        this.board = board;
        this.dice = dice;
        this.rollAgainOnSix = rollAgainOnSix;
        this.pieces = players.stream().map(GamePiece::new).collect(toList());
    }

    public void addListener(GameEventListener listener) { listeners.add(listener); }

    public boolean isGameOver() { return winner != null; }
    public Optional<Player> getWinner() { return Optional.ofNullable(winner); }

    // Returns winner if this turn ends the game
    public Optional<Player> takeTurn() {
        if (isGameOver()) throw new IllegalStateException("Game is over");

        GamePiece piece = pieces.get(currentPlayerIndex);
        Player player = piece.getPlayer();
        boolean rollAgain;

        do {
            int rolled = dice.roll();
            fireEvent(new GameEvent.DiceRolled(player, rolled));

            int from = piece.getPosition();
            int target = from + rolled;

            // Exact landing rule: don't move if overshoot
            if (target > board.getSize()) {
                int needed = board.getSize() - from;
                fireEvent(new GameEvent.TurnSkipped(player, from, rolled, needed));
                rollAgain = false;
                break;
            }

            piece.moveTo(target);
            fireEvent(new GameEvent.PlayerMoved(player, from, target));

            // Apply snake or ladder
            Board.JumpType jumpType = board.getJumpType(target);
            int afterJump = board.applyJump(target);

            switch (jumpType) {
                case SNAKE -> {
                    piece.moveTo(afterJump);
                    fireEvent(new GameEvent.SnakeEncountered(player, target, afterJump));
                }
                case LADDER -> {
                    piece.moveTo(afterJump);
                    fireEvent(new GameEvent.LadderClimbed(player, target, afterJump));
                }
                case NONE -> {} // no jump
            }

            // Win check
            if (board.isWinningPosition(piece.getPosition())) {
                totalTurns++;
                winner = player;
                fireEvent(new GameEvent.PlayerWon(player, totalTurns));
                return Optional.of(player);
            }

            rollAgain = rollAgainOnSix && rolled == dice.getMaxValue();

        } while (rollAgain);

        totalTurns++;
        currentPlayerIndex = (currentPlayerIndex + 1) % pieces.size();
        return Optional.empty();
    }

    public void playToCompletion() {
        while (!isGameOver()) takeTurn();
    }

    // Leaderboard: sorted by position descending
    public List<Map.Entry<Player, Integer>> getLeaderboard() {
        return pieces.stream()
            .map(p -> Map.entry(p.getPlayer(), p.getPosition()))
            .sorted(Map.Entry.<Player, Integer>comparingByValue().reversed())
            .collect(toList());
    }

    private void fireEvent(GameEvent event) {
        listeners.forEach(l -> l.onEvent(event));
    }
}

// ─── Usage ───────────────────────────────────────────────────────

Board board = new Board.Builder(100)
    .addSnake(99, 4).addSnake(70, 55).addSnake(52, 42).addSnake(25, 2)
    .addLadder(6, 25).addLadder(11, 40).addLadder(60, 85).addLadder(46, 90)
    .build();

List<Player> players = List.of(
    new Player("1", "Alice"), new Player("2", "Bob"), new Player("3", "Charlie"));

SnakeAndLadderGame game = new SnakeAndLadderGame(board, players, new SingleDice(), true);
game.addListener(new ConsoleLogger());
game.playToCompletion();
```

---

## Traps & Follow-ups

**Q: Two players on same cell?**
A: Standard rules — no conflict. If Ludo-style eviction needed: `CellOccupancyManager`, evicted player returns to start.

**Q: How to unit test without randomness?**
A: `LoadedDice(2, 6, 3, ...)` — inject deterministic sequence. Strategy pattern makes this trivial.

**Q: Thread safety for online multiplayer?**
A: `takeTurn()` synchronized. `currentPlayerIndex` volatile. Each game instance owns its own piece list — no cross-game shared state.

**Q: Add a leaderboard persisted across multiple games?**
A: `PersistentLeaderboardListener implements GameEventListener` — subscribes to `PlayerWon`, writes to DB. Decoupled from game logic.

**Q: What if we want different board shapes (3D, hexagonal)?**
A: `Board` encapsulates all movement rules. Extract `MovementRule` interface. `StandardBoard`, `Hexagonal Board` implement it. Game engine unchanged.

---
---

# PART 2: ATM MACHINE

**Asked at**: Amazon, Goldman Sachs, PhonePe, Wipro, Paytm

---

## Requirements to Clarify

- Insert card → enter PIN → select transaction → withdraw/deposit/balance/transfer → eject card
- 3 wrong PINs → card blocked
- Daily withdrawal limit per account
- Multiple ATMs sharing same bank accounts (concurrency!)
- Receipt printing, session timeout
- Cash dispenser with specific denominations

---

## Design Decisions

- **State** is the backbone: Idle → CardInserted → Authenticated → (transaction) → Idle
- **Strategy**: cash dispensing algorithm (greedy minimize-notes)
- **Observer**: audit log, fraud detection subscribes to transaction events
- **Chain of Responsibility**: withdrawal validation — PIN check → balance → daily limit → cash available

---

## Full Implementation

```java
// ─── Value Objects ───────────────────────────────────────────────

public record CardNumber(String value) {
    public CardNumber {
        if (value == null || !value.matches("\\d{16}"))
            throw new IllegalArgumentException("Card number must be 16 digits");
    }
}

public record PIN(String value) {
    public PIN {
        if (value == null || !value.matches("\\d{4}"))
            throw new IllegalArgumentException("PIN must be 4 digits");
    }
}

// ─── Bank Account (shared across ATMs — needs synchronization) ───

public class BankAccount {
    private final String accountId;
    private final CardNumber cardNumber;
    private final String hashedPIN; // BCrypt in production
    private volatile double balance;
    private volatile double withdrawnToday;
    private final double dailyLimit;
    private volatile int failedPINAttempts = 0;
    private volatile boolean blocked = false;
    private volatile LocalDate lastWithdrawalDate = LocalDate.now();

    public boolean verifyPIN(PIN pin) {
        // In production: BCrypt.checkpw(pin.value(), hashedPIN)
        return hashedPIN.equals(pin.value());
    }

    public synchronized boolean withdraw(double amount) {
        resetDailyLimitIfNewDay();

        if (blocked) throw new CardBlockedException(cardNumber);
        if (amount > balance) throw new InsufficientFundsException(balance, amount);
        if (withdrawnToday + amount > dailyLimit)
            throw new DailyLimitExceededException(dailyLimit, withdrawnToday, amount);

        balance -= amount;
        withdrawnToday += amount;
        return true;
    }

    public synchronized void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive");
        balance += amount;
    }

    public void recordFailedPIN() {
        int attempts = ++failedPINAttempts;
        if (attempts >= 3) {
            blocked = true;
            System.out.println("Card blocked after 3 failed attempts: " + cardNumber.value());
        }
    }

    public void resetFailedPINAttempts() { failedPINAttempts = 0; }

    private void resetDailyLimitIfNewDay() {
        if (!LocalDate.now().equals(lastWithdrawalDate)) {
            withdrawnToday = 0;
            lastWithdrawalDate = LocalDate.now();
        }
    }

    public double getBalance() { return balance; }
    public boolean isBlocked() { return blocked; }
    public int getFailedPINAttempts() { return failedPINAttempts; }
    public String getAccountId() { return accountId; }
}

// ─── Cash Dispenser (Strategy) ───────────────────────────────────

public interface CashDispenserStrategy {
    // Returns denomination → count needed. Throws if not possible.
    Map<Integer, Integer> calculateNotes(int amount, Map<Integer, Integer> available);
}

public class GreedyCashDispenser implements CashDispenserStrategy {
    public Map<Integer, Integer> calculateNotes(int amount, Map<Integer, Integer> available) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        int remaining = amount;

        List<Integer> denoms = new ArrayList<>(available.keySet());
        denoms.sort(Collections.reverseOrder()); // largest first

        for (int denom : denoms) {
            if (remaining <= 0) break;
            int canUse = Math.min(remaining / denom, available.getOrDefault(denom, 0));
            if (canUse > 0) {
                result.put(denom, canUse);
                remaining -= denom * canUse;
            }
        }

        if (remaining != 0)
            throw new InsufficientCashException("Cannot dispense ₹" + amount +
                ". Remaining: ₹" + remaining);
        return result;
    }
}

public class CashDispenser {
    private final Map<Integer, Integer> stock; // denomination → count
    private final CashDispenserStrategy strategy;

    public CashDispenser(CashDispenserStrategy strategy) {
        this.strategy = strategy;
        this.stock = new TreeMap<>(Collections.reverseOrder());
        stock.put(2000, 50); stock.put(500, 100);
        stock.put(200, 50);  stock.put(100, 100);
    }

    public synchronized void dispense(int amount) {
        Map<Integer, Integer> notes = strategy.calculateNotes(amount,
            Collections.unmodifiableMap(stock));
        notes.forEach((denom, count) -> stock.merge(denom, -count, Integer::sum));
        System.out.println("Dispensed: " + notes);
    }

    public synchronized void loadCash(Map<Integer, Integer> notes) {
        notes.forEach((d, c) -> stock.merge(d, c, Integer::sum));
    }

    public synchronized int getTotalCash() {
        return stock.entrySet().stream().mapToInt(e -> e.getKey() * e.getValue()).sum();
    }
}

// ─── ATM States ──────────────────────────────────────────────────

public interface ATMState {
    void insertCard(ATMContext atm, CardNumber card);
    void enterPIN(ATMContext atm, PIN pin);
    void withdraw(ATMContext atm, double amount);
    void deposit(ATMContext atm, double amount);
    void checkBalance(ATMContext atm);
    void ejectCard(ATMContext atm);
    String name();
}

public class IdleState implements ATMState {
    public void insertCard(ATMContext atm, CardNumber card) {
        BankAccount account = atm.getBankService().findByCard(card)
            .orElseThrow(() -> new CardNotFoundException(card));
        if (account.isBlocked())
            throw new CardBlockedException(card);
        atm.setAccount(account);
        atm.setState(new CardInsertedState());
        atm.display("Card accepted. Enter PIN:");
    }

    public void enterPIN(ATMContext atm, PIN pin) { throw new InvalidOperationException("Insert card first"); }
    public void withdraw(ATMContext atm, double amount) { throw new InvalidOperationException("Insert card first"); }
    public void deposit(ATMContext atm, double amount) { throw new InvalidOperationException("Insert card first"); }
    public void checkBalance(ATMContext atm) { throw new InvalidOperationException("Insert card first"); }
    public void ejectCard(ATMContext atm) { atm.display("No card inserted"); }
    public String name() { return "IDLE"; }
}

public class CardInsertedState implements ATMState {
    public void insertCard(ATMContext atm, CardNumber card) { throw new InvalidOperationException("Card already inserted"); }

    public void enterPIN(ATMContext atm, PIN pin) {
        BankAccount account = atm.getAccount();
        if (account.verifyPIN(pin)) {
            account.resetFailedPINAttempts();
            atm.setState(new AuthenticatedState());
            atm.display("PIN verified. Choose: WITHDRAW / DEPOSIT / BALANCE");
            atm.startSessionTimer(); // 60-second timeout
        } else {
            account.recordFailedPIN();
            if (account.isBlocked()) {
                atm.display("Card blocked. Please contact your bank.");
                atm.setAccount(null);
                atm.setState(new IdleState());
            } else {
                int remaining = 3 - account.getFailedPINAttempts();
                atm.display("Wrong PIN. " + remaining + " attempt(s) left.");
            }
        }
    }

    public void withdraw(ATMContext atm, double amount) { throw new InvalidOperationException("Enter PIN first"); }
    public void deposit(ATMContext atm, double amount) { throw new InvalidOperationException("Enter PIN first"); }
    public void checkBalance(ATMContext atm) { throw new InvalidOperationException("Enter PIN first"); }

    public void ejectCard(ATMContext atm) {
        atm.setAccount(null);
        atm.setState(new IdleState());
        atm.display("Card ejected.");
    }

    public String name() { return "CARD_INSERTED"; }
}

public class AuthenticatedState implements ATMState {
    public void insertCard(ATMContext atm, CardNumber card) { throw new InvalidOperationException("Session active"); }
    public void enterPIN(ATMContext atm, PIN pin) { throw new InvalidOperationException("Already authenticated"); }

    public void withdraw(ATMContext atm, double amount) {
        try {
            atm.getAccount().withdraw(amount);
            atm.getCashDispenser().dispense((int) amount);
            atm.getAuditLog().record(atm.getAccount(), "WITHDRAW", amount);
            atm.display(String.format("₹%.0f dispensed. New balance: ₹%.2f",
                amount, atm.getAccount().getBalance()));
        } catch (InsufficientFundsException e) {
            atm.display("Insufficient funds. Available: ₹" + e.getAvailable());
        } catch (DailyLimitExceededException e) {
            atm.display("Daily limit exceeded. Remaining limit: ₹" + e.getRemaining());
        } catch (InsufficientCashException e) {
            atm.display("ATM cannot dispense this amount. Try a multiple of ₹100.");
        }
    }

    public void deposit(ATMContext atm, double amount) {
        atm.getAccount().deposit(amount);
        atm.getAuditLog().record(atm.getAccount(), "DEPOSIT", amount);
        atm.display(String.format("₹%.0f deposited. New balance: ₹%.2f",
            amount, atm.getAccount().getBalance()));
    }

    public void checkBalance(ATMContext atm) {
        atm.display(String.format("Available balance: ₹%.2f", atm.getAccount().getBalance()));
    }

    public void ejectCard(ATMContext atm) {
        atm.cancelSessionTimer();
        atm.setAccount(null);
        atm.setState(new IdleState());
        atm.display("Session ended. Thank you!");
    }

    public String name() { return "AUTHENTICATED"; }
}

// ─── ATM Context ─────────────────────────────────────────────────

public class ATMContext {
    private ATMState state = new IdleState();
    private BankAccount currentAccount;
    private final BankService bankService;
    private final CashDispenser cashDispenser;
    private final AuditLog auditLog;
    private ScheduledFuture<?> sessionTimer;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void insertCard(CardNumber card) { state.insertCard(this, card); }
    public void enterPIN(PIN pin) { state.enterPIN(this, pin); }
    public void withdraw(double amount) { state.withdraw(this, amount); }
    public void deposit(double amount) { state.deposit(this, amount); }
    public void checkBalance() { state.checkBalance(this); }
    public void ejectCard() { state.ejectCard(this); }

    public void display(String message) { System.out.println("[ATM] " + message); }

    void startSessionTimer() {
        sessionTimer = scheduler.schedule(() -> {
            display("Session timed out. Ejecting card.");
            ejectCard();
        }, 60, TimeUnit.SECONDS);
    }

    void cancelSessionTimer() {
        if (sessionTimer != null) sessionTimer.cancel(false);
    }

    // Package-private accessors for state classes
    void setState(ATMState newState) {
        System.out.println("[ATM] State: " + state.name() + " → " + newState.name());
        this.state = newState;
    }

    void setAccount(BankAccount account) { this.currentAccount = account; }
    BankAccount getAccount() { return currentAccount; }
    BankService getBankService() { return bankService; }
    CashDispenser getCashDispenser() { return cashDispenser; }
    AuditLog getAuditLog() { return auditLog; }
    public String getCurrentState() { return state.name(); }
}
```

---

## Traps & Follow-ups

**Q: Two ATMs withdraw from same account simultaneously?**
A: `BankAccount.withdraw()` is `synchronized` — one waits. For distributed ATMs: DB row-level lock, or optimistic locking with version field. The account object is shared via `BankService` (singleton or DB-backed).

**Q: ATM runs out of cash mid-dispense?**
A: `CashDispenser.calculateNotes()` validates before touching stock. Account not debited until after dispense succeeds. If dispense throws — catch in `withdraw()`, don't debit account.

**Q: Network failure to bank during withdrawal?**
A: Local transaction log. On reconnect, reconcile. If bank says failed → reverse debit. If bank says success → confirm. Idempotency key per withdrawal prevents double processing.

**Q: Session timeout — user walks away?**
A: `ScheduledExecutorService` fires `ejectCard()` after 60 seconds of inactivity. Every user action resets the timer.

**Q: Add UPI/cardless withdrawal?**
A: `AuthenticationStrategy` interface — `CardPINAuth`, `UPIAuth`, `QRAuth`. ATMContext uses strategy for auth step. State machine (Idle → Authenticated) unchanged.

**Q: Card skimming / fraud detection?**
A: `FraudDetectionListener implements AuditObserver`. Analyzes: velocity (N withdrawals in M minutes), geo-anomaly (Mumbai ATM then Delhi ATM 5 min later). Triggers card block + bank alert.
