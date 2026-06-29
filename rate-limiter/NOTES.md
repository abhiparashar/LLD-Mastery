# Rate Limiter

## What Is It
Controls how many requests a client can make within a time window. Protects the server from being overwhelmed — by bad actors AND well-intentioned clients (retry storms, flash sales, misconfigured clients).

---

## The 3 Algorithms

### 1. Fixed Window Counter
Divide time into fixed buckets (e.g. every 60s). Count requests per bucket. Reset when bucket ends.

- Memory: tiny (one counter per user)
- Problem: boundary spike — 5 requests at 12:00:59 + 5 at 12:01:01 = 10 in 2 seconds, both windows say fine
- Used when: internal tools, admin dashboards — occasional spikes don't matter

### 2. Sliding Window Log
Store the exact timestamp of every request. On each new request, discard timestamps older than the window, count what's left.

- Memory: huge — 10,000 requests = 10,000 timestamps per user
- Accuracy: perfect
- Used when: low traffic, perfect accuracy needed — audit systems, financial transactions

### 3. Sliding Window Counter (what we're building)
Keep only two numbers per user: previous window count + current window count. Estimate using overlap.

```
effective count = previous_count × (1 - elapsed%) + current_count
```

Example: 60s window, limit 10, request at 12:01:45 (75% into current minute, 25% overlap with previous)
- previous = 8, current = 3
- effective = 8 × 0.25 + 3 = 5 → allow

- Memory: tiny (two numbers per user)
- Accuracy: good enough for production
- Used when: scale matters — Stripe, Cloudflare, most real APIs

---

## Algorithm Decision
**Sliding Window Counter** — memory efficient and accurate enough for production.

---

## Scope Decision
**Per client key** (generic `clientId` string). The caller decides what to pass — userId, IP, serviceId. Zero changes inside the rate limiter to switch scope.

---

## Algorithm 1 — Fixed Window Counter

### Classes Built
- `Window` (model) — clientId, limit, currentCount, windowStart. `increment()` to bump count, `final` on immutable fields
- `RateLimiterService` (service) — `Map<String, Window>` per client, `WINDOW_DURATION_SECONDS = 60` as internal constant, `isRequestAllowed(clientId)` returns boolean
- `RateLimiterController` — thin wrapper delegating to service

### Key Decisions
- `allowRequest` returns `boolean`, not exception — rate limit exceeded is an expected outcome, not an error
- `clientId` is a generic string — caller decides if it's userId, IP, serviceId
- Service creates windows on demand, no window injected from outside
- Window expiry creates a fresh `Window`, doesn't mutate the old one
- `increment()` not `setCurrentCount()` — object controls its own state (Tell, Don't Ask)

### The Bug (intentional)
Boundary spike: 3 requests at 12:00:59 + 3 at 12:01:01 = 6 in 2 seconds, both windows say fine. This is why we move to Sliding Window Log next.

---

## Algorithm 2 — Sliding Window Log

### Classes Built
- `SlidingWindowLog` (model) — clientId, `Deque<LocalDateTime>` timestamps. `ArrayDeque` chosen over `ArrayList` because we add to back and evict from front — O(1) both ends
- `SlidingWindowLogService` (service) — `Map<String, SlidingWindowLog>` per client, evicts expired timestamps before every size check using `removeIf`
- `SlidingWindowLogController` — thin wrapper delegating to service

### Key Decisions
- Evict **before** size check — without eviction, expired timestamps count against the limit
- `removeIf(t -> t.isBefore(now - 60s))` — the line that makes it a sliding window
- `limit` in service constructor, not hardcoded — caller decides the policy
- Model exposes real deque, not a copy — service needs to mutate it for eviction

### The Bug (intentional)
Memory: 1M users × 10K requests = 10B timestamps in RAM. This is why Sliding Window Counter exists.

---

## Algorithm 3 — Sliding Window Counter

### Classes Built
- `SlidingWindowCounter` (model) — `previousCount`, `currentCount`, `windowStart`. `incrementCurrent()` to bump count, `rollWindow()` to shift previous←current, reset current to 0, reset windowStart to now
- `SlidingWindowCounterService` (service) — calculates `effectiveCount` using overlap formula, rolls window on expiry
- `SlidingWindowCounterController` — thin wrapper delegating to service

### The Formula
```
elapsed    = now - windowStart (seconds)
overlap    = 1.0 - (elapsed / 60)
effectiveCount = previousCount × overlap + currentCount
```

### Key Decisions
- `rollWindow()` called when `elapsed >= 60`, before calculating overlap — not when blocked
- `WINDOW_DURATION_SECONDS` as constant in service — not in model
- After rollWindow, `timeElapsed` resets to ~0 so overlap becomes ~1.0 (full previous window weight)

### Why This Wins
- Fixed Window: O(1) memory, boundary bug
- Sliding Log: perfect accuracy, O(n) memory per user
- Sliding Counter: O(1) memory, good enough accuracy — best of both
