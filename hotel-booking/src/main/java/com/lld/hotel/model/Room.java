package com.lld.hotel.model;

import java.util.Map;
import java.util.UUID;

import com.lld.hotel.enums.RoomType;

public class Room {
  private final String roomId;
  private final RoomType roomType;
  private final double price;

  private static final Map<RoomType, Integer> PRICE_MAP = Map.of(
      RoomType.SINGLE, 3000,
      RoomType.DOUBLE, 5000,
      RoomType.SUITE, 8000);

  public Room(RoomType roomType) {
    this.roomId = UUID.randomUUID().toString();
    this.roomType = roomType;
    this.price = PRICE_MAP.get(roomType);
  }

  public double getPrice() {
    return price;
  }

  public String getRoomId() {
    return roomId;
  }

  public RoomType getRoomType() {
    return roomType;
  }
}
