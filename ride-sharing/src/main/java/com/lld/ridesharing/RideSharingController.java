package com.lld.ridesharing;

import com.lld.ridesharing.model.Driver;
import com.lld.ridesharing.model.Location;
import com.lld.ridesharing.model.Ride;
import com.lld.ridesharing.model.Rider;
import com.lld.ridesharing.service.RideMatchingService;

public class RideSharingController {

  private final RideMatchingService service;

  public RideSharingController() {
    this.service = new RideMatchingService();
  }

  public void registerDriver(Driver driver) {
    service.registerDriver(driver);
  }

  public Ride requestRide(Rider rider, Location pickupLocation, Location dropLocation) {
    return service.requestRide(rider, pickupLocation, dropLocation);
  }

  public void startRide(String rideId) {
    service.startRide(rideId);
  }

  public Ride completeRide(String rideId) {
    return service.completeRide(rideId);
  }

  public void cancelRide(String rideId) {
    service.cancelRide(rideId);
  }

  public Ride getRide(String rideId) {
    return service.getRide(rideId);
  }
}
