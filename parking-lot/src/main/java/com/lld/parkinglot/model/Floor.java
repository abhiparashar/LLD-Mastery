package com.lld.parkinglot.model;

import java.util.List;

import com.lld.parkinglot.enums.VehicleType;

public class Floor {

    private final int floorNumber;
    private final List<ParkingSpot> spots;

    public Floor(int floorNumber, List<ParkingSpot> spots) {
        if (spots == null || spots.isEmpty()) {
            throw new IllegalArgumentException("Spots cannot be null or empty");
        }
        this.floorNumber = floorNumber;
        this.spots = spots;
    }

    public ParkingSpot getAvailableSpot(VehicleType vehicleType) {
        for (ParkingSpot spot : spots) {
            if (spot.getSpotType() == vehicleType && spot.isAvailable()) {
                return spot;
            }
        }
        return null;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }
}
