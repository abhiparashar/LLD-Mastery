package com.lld.ratelimiter.model;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowLog {
  private final String clientId;
  private final Deque<LocalDateTime> timestamps = new ArrayDeque<>();

  public SlidingWindowLog(String clientId) {
    this.clientId = clientId;
  }

  public String getClientId() {
    return clientId;
  }

  public Deque<LocalDateTime> getTimestamps() {
    return timestamps;
  }

  public void addTimestamp(LocalDateTime time) {
    timestamps.addLast(time);
  }
}
