package com.lld.hotel.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lld.hotel.enums.BookingStatus;
import com.lld.hotel.enums.RoomType;
import com.lld.hotel.model.Booking;
import com.lld.hotel.model.Guest;
import com.lld.hotel.model.Room;

public class HotelService {

    private final List<Room> rooms;
    private final List<Booking> bookings;

    public HotelService(List<Room> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            throw new IllegalArgumentException("Rooms cannot be null or empty");
        }
        this.rooms = rooms;
        this.bookings = new ArrayList<>();
    }

    public Room findAvailableRoom(RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate) {
        for (Room room : rooms) {
            if (room.getRoomType() == roomType && isRoomAvailable(room, checkInDate, checkOutDate)) {
                return room;
            }
        }
        return null;
    }

    private boolean isRoomAvailable(Room room, LocalDate checkInDate, LocalDate checkOutDate) {
        for (Booking booking : bookings) {
            if (!booking.getRoom().getRoomId().equals(room.getRoomId())) {
                continue;
            }
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }
            boolean overlaps = booking.getCheckInDate().isBefore(checkOutDate)
                    && booking.getCheckOutDate().isAfter(checkInDate);
            if (overlaps) {
                return false;
            }
        }
        return true;
    }

    public Booking createBooking(Guest guest, RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate) {
        Room room = findAvailableRoom(roomType, checkInDate, checkOutDate);
        if (room == null) {
            throw new IllegalStateException("No room available for type: " + roomType);
        }
        Booking booking = new Booking(guest, room, checkInDate, checkOutDate);
        booking.confirm();
        bookings.add(booking);
        return booking;
    }

    public void cancelBooking(String bookingId) {
        Booking booking = findBooking(bookingId);
        booking.cancel();
    }

    public void checkOutBooking(String bookingId) {
        Booking booking = findBooking(bookingId);
        booking.checkOut();
    }

    private Booking findBooking(String bookingId) {
        for (Booking booking : bookings) {
            if (booking.getBookingId().equals(bookingId)) {
                return booking;
            }
        }
        throw new IllegalArgumentException("Booking not found: " + bookingId);
    }
}
