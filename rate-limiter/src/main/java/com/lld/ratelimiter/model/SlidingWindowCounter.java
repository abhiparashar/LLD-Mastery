package com.lld.ratelimiter.model;

import java.time.LocalDateTime;

public class SlidingWindowCounter {
  private int previousCount;
  private int currentCount;
  private LocalDateTime windowStart;

  public SlidingWindowCounter() {
    this.previousCount = 0;
    this.currentCount = 0;
    this.windowStart = LocalDateTime.now();
  }

  public int getCurrentCount() {
    return currentCount;
  }

  public int getPreviousCount() {
    return previousCount;
  }

  public LocalDateTime getWindowStart() {
    return windowStart;
  }

  public void incrementCurrent() {
    currentCount++;
  }

  public void rollWindow() {
    previousCount = currentCount;
    currentCount = 0;
    windowStart = LocalDateTime.now();
  }
}
