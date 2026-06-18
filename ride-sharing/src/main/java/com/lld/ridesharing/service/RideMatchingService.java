package com.lld.ridesharing.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.lld.ridesharing.enums.DriverStatus;
import com.lld.ridesharing.enums.RideStatus;
import com.lld.ridesharing.model.Driver;
import com.lld.ridesharing.model.Location;
import com.lld.ridesharing.model.Ride;
import com.lld.ridesharing.model.Rider;

public class RideMatchingService {

  private static final double BASE_FARE = 50.0;
  private static final double RATE_PER_KM = 10.0;

  private final List<Driver> drivers = new CopyOnWriteArrayList<>();
  private final Map<String, Ride> rides = new ConcurrentHashMap<>();

  public void registerDriver(Driver driver) {
    drivers.add(driver);
  }

  public Ride requestRide(Rider rider, Location pickupLocation, Location dropLocation) {
    Driver driver = claimNearestAvailableDriver(pickupLocation);
    Ride ride = new Ride(rider, pickupLocation, dropLocation);
    ride.setDriver(driver);
    ride.setStatus(RideStatus.ACCEPTED);
    rides.put(ride.getRideId(), ride);
    return ride;
  }

  public void startRide(String rideId) {
    Ride ride = getRide(rideId);
    synchronized (ride) {
      if (ride.getStatus() != RideStatus.ACCEPTED) {
        throw new IllegalStateException("Ride cannot be started from status: " + ride.getStatus());
      }
      ride.setStatus(RideStatus.ONGOING);
    }
  }

  public Ride completeRide(String rideId) {
    Ride ride = getRide(rideId);
    synchronized (ride) {
      if (ride.getStatus() != RideStatus.ONGOING) {
        throw new IllegalStateException("Ride cannot be completed from status: " + ride.getStatus());
      }
      ride.setFare(calculateFare(ride));
      ride.setCompletedAt(System.currentTimeMillis());
      ride.setStatus(RideStatus.COMPLETED);
    }
    releaseDriver(ride.getDriver());
    return ride;
  }

  public void cancelRide(String rideId) {
    Ride ride = getRide(rideId);
    synchronized (ride) {
      if (ride.getStatus() != RideStatus.ACCEPTED) {
        throw new IllegalStateException("Ride cannot be cancelled from status: " + ride.getStatus());
      }
      ride.setStatus(RideStatus.CANCELLED);
    }
    releaseDriver(ride.getDriver());
  }

  public Ride getRide(String rideId) {
    Ride ride = rides.get(rideId);
    if (ride == null) {
      throw new IllegalArgumentException("Ride not found: " + rideId);
    }
    return ride;
  }

  // Picks the closest AVAILABLE driver and claims them in one locked step so two
  // riders racing for the same nearest driver can't both win.
  private Driver claimNearestAvailableDriver(Location pickup) {
    List<Driver> candidates = drivers.stream()
        .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
        .sorted((d1, d2) -> Double.compare(
            d1.getCurrentLocation().distanceTo(pickup),
            d2.getCurrentLocation().distanceTo(pickup)))
        .collect(Collectors.toList());

    for (Driver driver : candidates) {
      synchronized (driver) {
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
          driver.setStatus(DriverStatus.BUSY);
          return driver;
        }
      }
    }
    throw new IllegalStateException("No driver was found");
  }

  private void releaseDriver(Driver driver) {
    synchronized (driver) {
      driver.setStatus(DriverStatus.AVAILABLE);
    }
  }

  private double calculateFare(Ride ride) {
    double distance = ride.getPickupLocation().distanceTo(ride.getDropLocation());
    return BASE_FARE + RATE_PER_KM * distance;
  }
}
