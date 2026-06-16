package com.lld.hotel;

import java.time.LocalDate;
import java.util.List;

import com.lld.hotel.enums.RoomType;
import com.lld.hotel.model.Booking;
import com.lld.hotel.model.Guest;
import com.lld.hotel.model.Room;
import com.lld.hotel.service.HotelService;

public class Hotel {

    private final String name;
    private final List<Room> rooms;
    private final HotelService service;

    public Hotel(String name, List<Room> rooms) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Hotel name cannot be empty");
        }
        this.name = name;
        this.rooms = rooms;
        this.service = new HotelService(rooms);
    }

    public Booking bookRoom(Guest guest, RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate) {
        return service.createBooking(guest, roomType, checkInDate, checkOutDate);
    }

    public void cancelBooking(String bookingId) {
        service.cancelBooking(bookingId);
    }

    public void checkOut(String bookingId) {
        service.checkOutBooking(bookingId);
    }

    public String getName() {
        return name;
    }

    public List<Room> getRooms() {
        return rooms;
    }
}
