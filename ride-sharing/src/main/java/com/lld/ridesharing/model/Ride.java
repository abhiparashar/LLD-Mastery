package com.lld.ridesharing.model;

import java.util.UUID;

import com.lld.ridesharing.enums.RideStatus;

public class Ride {
  private final String rideId;
  private final Rider rider;
  private Driver driver;
  private final Location pickupLocation;
  private final Location dropLocation;
  private RideStatus status;
  private double fare;
  private final long requestedAt;
  private long completedAt;

  public Ride(Rider rider, Location pickupLocation, Location dropLocation) {
    this.rideId = UUID.randomUUID().toString();
    this.rider = rider;
    this.driver = null;
    this.pickupLocation = pickupLocation;
    this.dropLocation = dropLocation;
    this.status = RideStatus.REQUESTED;
    this.fare = 0.0;
    this.requestedAt = System.currentTimeMillis();
  }

  public String getRideId() {
    return rideId;
  }

  public Rider getRider() {
    return rider;
  }

  public Driver getDriver() {
    return driver;
  }

  public void setDriver(Driver driver) {
    this.driver = driver;
  }

  public Location getPickupLocation() {
    return pickupLocation;
  }

  public Location getDropLocation() {
    return dropLocation;
  }

  public RideStatus getStatus() {
    return status;
  }

  public void setStatus(RideStatus status) {
    this.status = status;
  }

  public double getFare() {
    return fare;
  }

  public void setFare(double fare) {
    this.fare = fare;
  }

  public long getRequestedAt() {
    return requestedAt;
  }

  public long getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(long completedAt) {
    this.completedAt = completedAt;
  }
}
