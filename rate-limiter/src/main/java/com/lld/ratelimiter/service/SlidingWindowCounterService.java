package com.lld.ratelimiter.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.lld.ratelimiter.model.SlidingWindowCounter;

public class SlidingWindowCounterService {
  private static final long WINDOW_DURATION_SECONDS = 60;
  private final int limit;
  private final Map<String, SlidingWindowCounter> clientCounters = new HashMap<>();

  public SlidingWindowCounterService(int limit) {
    this.limit = limit;
  }

  public boolean isRequestAllowed(String clientId) {
    SlidingWindowCounter counter = clientCounters.get(clientId);

    if (counter == null) {
      counter = new SlidingWindowCounter();
      clientCounters.put(clientId, counter);
      counter.incrementCurrent();
      return true;
    }

    long timeElapsed = Duration.between(counter.getWindowStart(), LocalDateTime.now()).toSeconds();
    if (timeElapsed >= WINDOW_DURATION_SECONDS) {
      counter.rollWindow();
    }

    double overlap = 1.0 - ((double) timeElapsed / WINDOW_DURATION_SECONDS);
    double effectiveCount = counter.getPreviousCount() * overlap + counter.getCurrentCount();

    if (effectiveCount < limit) {
      counter.incrementCurrent();
      return true;
    }

    return false;
  }

}
