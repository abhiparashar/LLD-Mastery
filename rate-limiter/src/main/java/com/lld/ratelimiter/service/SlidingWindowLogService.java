package com.lld.ratelimiter.service;

import java.time.LocalDateTime;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.lld.ratelimiter.model.SlidingWindowLog;

public class SlidingWindowLogService {
  private static final long WINDOW_DURATION_SECONDS = 60;
  private final int limit;
  private final Map<String, SlidingWindowLog> clientLogs = new HashMap<>();

  public SlidingWindowLogService(int limit) {
    this.limit = limit;
  }

  public boolean isRequestAllowed(String clientId) {
    SlidingWindowLog log = clientLogs.get(clientId);

    if (log == null) {
      log = new SlidingWindowLog(clientId);
      clientLogs.put(clientId, log);
      log.addTimeStamp();
      return true;
    }

    Deque<LocalDateTime> timestamps = log.getTimeStamps();
    timestamps.removeIf(t -> t.isBefore(LocalDateTime.now().minusSeconds(WINDOW_DURATION_SECONDS)));

    if (timestamps.size() < limit) {
      log.addTimeStamp();
      return true;
    }

    return false;
  }

}
