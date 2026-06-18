package com.lld.movieticket.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Show {
  private final String showId;
  private final Movie movie;
  private final Hall hall;
  private final LocalDateTime startTime;

  public Show(Movie movie, Hall hall, LocalDateTime startTime) {
    this.showId = UUID.randomUUID().toString();
    this.movie = movie;
    this.hall = hall;
    this.startTime = startTime;
  }

  public String getShowId() {
    return showId;
  }

  public Movie getMovie() {
    return movie;
  }

  public Hall getHall() {
    return hall;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }
}
