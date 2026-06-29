package com.lld.ratelimiter;

import com.lld.ratelimiter.service.SlidingWindowCounterService;

public class SlidingWindowCounterMain {

  public static void main(String[] args) throws InterruptedException {
    SlidingWindowCounterController controller = new SlidingWindowCounterController(
        new SlidingWindowCounterService(3));

    String alice = "alice";

    System.out.println("--- ALICE fires 5 requests (limit = 3) ---");
    for (int i = 1; i <= 5; i++) {
      System.out.println("Request " + i + ": " + (controller.isRequestAllowed(alice) ? "ALLOWED" : "BLOCKED"));
    }

    System.out.println("\n--- WAIT 61 seconds for window to roll ---");
    Thread.sleep(61000);

    System.out.println("\n--- ALICE fires 3 more after window rolls ---");
    for (int i = 1; i <= 3; i++) {
      System.out.println("Request " + i + ": " + (controller.isRequestAllowed(alice) ? "ALLOWED" : "BLOCKED"));
    }
  }
}
