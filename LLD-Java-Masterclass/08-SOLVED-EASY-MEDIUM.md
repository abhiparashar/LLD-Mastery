# 08 — Solved Problems: Easy & Medium

---

## EASY: Parking Lot

**Asked at**: Google, Amazon, Microsoft, Uber, Flipkart — literally everywhere.

### Requirements
- Multiple floors, multiple spot types (Motorcycle, Car, Bus)
- Park vehicle, generate ticket
- Exit, calculate fee, vacate spot
- Thread-safe (multiple entry/exit gates)

### Design Decisions
- `ParkingSpot.occupy()` is synchronized — prevents double-booking
- `PricingStrategy` interface — easily add new pricing models
- `SpotFinder` strategy — nearest, random, or floor-priority

### Key Classes
```java
enum VehicleType { MOTORCYCLE, CAR, BUS }
enum SpotStatus { AVAILABLE, OCCUPIED }

class Vehicle { String plate; VehicleType type; }
class ParkingSpot { String id; int floor; VehicleType type; SpotStatus status; }
class Ticket { String id; Vehicle vehicle; ParkingSpot spot; Instant entryTime; }

interface PricingStrategy { Money calculateFee(Duration d, VehicleType type); }
interface SpotFinder { Optional<ParkingSpot> find(VehicleType type); }

class ParkingLotService {
    Ticket park(Vehicle v);   // find spot → occupy → issue ticket
    Payment exit(String ticketId); // find ticket → calculate fee → vacate spot
}
```

### Traps & Follow-ups
- **"Two threads park simultaneously, same last spot?"** → `spot.occupy()` is synchronized, one returns false
- **"Add EV charging spots?"** → New `SpotType.EV_CHARGING`, new `EVSpotFinder`
- **"Add reservations?"** → `RESERVED` status, `ReservationService`, time-based expiry
- **"Multiple lot locations?"** → `ParkingLotRegistry`, route by GPS proximity

---

## EASY: Library Management System

**Asked at**: Amazon, Infosys, TCS

### Requirements
- Books, members, borrowing
- Search books by title/author/ISBN
- Borrow, return, reserve
- Fine for late return

### Key Classes
```java
class Book { String isbn; String title; String author; int totalCopies; int availableCopies; }
class Member { String id; String name; List<Borrowing> activeBorrowings; }
class Borrowing { Book book; Member member; LocalDate dueDate; LocalDate returnDate; }

interface BookSearchStrategy { List<Book> search(String query); }
class TitleSearch implements BookSearchStrategy { }
class AuthorSearch implements BookSearchStrategy { }
class ISBNSearch implements BookSearchStrategy { }

class LibraryService {
    Borrowing borrowBook(String memberId, String isbn);
    Fine returnBook(String borrowingId);
    Reservation reserveBook(String memberId, String isbn);
}
```

### Traps & Follow-ups
- **"Multiple copies of same book?"** → `availableCopies` counter, decrement on borrow
- **"Book is out, member wants to reserve?"** → Observer pattern: when returned, notify reserving members
- **"Calculate fine?"** → `FineCalculator` strategy, can be flat or progressive
- **"Membership types (Student, Faculty)?"** → Different borrow limits and fine rates

---

## MEDIUM: Elevator System

**Asked at**: Google, Amazon, Uber, Microsoft

### Requirements
- Multiple elevators, multiple floors
- Request elevator from floor (external), select floor inside (internal)
- Scheduling algorithm (SCAN/LOOK, nearest elevator)
- Direction awareness (UP/DOWN)

### Design Decisions
- State pattern for elevator state: IDLE, MOVING_UP, MOVING_DOWN, MAINTENANCE
- Strategy for scheduling: NearestElevator, SCAN algorithm
- Observer: ElevatorController notifies elevators of new requests

### Key Classes
```java
enum Direction { UP, DOWN, IDLE }
enum ElevatorState { IDLE, MOVING, DOOR_OPEN, MAINTENANCE }

class ElevatorRequest { 
    int sourceFloor; 
    int destinationFloor; // null for external request
    Direction direction; 
}

interface ElevatorScheduler {
    Elevator selectElevator(List<Elevator> elevators, ElevatorRequest request);
}

class NearestElevatorScheduler implements ElevatorScheduler { }
class SCANScheduler implements ElevatorScheduler { } // like disk SCAN algorithm

class Elevator {
    int id;
    int currentFloor;
    Direction direction;
    ElevatorState state;
    PriorityQueue<Integer> upQueue;   // floors to serve going up
    PriorityQueue<Integer> downQueue; // floors to serve going down
    
    void addRequest(int floor);
    void move(); // advance one floor toward next destination
    void openDoor();
    void closeDoor();
}

class ElevatorController {
    List<Elevator> elevators;
    ElevatorScheduler scheduler;
    
    void requestElevator(int floor, Direction direction);
    void requestFloor(int elevatorId, int floor);
}
```

### SCAN Algorithm (Most Asked Follow-up)
```
Elevator moving UP:
  - Serve all floors in upQueue in ascending order
  - At top, switch to DOWN, serve downQueue in descending order
  - Like a disk read head — sweeps back and forth

Nearest Elevator:
  - Score = |currentFloor - requestFloor|
  - Bonus if moving in same direction toward request
  - Select elevator with lowest score
```

### Traps & Follow-ups
- **"What if elevator is in maintenance?"** → State pattern, MAINTENANCE state rejects requests
- **"Peak hours — all elevators going up?"** → SCAN ensures fairness, no starvation
- **"Emergency floor override?"** → `emergencyRequest()` clears queue, moves directly
- **"VIP floor access?"** → Protection Proxy on `Elevator.addRequest()`, checks permissions

---

## MEDIUM: Chess

**Asked at**: Google, Amazon, Meta, Microsoft

### Requirements
- Two players, 8x8 board
- All piece movements
- Check, checkmate detection
- Turn management

### Design Decisions
- Piece hierarchy: `Piece` (abstract) → `King`, `Queen`, `Rook`, `Bishop`, `Knight`, `Pawn`
- Each piece knows its valid moves
- `MoveValidator` checks for check conditions
- Strategy for AI moves (if asked)

### Key Classes
```java
enum Color { WHITE, BLACK }
enum PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

record Position(int row, int col) {
    boolean isValid() { return row >= 0 && row < 8 && col >= 0 && col < 8; }
}

abstract class Piece {
    Color color;
    Position position;
    abstract List<Position> getValidMoves(Board board);
    boolean isValidMove(Position target, Board board);
}

class Knight extends Piece {
    List<Position> getValidMoves(Board board) {
        // L-shaped: (±1,±2), (±2,±1)
        int[][] deltas = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        return Arrays.stream(deltas)
            .map(d -> new Position(position.row()+d[0], position.col()+d[1]))
            .filter(Position::isValid)
            .filter(p -> board.isEmpty(p) || board.getPiece(p).color() != this.color)
            .collect(toList());
    }
}

class Board {
    Piece[][] grid = new Piece[8][8];
    Optional<Piece> getPiece(Position p);
    boolean isEmpty(Position p);
    void movePiece(Position from, Position to);
}

class Game {
    Board board;
    Player currentPlayer;
    MoveValidator validator;
    
    boolean makeMove(Position from, Position to);
    boolean isCheck(Color color);
    boolean isCheckmate(Color color);
    boolean isStalemate(Color color);
}

class MoveValidator {
    boolean isValidMove(Piece piece, Position target, Board board);
    boolean wouldLeaveKingInCheck(Piece piece, Position target, Board board);
    List<Position> getValidMovesForPlayer(Color color, Board board);
}
```

### Traps & Follow-ups
- **"How do you detect check?"** → After any move, see if opponent's King is in the valid moves of any of your pieces
- **"How do you detect checkmate?"** → King is in check AND no valid move for any piece removes check
- **"En passant?"** → Track last pawn move, allow capture on next turn only
- **"Castling?"** → King and Rook haven't moved, no pieces between, King not passing through check
- **"Pawn promotion?"** → Pawn reaches last rank → replace with Queen/Rook/Bishop/Knight

---

## MEDIUM: BookMyShow / Movie Ticket Booking

**Asked at**: Amazon, Flipkart, Swiggy, Paytm

### Requirements
- Movies, theatres, shows, seats
- Search by city/movie/date
- Book seats (multiple), payment
- Cancellation

### Critical Design: Concurrent Seat Booking

```java
enum SeatStatus { AVAILABLE, LOCKED, BOOKED, CANCELLED }
enum SeatType { REGULAR, PREMIUM, RECLINER }

class Seat {
    String seatId;
    SeatType type;
    volatile SeatStatus status; // volatile for visibility
    String lockedByBookingId;
    Instant lockExpiry;
}

class Show {
    Movie movie;
    Theatre theatre;
    LocalDateTime showTime;
    Map<String, Seat> seats;
    
    // Optimistic locking approach
    synchronized List<Seat> lockSeats(List<String> seatIds, String bookingId, Duration lockDuration) {
        // Validate all requested seats are available
        List<Seat> requestedSeats = seatIds.stream()
            .map(seats::get)
            .collect(toList());
        
        boolean allAvailable = requestedSeats.stream()
            .allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
        
        if (!allAvailable) throw new SeatsUnavailableException();
        
        // Lock all seats atomically
        Instant expiry = Instant.now().plus(lockDuration);
        requestedSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.LOCKED);
            seat.setLockedByBookingId(bookingId);
            seat.setLockExpiry(expiry);
        });
        
        return requestedSeats;
    }
    
    synchronized void confirmSeats(String bookingId) {
        seats.values().stream()
            .filter(s -> bookingId.equals(s.getLockedByBookingId()))
            .forEach(s -> s.setStatus(SeatStatus.BOOKED));
    }
    
    synchronized void releaseExpiredLocks() {
        Instant now = Instant.now();
        seats.values().stream()
            .filter(s -> s.getStatus() == SeatStatus.LOCKED)
            .filter(s -> s.getLockExpiry().isBefore(now))
            .forEach(s -> s.setStatus(SeatStatus.AVAILABLE));
    }
}

class BookingService {
    Booking initiateBooking(String userId, String showId, List<String> seatIds) {
        Show show = showRepository.findById(showId);
        String bookingId = generateId();
        List<Seat> lockedSeats = show.lockSeats(seatIds, bookingId, Duration.ofMinutes(10));
        
        Booking booking = new Booking(bookingId, userId, show, lockedSeats, BookingStatus.PAYMENT_PENDING);
        bookingRepository.save(booking);
        return booking;
    }
    
    Booking confirmBooking(String bookingId, PaymentDetails payment) {
        Booking booking = bookingRepository.findById(bookingId);
        PaymentResult result = paymentGateway.process(payment, booking.totalAmount());
        
        if (result.isSuccess()) {
            booking.getShow().confirmSeats(bookingId);
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.getShow().releaseSeats(bookingId);
            booking.setStatus(BookingStatus.FAILED);
        }
        return bookingRepository.save(booking);
    }
}
```

### Traps & Follow-ups
- **"Two users try to book the same seat simultaneously?"** → `lockSeats()` is synchronized, one gets SeatUnavailableException
- **"User books but doesn't pay — seats held forever?"** → 10-minute lock expiry, background job releases expired locks
- **"Seat pricing by type?"** → `PricingStrategy` per `SeatType`
- **"Add dynamic pricing (surge)?"** → Decorator on `PricingStrategy` — `SurgePricingDecorator`
- **"Cancellation policy?"** → Percentage refund based on hours before show
