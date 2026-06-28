package com.lld.ratelimiter.model;

import java.time.LocalDateTime;

public class Window {
  private final String clientId;
  private final int limit;
  private int currentCount;
  private final LocalDateTime windowStart;

  public Window(String clientId, int limit) {
    this.clientId = clientId;
    this.limit = limit;
    this.windowStart = LocalDateTime.now();
  }

  public String getClientId() {
    return clientId;
  }

  public int getCurrentCount() {
    return currentCount;
  }

  public int getLimit() {
    return limit;
  }

  public LocalDateTime getWindowStart() {
    return windowStart;
  }

  public void increment() {
    this.currentCount++;
  }

}
