package com.lld.parkinglot.model;

import java.time.Instant;
import java.util.UUID;

import com.lld.parkinglot.enums.TicketStatus;

public class ParkingTicket {

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final Instant entryTime;
    private Instant exitTime;
    private double amount;
    private TicketStatus status;

    public ParkingTicket(Vehicle vehicle, ParkingSpot spot) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null");
        }
        if (spot == null) {
            throw new IllegalArgumentException("Spot cannot be null");
        }
        this.ticketId = UUID.randomUUID().toString();
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = Instant.now();
        this.status = TicketStatus.ACTIVE;
    }

    public void processExit(double amount) {
        this.exitTime = Instant.now();
        this.amount = amount;
        this.status = TicketStatus.PAID;
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public Instant getEntryTime() { return entryTime; }
    public Instant getExitTime() { return exitTime; }
    public double getAmount() { return amount; }
    public TicketStatus getStatus() { return status; }
}
