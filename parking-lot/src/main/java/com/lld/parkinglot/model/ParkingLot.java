package com.lld.parkinglot.model;

import java.util.List;

import com.lld.parkinglot.service.ParkingLotService;

public class ParkingLot {

    private final String name;
    private final List<Floor> floors;
    private final ParkingLotService service;

    public ParkingLot(String name, List<Floor> floors) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parking lot name cannot be empty");
        }
        if (floors == null || floors.isEmpty()) {
            throw new IllegalArgumentException("Floors cannot be null or empty");
        }
        this.name = name;
        this.floors = floors;
        this.service = new ParkingLotService(floors);
    }

    public ParkingTicket parkVehicle(Vehicle vehicle) {
        return service.parkVehicle(vehicle);
    }

    public double exitVehicle(String ticketId) {
        return service.exitVehicle(ticketId);
    }

    public String getName() {
        return name;
    }

    public List<Floor> getFloors() {
        return floors;
    }
}
