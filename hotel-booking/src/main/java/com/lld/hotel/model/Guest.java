package com.lld.hotel.model;

import java.util.UUID;

public class Guest {
  private final String guestId;
  private final String guestName;
  private final String guestEmail;

  public Guest(String guestName, String guestEmail) {
    if (guestName == null) {
      throw new IllegalArgumentException("Guest name can not be null");
    }
    if (guestEmail == null) {
      throw new IllegalArgumentException("Guest email can not be null");
    }
    this.guestId = UUID.randomUUID().toString();
    this.guestName = guestName;
    this.guestEmail = guestEmail;
  }

  public String getGuestId() {
    return guestId;
  }

  public String getGuestName() {
    return guestName;
  }

  public String getGuestEmail() {
    return guestEmail;
  }
}
