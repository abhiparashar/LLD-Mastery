# 12 — Company-Specific Problems: Google

> Problem statements, requirements, entity models, design approach, patterns, traps. No implementation code.

---

## Problem 1: Google Calendar — Meeting Scheduler

**Frequency**: Very High | **Level**: Hard

### The Problem
Design Google Calendar with conflict detection, meeting invites, and recurring events.

### Requirements to Clarify
- Create, update, delete events
- Invite attendees, accept/decline
- Recurring events (daily, weekly, monthly, custom RRULE)
- Conflict detection when scheduling
- View free/busy slots for a user
- Reminders

### Core Entities
```
User            → id, name, email, timezone, settings
Calendar        → id, ownerId, name, color, isPublic
Event           → id, calendarId, title, startTime, endTime, location, description
EventAttendee   → eventId, userId, status (INVITED/ACCEPTED/DECLINED/TENTATIVE)
RecurrenceRule  → eventId, frequency (DAILY/WEEKLY/MONTHLY), interval, until, byDay, byMonthDay
EventReminder   → eventId, userId, minutesBefore, method (EMAIL/PUSH)
FreeBusySlot    → userId, startTime, endTime (computed from events)
```

### Design Approach
- **Interval Tree**: For efficient conflict detection — query "does any event overlap with [start, end]?" in O(log n)
- **Strategy**: `RecurrenceExpander` — expand RRULE into concrete event instances for a date range
- **Observer**: When event updated/cancelled, notify all attendees
- **Iterator**: `EventIterator` for recurring event occurrences
- **Builder**: Complex `Event` creation with optional recurrence, reminders, attendees

### Critical Algorithm: Conflict Detection
```
Naive: O(n) scan all events → too slow for heavy users
Better: Interval Tree — O(log n) query
  - Insert: add interval [start, end]
  - Query: find all intervals overlapping [queryStart, queryEnd]
  - Two intervals A and B overlap if: A.start < B.end AND B.start < A.end
```

### Patterns Used
- Strategy (recurrence expansion)
- Observer (attendee notifications)
- Builder (event creation)
- Iterator (recurring event expansion)
- Interval Tree (data structure, not GoF pattern)

### Traps & Follow-ups
- **"User edits one occurrence of recurring event?"** → "This event", "This and following", "All events" — creates event exception or splits recurrence series
- **"Timezone handling?"** → Store all times in UTC, convert to user's timezone on display. RRULE processing must account for DST
- **"Find a meeting time that works for 5 people?"** → Free/Busy merge: union of all busy slots, find gaps ≥ meeting duration
- **"Concurrent update to same event?"** → Optimistic locking with event version, last-write-wins or conflict notification
- **"Millions of users, query all events in date range?"** → Composite index on (calendarId, startTime). Recurring events stored as rule + exceptions, not expanded rows

---

## Problem 2: Google Drive — Real-time Collaborative Editing

**Frequency**: High | **Level**: Very Hard

### The Problem
Design Google Docs collaborative editing where multiple users edit the same document simultaneously.

### Requirements to Clarify
- Multiple users edit same document simultaneously
- Changes appear in real-time for all users
- Offline editing with sync on reconnect
- Conflict resolution
- Version history

### Core Entities
```
Document        → id, ownerId, title, currentVersion, createdAt
DocumentVersion → documentId, version, content, createdAt, createdBy
Operation       → id, documentId, type (INSERT/DELETE/RETAIN), position, content, userId, timestamp, version
Cursor          → userId, documentId, position, color (for showing other users' cursors)
Collaborator    → documentId, userId, permission (VIEWER/COMMENTER/EDITOR)
OfflineChange   → userId, documentId, operations (buffered when offline)
```

### The Core Problem: Operational Transform (OT)

```
Problem:
  Document: "Hello"
  User A at position 0: INSERT "Hi " → "Hi Hello"
  User B at position 5: INSERT "!" → "Hello!"
  
  If both applied naively: wrong result
  
OT Solution:
  Transform B's operation against A's operation:
  A inserted 3 chars at position 0
  B's position shifts: 5 + 3 = 8
  Result: "Hi Hello!" ✓

Key OT properties:
  - Causality: operations have happened-before relationships
  - Convergence: all replicas converge to same state
  - Intention preservation: user's intent is maintained after transform
```

### Design Approach
- **Command**: Each edit is an `Operation` command — can be applied, transformed, reversed
- **Observer**: Server broadcasts operations to all connected collaborators
- **Strategy**: `ConflictResolutionStrategy` — OT or CRDT-based
- **CRDT alternative**: Conflict-free Replicated Data Types — mathematically guaranteed convergence, no central transform needed
- **Event Sourcing**: Document state = ordered log of operations (can replay from any version)

### Patterns Used
- Command (operations)
- Observer (real-time broadcast)
- Event Sourcing (version history = operation log)
- Strategy (conflict resolution algorithm)

### Traps & Follow-ups
- **"OT vs CRDT — which would you use?"** → OT: requires central server for transform coordination. CRDT: peer-to-peer, simpler for offline sync, but higher memory (tombstones). Google Docs uses OT. Notion/Figma shifted toward CRDT.
- **"User goes offline and makes edits?"** → Buffer operations locally. On reconnect: transform buffered ops against server's ops since disconnect. Apply transformed ops.
- **"How do you show other users' cursors?"** → `Cursor` presence events via WebSocket. Each cursor has userId, position, color. Transform cursor positions along with ops.
- **"Version history?"** → Event sourcing: replay operations from version 0 to N. Store snapshots at intervals for efficiency (don't replay 10,000 ops every time).
- **"Permissions — viewer can't edit?"** → Middleware layer validates permission before accepting operation.

---

## Problem 3: Google Search Index and Ranking

**Frequency**: Medium | **Level**: Very Hard

### The Problem
Design the data model and service layer for a search index (not full Google — scoped to LLD level).

### Requirements to Clarify (Scope this aggressively)
- Index a set of documents
- Search by keyword, return ranked results
- Basic ranking (TF-IDF, later PageRank)
- Incremental indexing (add/update/delete docs)
- Stemming, stop words (optional)

### Core Entities
```
Document        → id, url, title, content, lastIndexedAt, pageRank
InvertedIndex   → term → List<Posting>
Posting         → documentId, termFrequency, positions (List<Integer>), fieldType (TITLE/BODY)
SearchQuery     → queryString, parsedTerms, filters, page, pageSize
SearchResult    → documentId, title, snippet, score, url
IndexStats      → term, documentFrequency, totalDocuments (for IDF calculation)
```

### Core Algorithm: TF-IDF
```
TF (Term Frequency) = occurrences of term in document / total terms in document
IDF (Inverse Document Frequency) = log(totalDocs / docsContainingTerm)
TF-IDF Score = TF * IDF

Higher score = more relevant

Title matches weighted higher than body matches
Exact phrase match weighted higher than individual term matches
```

### Design Approach
- **Strategy**: `RankingStrategy` — `TFIDFRanking`, `PageRankRanking`, `HybridRanking`
- **Strategy**: `QueryParser` — parses "java AND design NOT singleton" into structured query
- **Builder**: `SearchQueryBuilder` — build complex queries with filters
- **Observer**: When document updated → trigger re-indexing pipeline
- **Pipeline/CoR**: `IndexingPipeline` — Tokenize → Normalize → StopWordFilter → Stem → BuildPostings

### Patterns Used
- Strategy (ranking, query parsing)
- Chain of Responsibility (indexing pipeline)
- Observer (re-index trigger)
- Builder (query construction)

### Traps & Follow-ups
- **"Inverted index in memory vs DB?"** → In-memory for speed (like Lucene). DB for persistence. Hybrid: keep hot terms in memory, cold on disk
- **"Boolean queries (AND, OR, NOT)?"** → Parse query into expression tree. Evaluate by intersecting/unioning posting lists
- **"Phrase queries ("hello world")?"** → Position index: check if terms appear consecutively in same document
- **"PageRank in LLD?"** → Graph of documents with links. PageRank = iterative algorithm converging to steady-state probability. LLD focuses on data model, not the full distributed MapReduce implementation

---

## Problem 4: Gmail — Smart Email System

**Frequency**: Medium | **Level**: Hard

### The Problem
Design Gmail's core email threading, labeling, and smart features.

### Requirements to Clarify
- Send, receive, reply, forward
- Threading (conversations)
- Labels, folders, filters
- Search
- Smart Compose (autocomplete) as extension

### Core Entities
```
Email           → id, from, to (List), cc, bcc, subject, body, timestamp, messageId, inReplyTo
Thread          → id, subject, emails (List), participants, lastMessageTime
Label           → id, userId, name, color, type (SYSTEM/USER)
EmailLabel      → emailId, labelId
Filter          → id, userId, criteria (from/to/subject/keyword), action (label/archive/delete)
Contact         → userId, name, email, frequency (for autocomplete)
Attachment      → emailId, filename, mimeType, size, storageRef
```

### Threading Algorithm
```
Thread grouping rules:
1. Same subject (normalized: remove Re:, Fwd:)
2. In-Reply-To header references existing message
3. References header contains message chain

Algorithm:
  - New email arrives
  - Check inReplyTo: if matches existing messageId → same thread
  - Check References: if any reference matches → same thread  
  - Check subject (normalized): if matches recent thread (<7 days) → same thread
  - Else: new thread
```

### Design Approach
- **Chain of Responsibility**: `FilterPipeline` — applies user's email filters in order (label, archive, forward, delete)
- **Strategy**: `ThreadingStrategy` — groups emails into conversations
- **Observer**: New email arrival → trigger filter pipeline, push notification, badge count update
- **Decorator**: Email rendering — `PlainTextRenderer` → `HTMLSanitizer` → `LinkPreviewDecorator`
- **Trie** (data structure): For autocomplete of recipient addresses

### Traps & Follow-ups
- **"Unread count per label?"** → Denormalized counter per (userId, labelId), incremented on receive, decremented on read — much faster than COUNT query
- **"Search across all emails?"** → Inverted index on email content, from, subject. Full-text search (Elasticsearch in practice)
- **"Smart Compose?"** → Language model for next-token prediction. LLD scope: `AutoCompleteService` with `Trie` for contact suggestions + ML service integration via Strategy
- **"Spam detection?"** → `SpamClassifier` strategy in filter pipeline. Rules-based + ML score threshold

---

## Problem 5: Google Maps — Route Optimization

**Frequency**: Medium | **Level**: Hard

### The Problem
Design the route planning and navigation system.

### Requirements to Clarify
- Find shortest/fastest route between two points
- Real-time traffic integration
- Multiple waypoints
- Mode: driving, walking, transit
- Turn-by-turn navigation

### Core Entities
```
Location        → lat, lon, name
Node            → id, location (intersection or waypoint)
Edge            → fromNode, toNode, distance, travelTime, roadType, restrictions
Graph           → nodes, edges (adjacency list)
Route           → id, origin, destination, waypoints, segments, totalDistance, totalTime
RouteSegment    → fromNode, toNode, instruction, distance, duration
TrafficCondition → edgeId, congestionLevel, timestamp, estimatedDelay
```

### Core Algorithm: A* Search (Better than Dijkstra for geo)
```
Dijkstra: explores all nodes equally, O((V+E) log V)
A*: uses heuristic (straight-line distance) to prioritize promising paths
  f(n) = g(n) + h(n)
  g(n) = actual cost from start to n
  h(n) = estimated cost from n to goal (haversine distance)
  
Much faster in practice for road networks with geographic structure
```

### Design Approach
- **Strategy**: `RoutingAlgorithm` — `DijkstraRouter`, `AStarRouter`, `BidirectionalDijkstraRouter`
- **Strategy**: `TravelMode` — `DrivingStrategy`, `WalkingStrategy`, `TransitStrategy` (different edge weights)
- **Observer**: `TrafficMonitor` updates edge weights in real-time, triggers rerouting if faster path found
- **Decorator**: `TrafficAwareRouter` wraps base router, adjusts edge weights with real-time traffic

### Traps & Follow-ups
- **"How do you handle real-time traffic?"** → Edge weights updated from traffic feed. ETA recalculated with updated weights. If reroute savings > threshold, push new route
- **"Multiple waypoints?"** → Nearest Neighbor TSP approximation for ordering waypoints optimally, then A* between each pair
- **"Offline maps?"** → Pre-download region graph. Routing runs locally. Sync traffic and updated roads on reconnect
- **"Avoid tolls / highways?"** → Edge metadata: `hasToll`, `isHighway`. Filter edges based on user preferences during graph traversal
