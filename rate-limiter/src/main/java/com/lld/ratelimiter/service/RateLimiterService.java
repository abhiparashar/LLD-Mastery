package com.lld.ratelimiter.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.lld.ratelimiter.model.Window;

public class RateLimiterService {
  private static final long WINDOW_DURATION_SECONDS = 60;
  private final int limit;
  private final Map<String, Window> clientWindows = new HashMap<>();

  public RateLimiterService(int limit) {
    this.limit = limit;
  }

  public boolean isRequestAllowed(String clientId) {
    Window window = clientWindows.get(clientId);

    if (window == null) {
      window = new Window(clientId, limit);
      clientWindows.put(clientId, window);
      window.increment();
      return true;
    }

    boolean isWithinWindow = LocalDateTime.now()
        .isBefore(window.getWindowStart().plusSeconds(WINDOW_DURATION_SECONDS));

    if (!isWithinWindow) {
      window = new Window(clientId, limit);
      clientWindows.put(clientId, window);
      window.increment();
      return true;
    }

    if (window.getCurrentCount() < window.getLimit()) {
      window.increment();
      return true;
    }

    return false;
  }
}
