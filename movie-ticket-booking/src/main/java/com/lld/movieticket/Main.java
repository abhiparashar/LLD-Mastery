package com.lld.movieticket;

import java.time.LocalDateTime;
import java.util.List;

import com.lld.movieticket.enums.SeatType;
import com.lld.movieticket.model.Hall;
import com.lld.movieticket.model.Movie;
import com.lld.movieticket.model.Seat;
import com.lld.movieticket.model.Show;
import com.lld.movieticket.model.Theatre;
import com.lld.movieticket.model.Ticket;
import com.lld.movieticket.model.User;

public class Main {

  public static void main(String[] args) {

    MovieBookingController controller = new MovieBookingController();

    // Admin sets up the catalog: a movie, a theatre with one hall, and a show
    Movie movie = controller.addMovie("Inception", "Sci-Fi", "English", 148);

    Theatre theatre = new Theatre("PVR Phoenix");
    List<Seat> seats = List.of(
        new Seat("A", 1, SeatType.REGULAR),
        new Seat("A", 2, SeatType.REGULAR),
        new Seat("A", 3, SeatType.PREMIUM));
    Hall hall = controller.addHall(theatre, 1, seats);

    Show show = controller.scheduleShow(movie, hall, LocalDateTime.of(2026, 6, 25, 18, 0));

    System.out.println("--- AVAILABLE SEATS BEFORE BOOKING ---");
    for (Seat seat : controller.getAvailableSeats(show)) {
      System.out.println(seat.getRow() + seat.getSeatNumber() + " (" + seat.getSeatType() + ")");
    }

    // User books a seat
    System.out.println("\n--- ALICE BOOKS SEAT A1 ---");
    User alice = new User("Alice");
    Ticket aliceTicket = controller.bookTicket(alice, show, seats.get(0));
    System.out.println("Ticket ID : " + aliceTicket.getTicketId());
    System.out.println("Status    : " + aliceTicket.getStatus());

    // Edge case: another user tries to book the same seat for the same show
    System.out.println("\n--- EDGE CASE: Bob tries to book the same seat ---");
    User bob = new User("Bob");
    try {
      controller.bookTicket(bob, show, seats.get(0));
    } catch (IllegalStateException e) {
      System.out.println("Caught: " + e.getMessage());
    }

    System.out.println("\n--- AVAILABLE SEATS AFTER ALICE'S BOOKING ---");
    for (Seat seat : controller.getAvailableSeats(show)) {
      System.out.println(seat.getRow() + seat.getSeatNumber() + " (" + seat.getSeatType() + ")");
    }

    // Alice cancels her ticket
    System.out.println("\n--- ALICE CANCELS HER TICKET ---");
    controller.cancelTicket(aliceTicket.getTicketId());
    System.out.println("Status after cancel: " + aliceTicket.getStatus());

    // Now Bob can book the freed-up seat
    System.out.println("\n--- EDGE CASE: Bob books the freed-up seat ---");
    Ticket bobTicket = controller.bookTicket(bob, show, seats.get(0));
    System.out.println("Ticket ID : " + bobTicket.getTicketId());
    System.out.println("Status    : " + bobTicket.getStatus());

    // Edge case: cancelling an already-cancelled ticket should fail
    System.out.println("\n--- EDGE CASE: cancel an already-cancelled ticket ---");
    try {
      controller.cancelTicket(aliceTicket.getTicketId());
    } catch (IllegalStateException e) {
      System.out.println("Caught: " + e.getMessage());
    }
  }
}
