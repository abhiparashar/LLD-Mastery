package com.lld.ridesharing.model;

import java.util.UUID;

import com.lld.ridesharing.enums.DriverStatus;
import com.lld.ridesharing.enums.VehicleType;

public class Driver {
  private final String driverId;
  private Location currentLocation;
  private final int rating;
  private DriverStatus status;
  private final VehicleType vehicleType;

  public Driver(Location currentLocation, int rating, VehicleType vehicleType, DriverStatus status) {
    this.driverId = UUID.randomUUID().toString();
    this.currentLocation = currentLocation;
    this.rating = rating;
    this.status = status;
    this.vehicleType = vehicleType;
  }

  public String getDriverId() {
    return driverId;
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

  public DriverStatus getStatus() {
    return status;
  }

  public void setStatus(DriverStatus status) {
    this.status = status;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }
}
