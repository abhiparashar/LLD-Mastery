package com.lld.ratelimiter;

import com.lld.ratelimiter.service.RateLimiterService;

public class Main {

  public static void main(String[] args) {
    RateLimiterController controller = new RateLimiterController(new RateLimiterService(3));

    String alice = "alice";
    String bob = "bob";

    System.out.println("--- ALICE fires 5 requests (limit = 3) ---");
    for (int i = 1; i <= 5; i++) {
      System.out.println("Request " + i + ": " + (controller.isRequestAllowed(alice) ? "ALLOWED" : "BLOCKED"));
    }

    System.out.println("\n--- BOB fires 2 requests (limit = 3) ---");
    for (int i = 1; i <= 2; i++) {
      System.out.println("Request " + i + ": " + (controller.isRequestAllowed(bob) ? "ALLOWED" : "BLOCKED"));
    }

    System.out.println("\n--- BOUNDARY BUG DEMO ---");
    System.out.println("Alice and Bob are independent — Bob still has 1 request left");
    System.out.println("Request 3: " + (controller.isRequestAllowed(bob) ? "ALLOWED" : "BLOCKED"));
    System.out.println("Request 4: " + (controller.isRequestAllowed(bob) ? "ALLOWED" : "BLOCKED"));
  }
}
