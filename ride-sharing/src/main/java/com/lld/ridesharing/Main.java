package com.lld.ridesharing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.lld.ridesharing.enums.DriverStatus;
import com.lld.ridesharing.enums.VehicleType;
import com.lld.ridesharing.model.Driver;
import com.lld.ridesharing.model.Location;
import com.lld.ridesharing.model.Ride;
import com.lld.ridesharing.model.Rider;

public class Main {

  public static void main(String[] args) throws InterruptedException {

    RideSharingController controller = new RideSharingController();

    Driver driver1 = new Driver(new Location(12.90, 77.60), 5, VehicleType.CAR, DriverStatus.AVAILABLE);
    Driver driver2 = new Driver(new Location(12.95, 77.65), 4, VehicleType.BIKE, DriverStatus.AVAILABLE);
    Driver driver3 = new Driver(new Location(13.00, 77.70), 5, VehicleType.CAR, DriverStatus.AVAILABLE);

    controller.registerDriver(driver1);
    controller.registerDriver(driver2);
    controller.registerDriver(driver3);

    // Happy path: Alice requests a ride, nearest driver gets matched
    System.out.println("--- ALICE REQUESTS A RIDE ---");
    Rider alice = new Rider("Alice", 5);
    Ride aliceRide = controller.requestRide(alice, new Location(12.91, 77.61), new Location(13.05, 77.75));
    System.out.println("Ride ID : " + aliceRide.getRideId());
    System.out.println("Driver  : " + aliceRide.getDriver().getDriverId());
    System.out.println("Status  : " + aliceRide.getStatus());

    // Ride lifecycle: start, then complete
    System.out.println("\n--- RIDE STARTS AND COMPLETES ---");
    controller.startRide(aliceRide.getRideId());
    System.out.println("Status after start: " + aliceRide.getStatus());

    Ride completedRide = controller.completeRide(aliceRide.getRideId());
    System.out.println("Status after complete : " + completedRide.getStatus());
    System.out.println("Fare                   : " + completedRide.getFare());
    System.out.println("Driver freed up        : " + driver1.getStatus());

    // Edge case: can't cancel a ride that already completed
    System.out.println("\n--- EDGE CASE: cancel a completed ride ---");
    try {
      controller.cancelRide(aliceRide.getRideId());
    } catch (IllegalStateException e) {
      System.out.println("Caught: " + e.getMessage());
    }

    // Edge case: no driver available once every driver is busy
    System.out.println("\n--- EDGE CASE: no driver available ---");
    controller.requestRide(new Rider("Bob", 4), new Location(12.92, 77.62), new Location(13.0, 77.7));
    controller.requestRide(new Rider("Carol", 4), new Location(12.96, 77.66), new Location(13.0, 77.7));
    controller.requestRide(new Rider("Dave", 4), new Location(13.01, 77.71), new Location(13.1, 77.8));
    try {
      controller.requestRide(new Rider("Eve", 5), new Location(12.9, 77.6), new Location(13.0, 77.7));
    } catch (IllegalStateException e) {
      System.out.println("Caught: " + e.getMessage());
    }

    // Concurrency check: many riders race for one driver — exactly one should win
    System.out.println("\n--- CONCURRENCY: race for the same available driver ---");
    runConcurrentRaceDemo();
  }

  private static void runConcurrentRaceDemo() throws InterruptedException {
    RideSharingController raceController = new RideSharingController();
    raceController.registerDriver(new Driver(new Location(0, 0), 5, VehicleType.CAR, DriverStatus.AVAILABLE));

    int riderCount = 20;
    ExecutorService pool = Executors.newFixedThreadPool(riderCount);
    CountDownLatch latch = new CountDownLatch(riderCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < riderCount; i++) {
      Rider rider = new Rider("Rider-" + i, 5);
      pool.submit(() -> {
        try {
          raceController.requestRide(rider, new Location(0, 0), new Location(1, 1));
          successCount.incrementAndGet();
        } catch (IllegalStateException e) {
          // expected for every rider except the one that wins the only driver
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();
    pool.shutdown();
    System.out.println("Successful matches: " + successCount.get() + " out of " + riderCount + " requests (expected 1)");
  }
}
