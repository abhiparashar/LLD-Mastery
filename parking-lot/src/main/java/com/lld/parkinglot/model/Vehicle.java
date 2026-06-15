package com.lld.parkinglot.model;

import com.lld.parkinglot.enums.VehicleType;

public class Vehicle {

    private final String vehicleNumber;
    private final VehicleType vehicleType;

    public Vehicle(String vehicleNumber, VehicleType vehicleType) {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new IllegalArgumentException("Vehicle number cannot be null or empty");
        }
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type cannot be null");
        }
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}
