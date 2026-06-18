package com.lld.movieticket.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.lld.movieticket.model.Hall;
import com.lld.movieticket.model.Movie;
import com.lld.movieticket.model.Seat;
import com.lld.movieticket.model.Show;
import com.lld.movieticket.model.Theatre;

public class MovieBookingService {

  private final List<Movie> movies = new ArrayList<>();
  private final List<Show> shows = new ArrayList<>();

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
}
