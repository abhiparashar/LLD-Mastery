package com.lld.movieticket.model;

import java.util.UUID;

import com.lld.movieticket.enums.SeatType;

public class Seat {
  private final String seatId;
  private final String row;
  private final int seatNumber;
  private final SeatType seatType;

  public Seat(String row, int seatNumber, SeatType seatType) {
    this.seatId = UUID.randomUUID().toString();
    this.row = row;
    this.seatNumber = seatNumber;
    this.seatType = seatType;
  }

  public String getSeatId() {
    return seatId;
  }

  public String getRow() {
    return row;
  }

  public int getSeatNumber() {
    return seatNumber;
  }

  public SeatType getSeatType() {
    return seatType;
  }
}
