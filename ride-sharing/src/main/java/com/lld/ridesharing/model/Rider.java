package com.lld.ridesharing.model;

import java.util.UUID;

public class Rider {
  private final String riderId;
  private final String name;
  private final int rating;

  public Rider(String name, int rating) {
    this.riderId = UUID.randomUUID().toString();
    this.name = name;
    this.rating = rating;
  }

  public String getRiderId() {
    return riderId;
  }

  public String getName() {
    return name;
  }

  public int getRating() {
    return rating;
  }
}
