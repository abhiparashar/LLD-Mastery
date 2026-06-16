package com.lld.ridesharing.model;

import java.util.UUID;

public class Rider {
  private final String riderId;
  private final String name;
  private Location currentLocation;
  private final int rating;

  public Rider(String name, Location currentLocation, int rating) {
    this.riderId = UUID.randomUUID().toString();
    this.name = name;
    this.currentLocation = currentLocation;
    this.rating = rating;
  }

  public String getRiderId() {
    return riderId;
  }

  public String getName() {
    return name;
  }

  public Location getCurrentLocation() {
    return currentLocation;
  }

  public void setCurrentLocation(Location currentLocation) {
    this.currentLocation = currentLocation;
  }

  public int getRating() {
    return rating;
  }
}
