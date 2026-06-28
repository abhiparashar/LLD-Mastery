package com.lld.ratelimiter.service;

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
    return false;
  }

}
