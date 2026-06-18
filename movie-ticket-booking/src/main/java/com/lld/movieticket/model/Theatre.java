package com.lld.movieticket.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Theatre {
  private final String theatreId;
  private final String theatreName;
  private final List<Hall> halls;

  public Theatre(String theatreName) {
    this.theatreId = UUID.randomUUID().toString();
    this.theatreName = theatreName;
    this.halls = new ArrayList<>();
  }

  public void addHall(Hall hall) {
    halls.add(hall);
  }

  public String getTheatreId() {
    return theatreId;
  }

  public String getTheatreName() {
    return theatreName;
  }

  public List<Hall> getHalls() {
    return halls;
  }
}
