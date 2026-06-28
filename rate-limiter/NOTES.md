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

## Design (in progress)
