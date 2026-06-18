package com.lld.ridesharing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.lld.ridesharing.model.Location;
import com.lld.ridesharing.enums.DriverStatus;
import com.lld.ridesharing.enums.RideStatus;
import com.lld.ridesharing.model.Driver;
import com.lld.ridesharing.model.Ride;
import com.lld.ridesharing.model.Rider;

public class RideMatchingService {
  List<Driver> drivers = new ArrayList<>();

  // Rquest Ride
  public Ride requestRide(Rider rider, Location pickLocation, Location dropLocation) {
    Optional<Driver> nearestDriver = findNearestAvailableDriver(pickLocation);
    if (nearestDriver.isEmpty()) {
      throw new IllegalStateException("No driver was found");
    }
    Driver driver = nearestDriver.get();
    Ride ride = new Ride(rider, pickLocation, dropLocation);
    ride.setDriver(driver);
    ride.setStatus(RideStatus.ACCEPTED);
    driver.setStatus(DriverStatus.BUSY);
    return ride;
  }

  // Find Driver
  private Optional<Driver> findNearestAvailableDriver(Location pickup) {
    return drivers.stream()
        .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
        .min((d1, d2) -> Double.compare(
            d1.getCurrentLocation().distanceTo(pickup),
            d2.getCurrentLocation().distanceTo(pickup)));
  }
}