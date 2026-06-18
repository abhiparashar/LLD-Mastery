package com.lld.movieticket.model;

import java.util.UUID;

import com.lld.movieticket.enums.TicketStatus;

public class Ticket {
  private final String ticketId;
  private final User user;
  private final Show show;
  private final Seat seat;
  private TicketStatus status;
  private final long bookedAt;

  public Ticket(User user, Show show, Seat seat) {
    this.ticketId = UUID.randomUUID().toString();
    this.user = user;
    this.show = show;
    this.seat = seat;
    this.status = TicketStatus.BOOKED;
    this.bookedAt = System.currentTimeMillis();
  }

  public String getTicketId() {
    return ticketId;
  }

  public User getUser() {
    return user;
  }

  public Show getShow() {
    return show;
  }

  public Seat getSeat() {
    return seat;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public void setStatus(TicketStatus status) {
    this.status = status;
  }

  public long getBookedAt() {
    return bookedAt;
  }
}
