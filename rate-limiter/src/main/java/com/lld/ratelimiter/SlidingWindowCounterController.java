package com.lld.ratelimiter;

import com.lld.ratelimiter.service.SlidingWindowCounterService;

public class SlidingWindowCounterController {

  private final SlidingWindowCounterService service;

  public SlidingWindowCounterController(SlidingWindowCounterService service) {
    this.service = service;
  }

  public boolean isRequestAllowed(String clientId) {
    return service.isRequestAllowed(clientId);
  }
}
