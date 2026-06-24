package com.lld.movieticket.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.lld.movieticket.enums.TicketStatus;
import com.lld.movieticket.model.Hall;
import com.lld.movieticket.model.Movie;
import com.lld.movieticket.model.Seat;
import com.lld.movieticket.model.Show;
import com.lld.movieticket.model.Theatre;
import com.lld.movieticket.model.Ticket;
import com.lld.movieticket.model.User;

public class MovieBookingService {

  private final List<Movie> movies = new ArrayList<>();
  private final List<Show> shows = new ArrayList<>();
  private final List<Ticket> tickets = new ArrayList<>();

  public Movie addMovie(String movieName, String genre, String language, int durationMinutes) {
    Movie movie = new Movie(movieName, genre, language, durationMinutes);
    movies.add(movie);
    return movie;
  }

  public Hall addHall(Theatre theatre, int hallId, List<Seat> seats) {
    Hall hall = new Hall(hallId, seats);
    theatre.addHall(hall);
    return hall;
  }

  public Show scheduleShow(Movie movie, Hall hall, LocalDateTime startTime) {
    Show show = new Show(movie, hall, startTime);
    shows.add(show);
    return show;
  }

  public List<Seat> getAvailableSeats(Show show) {
    List<Seat> availableSeats = new ArrayList<>();
    for (Seat seat : show.getHall().getSeats()) {
      if (isSeatAvailable(show, seat)) {
        availableSeats.add(seat);
      }
    }
    return availableSeats;
  }

  public Ticket bookTicket(User user, Show show, Seat seat) {
    if (!isSeatAvailable(show, seat)) {
      throw new IllegalStateException("Seat is already booked for this show");
    }
    Ticket ticket = new Ticket(user, show, seat);
    tickets.add(ticket);
    return ticket;
  }

  public void cancelTicket(String ticketId) {
    Ticket ticket = getTicket(ticketId);
    if (ticket.getStatus() != TicketStatus.BOOKED) {
      throw new IllegalStateException("Ticket cannot be cancelled from status: " + ticket.getStatus());
    }
    ticket.setStatus(TicketStatus.CANCELLED);
  }

  private boolean isSeatAvailable(Show show, Seat seat) {
    for (Ticket ticket : tickets) {
      if (ticket.getStatus() == TicketStatus.CANCELLED) {
        continue;
      }
      if (ticket.getShow().getShowId().equals(show.getShowId())
          && ticket.getSeat().getSeatId().equals(seat.getSeatId())) {
        return false;
      }
    }
    return true;
  }

  private Ticket getTicket(String ticketId) {
    for (Ticket ticket : tickets) {
      if (ticket.getTicketId().equals(ticketId)) {
        return ticket;
      }
    }
    throw new IllegalArgumentException("Ticket not found: " + ticketId);
  }
}
