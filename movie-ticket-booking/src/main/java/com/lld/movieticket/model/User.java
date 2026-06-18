package com.lld.movieticket.model;

import java.util.UUID;

public class User {
  private final String userId;
  private final String name;

  public User(String name) {
    this.userId = UUID.randomUUID().toString();
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getUserId() {
    return userId;
  }
}
