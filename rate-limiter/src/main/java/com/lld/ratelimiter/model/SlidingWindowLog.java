package com.lld.ratelimiter.model;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindowLog {
  private final String clientId;
  private final Deque<LocalDateTime> timeStamps = new ArrayDeque<>();

  public SlidingWindowLog(String clientId) {
    this.clientId = clientId;
  }

  public String getClientId() {
    return clientId;
  }

  public void addTimeStamp() {
    timeStamps.add(LocalDateTime.now());
  }

  public Deque<LocalDateTime> getTimeStamps() {
    return timeStamps;
  }
}
