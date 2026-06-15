package com.lld.parkinglot.model;

import java.util.UUID;

import com.lld.parkinglot.enums.VehicleType;

public class ParkingSpot {
  private final String spotId;
  private final VehicleType spotType;
  private final int floorNumber;

  private boolean isAvailable = true;

  public ParkingSpot(VehicleType vehicleType, int floorNumber) {
    if (vehicleType == null) {
      throw new IllegalArgumentException("Vehicle type can not be null");
    }
    this.spotId = UUID.randomUUID().toString();
    this.spotType = vehicleType;
    this.floorNumber = floorNumber;
  }

  public boolean isAvailable() {
    return isAvailable;
  }

  public String getSpotId() {
    return spotId;
  }

  public VehicleType getSpotType() {
    return spotType;
  }

  public int getFloorNumber() {
    return floorNumber;
  }

  public void markOccupied() {
    this.isAvailable = false;
  }

  public void markAvailable() {
    this.isAvailable = true;
  }
}
