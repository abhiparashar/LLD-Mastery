package com.lld.hotel;

import java.time.LocalDate;
import java.util.List;

import com.lld.hotel.enums.RoomType;
import com.lld.hotel.model.Booking;
import com.lld.hotel.model.Guest;
import com.lld.hotel.model.Room;

public class Main {

    public static void main(String[] args) {

        List<Room> rooms = List.of(
            new Room(RoomType.SINGLE),
            new Room(RoomType.DOUBLE),
            new Room(RoomType.SUITE)
        );

        Hotel hotel = new Hotel("Grand Stay", rooms);

        Guest alice = new Guest("Alice", "alice@example.com");
        Guest bob = new Guest("Bob", "bob@example.com");

        // Alice books the only SINGLE room for June 20-25
        Booking aliceBooking = hotel.bookRoom(alice, RoomType.SINGLE,
            LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 25));

        System.out.println("--- ALICE BOOKING ---");
        System.out.println("Booking ID : " + aliceBooking.getBookingId());
        System.out.println("Room       : " + aliceBooking.getRoom().getRoomId());
        System.out.println("Dates      : " + aliceBooking.getCheckInDate() + " to " + aliceBooking.getCheckOutDate());
        System.out.println("Amount     : " + aliceBooking.getAmount());
        System.out.println("Status     : " + aliceBooking.getStatus());

        // Edge case: Bob tries to book the SAME single room with overlapping dates
        System.out.println("\n--- EDGE CASE: overlapping booking ---");
        try {
            hotel.bookRoom(bob, RoomType.SINGLE, LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 28));
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Bob books the same room type but NON-overlapping dates — should succeed
        System.out.println("\n--- BOB BOOKING: non-overlapping dates ---");
        Booking bobBooking = hotel.bookRoom(bob, RoomType.SINGLE,
            LocalDate.of(2026, 6, 26), LocalDate.of(2026, 6, 28));
        System.out.println("Booking ID : " + bobBooking.getBookingId());
        System.out.println("Status     : " + bobBooking.getStatus());

        // Alice cancels her booking
        System.out.println("\n--- ALICE CANCELS ---");
        hotel.cancelBooking(aliceBooking.getBookingId());
        System.out.println("Status after cancel: " + aliceBooking.getStatus());

        // Now someone else CAN book the same room for Alice's original dates
        System.out.println("\n--- EDGE CASE: booking freed-up dates after cancellation ---");
        Booking newBooking = hotel.bookRoom(bob, RoomType.SINGLE,
            LocalDate.of(2026, 6, 20), LocalDate.of(2026, 6, 25));
        System.out.println("Booking ID : " + newBooking.getBookingId());
        System.out.println("Status     : " + newBooking.getStatus());
    }
}
