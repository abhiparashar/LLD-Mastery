package com.lld.ratelimiter;

import com.lld.ratelimiter.service.RateLimiterService;

public class RateLimiterController {

  private final RateLimiterService rateLimiterService;

  public RateLimiterController(RateLimiterService rateLimiterService) {
    this.rateLimiterService = rateLimiterService;
  }

  public boolean isRequestAllowed(String clientId) {
    return rateLimiterService.isRequestAllowed(clientId);
  }
}
