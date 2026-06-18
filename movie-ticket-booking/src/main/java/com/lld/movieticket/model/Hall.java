package com.lld.movieticket.model;

import java.util.List;

public class Hall {
  private final int hallId;
  private final List<Seat> seats;

  public Hall(int hallId, List<Seat> seats) {
    if (seats == null || seats.isEmpty()) {
      throw new IllegalArgumentException("Seats cannot be null or empty");
    }
    this.hallId = hallId;
    this.seats = seats;
  }

  public int getHallId() {
    return hallId;
  }

  public List<Seat> getSeats() {
    return seats;
  }
}
