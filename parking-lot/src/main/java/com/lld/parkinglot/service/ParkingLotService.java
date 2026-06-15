package com.lld.parkinglot.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lld.parkinglot.enums.TicketStatus;
import com.lld.parkinglot.enums.VehicleType;
import com.lld.parkinglot.model.Floor;
import com.lld.parkinglot.model.ParkingSpot;
import com.lld.parkinglot.model.ParkingTicket;
import com.lld.parkinglot.model.Vehicle;

public class ParkingLotService {

    private final List<Floor> floors;
    private final Map<String, ParkingTicket> activeTickets;

    private static final Map<VehicleType, Double> HOURLY_RATE = Map.of(
        VehicleType.BIKE, 20.0,
        VehicleType.CAR, 50.0,
        VehicleType.TRUCK, 100.0
    );

    public ParkingLotService(List<Floor> floors) {
        if (floors == null || floors.isEmpty()) {
            throw new IllegalArgumentException("Floors cannot be null or empty");
        }
        this.floors = floors;
        this.activeTickets = new HashMap<>();
    }

    // Entry — find spot, mark occupied, issue ticket
    public ParkingTicket parkVehicle(Vehicle vehicle) {
        ParkingSpot availableSpot = findAvailableSpot(vehicle.getVehicleType());

        if (availableSpot == null) {
            throw new IllegalStateException("No spot available for: " + vehicle.getVehicleType());
        }

        availableSpot.markOccupied();

        ParkingTicket ticket = new ParkingTicket(vehicle, availableSpot);
        activeTickets.put(ticket.getTicketId(), ticket);

        return ticket;
    }

    // Exit — calculate amount, close ticket, free spot
    public double exitVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.get(ticketId);

        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found: " + ticketId);
        }
        if (ticket.getStatus() == TicketStatus.PAID) {
            throw new IllegalStateException("Ticket already paid: " + ticketId);
        }

        double amount = calculateAmount(ticket);
        ticket.processExit(amount);
        ticket.getSpot().markAvailable();

        return amount;
    }

    // Loops floors in order — first floor with a matching free spot wins
    private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
        for (Floor floor : floors) {
            ParkingSpot spot = floor.getAvailableSpot(vehicleType);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    // Ceil to nearest hour — 1hr 10min billed as 2hrs
    private double calculateAmount(ParkingTicket ticket) {
        long minutes = Duration.between(ticket.getEntryTime(), Instant.now()).toMinutes();
        double hours = Math.ceil(minutes / 60.0);
        double rate = HOURLY_RATE.get(ticket.getVehicle().getVehicleType());
        return hours * rate;
    }
}
