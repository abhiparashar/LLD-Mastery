package com.lld.ratelimiter;

import com.lld.ratelimiter.service.SlidingWindowLogService;

public class SlidingWindowLogController {

  private final SlidingWindowLogService service;

  public SlidingWindowLogController(SlidingWindowLogService service) {
    this.service = service;
  }

  public boolean isRequestAllowed(String clientId) {
    return service.isRequestAllowed(clientId);
  }
}
