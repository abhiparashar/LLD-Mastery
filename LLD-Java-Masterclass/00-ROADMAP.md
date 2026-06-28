# LLD-Java FAANG Masterclass
### By a Principal Software Engineer, Backend @ Google

---

## Goal
After this guide you will be in the **top 1% of LLD candidates** globally. No other resource needed.

---

## How FAANG LLD Rounds Work

| Aspect | Reality |
|---|---|
| Time | 45–60 min. 5 min requirements, 30 min coding, 10 min extensions, 5 min Q&A |
| What they evaluate | OOP modeling, SOLID, right pattern for right problem, extensibility, concurrency awareness |
| What kills candidates | God classes, switch-case sprawl, no interfaces, ignoring threads, over-engineering, can't extend |
| Language | Java — type system forces you to think in contracts |

---

## 10-Week Study Plan

| Week | Files | Focus |
|---|---|---|
| 1 | 01, 02 | OOP Foundations + SOLID |
| 2 | 03, 04, 05 | All Design Patterns |
| 3 | 06, 07 | Concurrency + UML |
| 4 | 08 | Interview Framework |
| 5 | 09, 10 | Easy + Medium Solved Problems |
| 6 | 11 | Hard Solved Problems |
| 7 | 12, 13 | Traps + Anti-Patterns |
| 8 | 14, 15 | Java Idioms + Pattern Combos |
| 9 | 16, 17 | Advanced Concurrency + More Solved |
| 10 | 18, 19, 20 | Mock Interviews + 50 Practice Problems |

---

## File Index

| # | File | Topic |
|---|---|---|
| 01 | OOP-FOUNDATIONS | Encapsulation, Abstraction, Inheritance, Polymorphism |
| 02 | SOLID-PRINCIPLES | All 5 principles with violations, fixes, traps |
| 03 | PATTERNS-CREATIONAL | Factory, Builder, Singleton, Prototype |
| 04 | PATTERNS-STRUCTURAL | Decorator, Adapter, Proxy, Facade, Composite |
| 05 | PATTERNS-BEHAVIORAL | Strategy, Observer, State, Command, CoR, Template, Mediator |
| 06 | CONCURRENCY | Threads, locks, concurrent collections, producer-consumer |
| 07 | UML-AND-SCHEMA | Class diagrams, sequence diagrams, DB schema |
| 08 | INTERVIEW-FRAMEWORK | Step-by-step method to solve any LLD in 45 min |
| 09 | SOLVED-EASY | Parking Lot, Library Management |
| 10 | SOLVED-MEDIUM | Elevator, Chess, BookMyShow |
| 11 | SOLVED-HARD | Splitwise, Rate Limiter, Order Matching Engine |
| 12 | COUNTER-QUESTIONS | 40+ trap questions with model answers |
| 13 | ANTI-PATTERNS | 10 instant-reject patterns |
| 14 | JAVA-IDIOMS | Enums, Records, Sealed classes, Optional, Functional |
| 15 | PATTERN-COMBOS | How patterns compose in real systems |
| 16 | SOLVED-CLASSICS | LRU Cache, ATM, Vending Machine, Logger |
| 17 | SOLVED-ADVANCED | Ride Share, Notification System, Splitwise 2.0 |
| 18 | ADVANCED-CONCURRENCY | Thread pools, CompletableFuture, Lock striping |
| 19 | MODERN-PRACTICES | Clean Architecture, DDD, Hexagonal, CQRS in LLD |
| 20 | PRACTICE-50 | 50 graded problems with hints |

---

## Golden Rules for Every LLD Interview

1. **Clarify before coding** — ask scope, scale, concurrency needs
2. **Start with interfaces** — never a concrete class first
3. **Model nouns as classes, verbs as methods**
4. **Favor composition over inheritance**
5. **Every extension should require zero modification** (Open/Closed)
6. **Name things what they are** — no Util, Manager, Handler god classes
7. **Make concurrency explicit** — even if you don't implement it, mention it
8. **Know when NOT to use a pattern** — over-engineering is as bad as under-engineering
