package com.lld.movieticket;

import java.time.LocalDateTime;
import java.util.List;

import com.lld.movieticket.model.Hall;
import com.lld.movieticket.model.Movie;
import com.lld.movieticket.model.Seat;
import com.lld.movieticket.model.Show;
import com.lld.movieticket.model.Theatre;
import com.lld.movieticket.model.Ticket;
import com.lld.movieticket.model.User;
import com.lld.movieticket.service.MovieBookingService;

public class MovieBookingController {

  private final MovieBookingService service;

  public MovieBookingController() {
    this.service = new MovieBookingService();
  }

  public Movie addMovie(String movieName, String genre, String language, int durationMinutes) {
    return service.addMovie(movieName, genre, language, durationMinutes);
  }

  public Hall addHall(Theatre theatre, int hallId, List<Seat> seats) {
    return service.addHall(theatre, hallId, seats);
  }

  public Show scheduleShow(Movie movie, Hall hall, LocalDateTime startTime) {
    return service.scheduleShow(movie, hall, startTime);
  }

  public List<Seat> getAvailableSeats(Show show) {
    return service.getAvailableSeats(show);
  }

  public Ticket bookTicket(User user, Show show, Seat seat) {
    return service.bookTicket(user, show, seat);
  }

  public void cancelTicket(String ticketId) {
    service.cancelTicket(ticketId);
  }
}
