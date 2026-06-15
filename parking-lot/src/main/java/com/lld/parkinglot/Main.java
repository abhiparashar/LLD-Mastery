package com.lld.parkinglot;

import java.util.List;

import com.lld.parkinglot.enums.VehicleType;
import com.lld.parkinglot.model.Floor;
import com.lld.parkinglot.model.ParkingLot;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.ParkingTicket;
import com.lld.parkinglot.model.Vehicle;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        // Setup: 2 floors, each with 3 spots
        List<ParkingSpot> floor1Spots = List.of(
            new ParkingSpot(VehicleType.BIKE, 1),
            new ParkingSpot(VehicleType.CAR, 1),
            new ParkingSpot(VehicleType.CAR, 1)
        );

        List<ParkingSpot> floor2Spots = List.of(
            new ParkingSpot(VehicleType.CAR, 2),
            new ParkingSpot(VehicleType.TRUCK, 2),
            new ParkingSpot(VehicleType.BIKE, 2)
        );

        List<Floor> floors = List.of(
            new Floor(1, floor1Spots),
            new Floor(2, floor2Spots)
        );

        ParkingLot lot = new ParkingLot("Downtown Parking", floors);

        // Vehicle enters
        Vehicle car = new Vehicle("MH01AB1234", VehicleType.CAR);
        ParkingTicket ticket = lot.parkVehicle(car);

        System.out.println("--- ENTRY ---");
        System.out.println("Ticket ID  : " + ticket.getTicketId());
        System.out.println("Vehicle    : " + ticket.getVehicle().getVehicleNumber());
        System.out.println("Spot ID    : " + ticket.getSpot().getSpotId());
        System.out.println("Floor      : " + ticket.getSpot().getFloorNumber());
        System.out.println("Entry Time : " + ticket.getEntryTime());

        // Simulate 2 seconds of parking
        Thread.sleep(2000);

        // Vehicle exits
        double amount = lot.exitVehicle(ticket.getTicketId());

        System.out.println("\n--- EXIT ---");
        System.out.println("Exit Time  : " + ticket.getExitTime());
        System.out.println("Status     : " + ticket.getStatus());
        System.out.println("Amount Due : ₹" + amount);

        // Edge case: try exiting same ticket twice
        System.out.println("\n--- EDGE CASE: double exit ---");
        try {
            lot.exitVehicle(ticket.getTicketId());
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Edge case: no spot available for TRUCK on floor 1
        System.out.println("\n--- EDGE CASE: park another truck ---");
        Vehicle truck1 = new Vehicle("DL05XY9999", VehicleType.TRUCK);
        Vehicle truck2 = new Vehicle("DL06XY8888", VehicleType.TRUCK);
        lot.parkVehicle(truck1);
        try {
            lot.parkVehicle(truck2);
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }
}
