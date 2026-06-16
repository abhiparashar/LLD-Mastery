package com.lld.hotel.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.lld.hotel.enums.BookingStatus;

public class Booking {

    private final String bookingId;
    private final Guest guest;
    private final Room room;
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;
    private final double amount;
    private BookingStatus status;

    public Booking(Guest guest, Room room, LocalDate checkInDate, LocalDate checkOutDate) {
        if (guest == null) {
            throw new IllegalArgumentException("Guest cannot be null");
        }
        if (room == null) {
            throw new IllegalArgumentException("Room cannot be null");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw new IllegalArgumentException("Check-in and check-out dates cannot be null");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("Check-out date must be after check-in date");
        }

        this.bookingId = UUID.randomUUID().toString();
        this.guest = guest;
        this.room = room;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.amount = calculateAmount(room, checkInDate, checkOutDate);
        this.status = BookingStatus.PENDING;
    }

    private double calculateAmount(Room room, LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights * room.getPrice();
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public void checkOut() {
        this.status = BookingStatus.CHECKED_OUT;
    }

    public String getBookingId() { return bookingId; }
    public Guest getGuest() { return guest; }
    public Room getRoom() { return room; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public double getAmount() { return amount; }
    public BookingStatus getStatus() { return status; }
}
