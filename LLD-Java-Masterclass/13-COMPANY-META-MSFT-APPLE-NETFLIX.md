# 13 — Company-Specific Problems: Meta, Microsoft, Apple, Netflix

> Problem statements, requirements, entity models, design approach, patterns, traps. No implementation code.

---

# META

## Problem 1: Facebook Messenger / WhatsApp

**Frequency**: Very High | **Level**: Hard

### The Problem
Design a real-time chat system supporting 1-on-1 and group messaging.

### Requirements to Clarify
- 1-on-1 chat, group chat (up to 256 members)
- Text, images, documents, voice messages
- Message delivery status: Sent → Delivered → Read (double tick)
- Online/offline presence
- Message history (pagination)
- Push notifications for offline users

### Core Entities
```
User            → id, name, phone, status (ONLINE/OFFLINE/LAST_SEEN), deviceTokens
Conversation    → id, type (DIRECT/GROUP), participants, lastMessage, lastActivityTime
Message         → id, conversationId, senderId, content, type (TEXT/IMAGE/AUDIO), timestamp, status
MessageStatus   → messageId, userId, status (SENT/DELIVERED/READ), timestamp
GroupMetadata   → conversationId, name, icon, adminIds, description
MessageReaction → messageId, userId, reaction (emoji)
Attachment      → messageId, url, mimeType, size, thumbnailUrl
```

### Design Approach
- **Observer**: Message send → notify all participants via WebSocket (online) or push notification (offline)
- **State**: Message delivery state machine — PENDING → SENT → DELIVERED → READ
- **Strategy**: `MessageDeliveryStrategy` — WebSocket for online, FCM/APNs push for offline
- **Mediator**: `ChatServer` coordinates between sender and receivers
- **Chain of Responsibility**: `MessageProcessingPipeline` — validate → store → deliver → notify

### Concurrency & Scale Design
```
High-volume considerations to mention:
- Message fanout: 1 message in group of 256 = 255 deliveries
- Use message queue (Kafka) for async fanout
- Presence: heartbeat every 30s, Redis TTL for online status
- Read receipts: batch aggregate to avoid N writes per message
- Message ordering: per-conversation sequence number
- Storage: hot messages in Redis, cold in Cassandra (write-optimized)
```

### Traps & Follow-ups
- **"Delivered vs Read in group chat?"** → DELIVERED when all online members get it. READ when each member reads it (per-member receipt tracking)
- **"Message ordering?"** → Server-assigned sequence number per conversation. Client reorders if network delivers out of order
- **"End-to-end encryption?"** → Keys managed on device, server stores only ciphertext. Signal Protocol (double ratchet). LLD scope: `EncryptionService` interface with key exchange model
- **"Offline message delivery?"** → Store in persistent queue. On reconnect, server pushes missed messages
- **"Group of 50,000 (broadcast channel)?"** → Different model — publisher/subscriber with fan-out service, not direct delivery to each member

---

## Problem 2: Facebook News Feed

**Frequency**: High | **Level**: Hard

### The Problem
Design the News Feed generation and ranking system.

### Requirements to Clarify
- Generate personalized feed for each user
- Posts from friends and followed pages
- Ranked by relevance (not purely chronological)
- Real-time for active users, cached for inactive
- Pagination

### Core Entities
```
Post            → id, authorId, content, type (TEXT/IMAGE/VIDEO/LINK), timestamp, privacy
FeedItem        → userId, postId, score, insertedAt (per-user ranked feed)
EdgeRank        → affinity (user-author relationship strength), weight (post type), time decay
UserConnection  → userId, friendId or followedPageId, connectionType, interactionScore
FeedCache       → userId, feedItems (ranked), generatedAt, cursor
```

### Feed Generation: Push vs Pull

```
PUSH (Fan-out on write):
  When Alice posts → immediately write to all her friends' feed caches
  + Fast reads (feed is pre-computed)
  - Expensive for celebrities (10M followers = 10M writes per post)

PULL (Fan-out on read):
  When Bob opens app → pull posts from all friends, merge, rank
  + No write amplification
  - Slow reads, can't rank well without materialized feed

HYBRID (What Facebook/Instagram use):
  Regular users: PUSH to feed cache
  Celebrities (>100k followers): PULL on read, merged with push feed
```

### Design Approach
- **Strategy**: `FeedRankingStrategy` — EdgeRank, ML-based, reverse chronological
- **Strategy**: `FeedGenerationStrategy` — push, pull, hybrid based on author's follower count
- **Observer**: New post → trigger fan-out service
- **Builder**: Complex `FeedQuery` construction with filters, cursor pagination

### Traps & Follow-ups
- **"Real-time feed update?"** → WebSocket push for active users. On post creation, publisher sends to connected users' WebSocket connections
- **"EdgeRank factors?"** → Affinity (how much you interact with author), weight (video > photo > text), time decay (exponential)
- **"Feed diversity?"** → Anti-clustering: don't show 10 posts from same author consecutively. Interleave by source
- **"Privacy filtering?"** → Apply AFTER ranking. Can't pre-filter because privacy rules are complex (friend-of-friend, custom lists)

---

## Problem 3: Instagram Stories

**Frequency**: Medium | **Level**: Medium

### The Problem
Design Instagram Stories — ephemeral content that disappears after 24 hours.

### Requirements to Clarify
- Post images/videos as stories (24-hour expiry)
- View stories of followed users
- See who viewed your story
- Story highlights (permanent)
- Reactions and replies

### Core Entities
```
Story           → id, userId, mediaUrl, mediaType, duration, createdAt, expiresAt (createdAt + 24h)
StoryView       → storyId, viewerId, viewedAt, reaction (optional)
StoryHighlight  → id, userId, coverImageUrl, title
HighlightStory  → highlightId, storyId, addedAt
StoryRing       → userId, stories (sorted by createdAt), hasUnviewedStories
```

### Design Approach
- **Observer**: When story expires → delete media, archive metadata, notify user
- **Strategy**: `StoryOrderStrategy` — shows friends you interact with most first (like Instagram's algorithm)
- **Decorator**: `StoryHighlightDecorator` — extends story beyond 24 hours when added to highlight
- **State**: Story states — ACTIVE → EXPIRED → ARCHIVED (if in highlight)

### Traps & Follow-ups
- **"24-hour expiry implementation?"** → Don't delete immediately — TTL in Redis for fast lookup. Background job soft-deletes expired stories hourly. Serve from storage only if not expired
- **"View count at scale?"** → Approximate counting. HyperLogLog in Redis for unique viewer counts (memory-efficient). Exact list stored separately, fetched lazily
- **"Story ring ordering?"** → Friends with recent interaction come first. Mix of ML ranking and recency

---

# MICROSOFT

## Problem 1: Microsoft Teams — Chat & Meetings

**Frequency**: High | **Level**: Hard

### The Problem
Design Teams' messaging and meeting infrastructure with enterprise features.

### Requirements to Clarify
- Channels (public/private), direct messages, group chats
- Meetings with scheduling, video, screen share (focus on scheduling and data model)
- @mentions, threaded replies
- File sharing integration with SharePoint/OneDrive
- Enterprise: compliance recording, data retention policies

### Core Entities
```
Organization    → id, name, domain, settings
Team            → id, orgId, name, members, channels
Channel         → id, teamId, name, type (GENERAL/PRIVATE/SHARED), members
Message         → id, channelId, senderId, content, parentMessageId (for threads), mentions, reactions
Meeting         → id, organizerId, title, startTime, endTime, attendees, joinUrl, recordingUrl
MeetingAttendee → meetingId, userId, status (INVITED/ACCEPTED/DECLINED), joinTime, leaveTime
ComplianceRecord → messageId, retentionPolicy, archiveDate, immutable (can't be deleted by user)
DataRetentionPolicy → orgId, entityType, retentionDays, deleteAfterExpiry
```

### Design Approach
- **Strategy**: `MessageSearchStrategy` — full-text search with access control (users only search channels they're in)
- **Chain of Responsibility**: `MessageCompliancePipeline` — DLP (data loss prevention) → retention tagging → archive → deliver
- **Observer**: @mention → notify mentioned user; meeting start → notify attendees
- **State**: Meeting states — SCHEDULED → ACTIVE → RECORDING → ENDED → ARCHIVED
- **Decorator**: `ComplianceDecorator` wraps channel — intercepts all messages for compliance processing

### Traps & Follow-ups
- **"What's different about enterprise chat vs consumer chat?"** → Compliance (messages can't be deleted by users if under retention policy), eDiscovery (legal hold), admin controls, SSO, audit logs
- **"Threaded replies?"** → `parentMessageId` field — fetched separately as a thread. Thread preview shows last 2 replies in channel view
- **"Offline message queue?"** → Enterprise usually has push notification + guaranteed delivery via message queue with ack mechanism
- **"Large org — 100k member org-wide announcements?"** → Tiered fan-out: announcements go via push notification service directly, not chat message fan-out

---

## Problem 2: Office 365 — Document Collaboration

**Frequency**: Medium | **Level**: Hard

### The Problem
Design collaborative editing for Word/Excel/PowerPoint in the cloud.

### Requirements to Clarify
- Multiple users edit same doc simultaneously
- Each Office app has different complexity (Word = text, Excel = cells+formulas, PowerPoint = slides)
- AutoSave every few seconds
- Version history
- Comments and suggestions

### Core Entities
```
CloudDocument   → id, ownerId, type (WORD/EXCEL/PPT), storageUrl, currentVersion, lastModified
DocumentSession → documentId, userId, sessionToken, lastActive, cursorPosition
DocumentChange  → id, documentId, userId, changeType, content, timestamp, vectorClock
DocumentVersion → documentId, version, snapshotUrl, changesSinceLastSnapshot, createdAt
Comment         → documentId, userId, anchoredRange, text, resolvedBy, replies
AutoSaveState   → sessionId, pendingChanges, lastSyncedVersion
```

### Key Difference From Google Docs
```
Excel:
  - Cell-level granularity (A1, B2) not character-level
  - Formula dependencies create a DAG — changing A1 might recalc 20 other cells
  - Conflict: two users edit same cell → last-write-wins for simple values, formula conflicts flagged

PowerPoint:
  - Slide-level isolation — two users editing different slides have no conflict
  - Object-level within slide (textbox, image positions)
  - Less real-time pressure than Google Docs
```

### Traps & Follow-ups
- **"Excel formula dependency graph?"** → DAG — when cell A1 changes, traverse dependents topologically and recalculate. Circular dependency detection.
- **"Co-authoring lock granularity?"** → Word: paragraph-level lock (simpler than character-level OT). Excel: cell-level lock. PPT: slide-level lock
- **"What's AutoSave?"** → Client sends delta every 30s to cloud. Server merges and persists. Different from explicit Save which creates a version snapshot

---

# APPLE

## Problem 1: App Store Review & Distribution

**Frequency**: Medium | **Level**: Medium-Hard

### The Problem
Design the App Store's submission, review, and distribution pipeline.

### Requirements to Clarify
- Developer submits app binary
- Automated + manual review process
- Staged rollout to users
- Version management (multiple active versions)
- Rating, reviews, developer responses

### Core Entities
```
AppSubmission   → id, appId, version, binaryUrl, changelog, submittedAt, status
ReviewProcess   → submissionId, automatedResult, manualReviewerId, decision, rejectionReasons, timestamp
App             → id, developerAccountId, bundleId, name, category, currentVersion
AppVersion      → appId, version, binaryUrl, releaseNotes, status, rolloutPercentage
UserRating      → appId, userId, rating (1-5), review, timestamp
DeveloperResponse → ratingId, developerId, response, timestamp
RolloutConfig   → appVersionId, percentage, regions, releaseDate
```

### Design Approach
- **Chain of Responsibility**: `ReviewPipeline` — AutomatedScan (malware, API usage) → ContentReview → PrivacyCheck → ManualReview (if flagged)
- **State**: Submission states — SUBMITTED → AUTOMATED_REVIEW → MANUAL_REVIEW → APPROVED → IN_REVIEW → REJECTED
- **Strategy**: `RolloutStrategy` — phased (1% → 10% → 50% → 100%), regional, immediate
- **Observer**: Status changes → notify developer via email/developer portal

### Traps & Follow-ups
- **"Staged rollout — how to decide which users get new version?"** → Consistent hashing on userId. User in rollout bucket gets new version, others get previous
- **"App crashes after 10% rollout?"** → Developer can pause rollout, push patch, resume. Monitoring feeds crash rate to rollout service
- **"Expedited review?"** → Priority queue. Critical bug fixes get fast-tracked. Commercial arrangement for regular prioritization

---

## Problem 2: iCloud Photos — Privacy-First Storage

**Frequency**: Medium | **Level**: Hard

### The Problem
Design iCloud Photos with end-to-end encryption, sync across devices, and smart albums.

### Requirements to Clarify
- Photo/video upload from device
- Sync across all user's Apple devices
- End-to-end encryption (server can't see content)
- Smart albums (by date, location, person, scene)
- iCloud Photo Library (optimized storage: full quality on cloud, compressed on device)

### Core Entities
```
Photo           → id, ownerId, encryptedDataRef, encryptedMetadata, thumbnailRef, capturedAt, uploadedAt
PhotoMetadata   → photoId, location, cameraModel, dimensions, duration (encrypted)
DeviceSyncState → deviceId, userId, lastSyncedAt, localPhotoIds, pendingUploads
Album           → id, userId, name, type (MANUAL/SMART), photos
SmartAlbumRule  → albumId, criteria (dateRange, location, personId, sceneType)
Person          → id, userId, faceClusterId, name (user-assigned, encrypted)
EncryptionKey   → userId, deviceId, keyType, publicKey, encryptedPrivateKey
```

### Design Approach
- **Strategy**: `CompressionStrategy` — HEIC, JPEG, ProRAW for different storage/quality tradeoffs
- **Observer**: New photo upload → trigger face recognition, scene classification, EXIF parsing (all on-device for E2E encryption)
- **Strategy**: `SyncStrategy` — WiFi-only, any network, optimized storage (full quality up, compressed local)
- **Builder**: `SmartAlbumQueryBuilder` — compose criteria for smart album membership

### Key Privacy Architecture
```
With E2E encryption:
  - Encryption key derived from user's Apple ID password (never sent to Apple)
  - Photo encrypted on device before upload
  - Server stores only ciphertext + encrypted metadata
  - Face recognition / ML must happen ON DEVICE
  - If user forgets password → data lost (Apple can't decrypt)
  
Without E2E (standard):
  - Apple can access for CSAM detection, legal requests
  - Keys managed by Apple, recoverable
```

### Traps & Follow-ups
- **"Smart albums with E2E encryption?"** → ML analysis runs on-device. Classification labels stored encrypted. Search/album queries processed on-device against local encrypted index
- **"Sync conflict — same photo edited on two devices?"** → Last-write-wins with user notification. Or create two copies and ask user. Photo identity based on hash of original.

---

# NETFLIX

## Problem 1: Video Streaming with Adaptive Quality

**Frequency**: High | **Level**: Hard

### The Problem
Design Netflix's video streaming infrastructure focusing on adaptive bitrate and CDN.

### Requirements to Clarify
- Videos served at multiple quality levels (480p, 720p, 1080p, 4K)
- Adaptive bitrate: quality adjusts to network speed
- Global CDN distribution
- Offline download
- Resume playback from any device

### Core Entities
```
VideoContent    → id, title, type (MOVIE/EPISODE), originalFileRef, durationSeconds
VideoRendition  → videoId, quality (480p/720p/1080p/4K), bitrate, codec, segmentedManifestUrl
VideoSegment    → videoId, quality, segmentIndex, duration (10s), url, cdnUrl
ContentCatalog  → country, availableTitles (geo-licensing)
PlaybackSession → id, userId, videoId, deviceId, currentPosition, quality, startedAt
CDNNode         → id, region, location, capacity, cachedContent
UserDownload    → userId, videoId, quality, downloadedSegments, expiresAt (license expiry)
```

### Adaptive Bitrate (ABR) Design
```
HLS / DASH manifest:
  - Master manifest: lists all quality renditions + their manifests
  - Per-quality manifest: lists all 10-second segments for that quality
  
Client algorithm:
  - Measure download speed of each segment
  - Maintain bandwidth estimate (weighted moving average)
  - Select quality level matching ~80% of bandwidth estimate
  - Buffer 3 segments ahead
  - On stall: drop 1-2 quality levels immediately
  - On steady: slowly increase quality every 10-15 segments
```

### Design Approach
- **Strategy**: `ABRAlgorithm` — `BufferBasedABR`, `ThroughputBasedABR`, `HybridABR`
- **Strategy**: `CDNSelectionStrategy` — anycast routing, latency-based, load-based
- **Observer**: Playback events (buffer, stall, quality change) → analytics service → real-time monitoring
- **Decorator**: `DRMDecorator` — wraps segment delivery with license validation

### Traps & Follow-ups
- **"What if CDN edge has cache miss?"** → Edge requests from origin (Netflix's own or AWS S3). Hierarchical caching: L1 edge → L2 regional → origin
- **"Offline download expiry?"** → Time-limited DRM license. After 30 days or 48 hours after first play → license expired → video unplayable without reconnect
- **"Resume on different device?"** → `PlaybackSession.currentPosition` synced to server every 30 seconds. New device fetches last position on open
- **"Global licensing — content not available in country?"** → `ContentCatalog` filtered by user's country. CDN serves only licensed content to that region

---

## Problem 2: Content Recommendation Engine

**Frequency**: Medium | **Level**: Hard

### The Problem
Design Netflix's recommendation system data model and service layer.

### Requirements to Clarify
- Personalized rows: "Trending", "Because You Watched X", "Top Picks for You"
- Multi-arm bandit for thumbnail selection (different thumbnails for different users)
- A/B testing of recommendation algorithms
- Cold start for new users

### Core Entities
```
ViewingHistory  → userId, contentId, watchedPercent, rating, timestamp
ContentSimilarity → contentA, contentB, score, algorithm, computedAt
UserPreference  → userId, preferredGenres, preferredActors, preferredLanguages
Recommendation  → userId, rowType, contentIds (ranked), algorithm, generatedAt
ThumbnailVariant → contentId, imageUrl, targetAudience, clickThroughRate
ABExperiment    → id, name, variants (algorithms), userSegmentation, metrics, status
```

### Design Approach
- **Strategy**: `RecommendationStrategy` — `CollaborativeFiltering`, `ContentBased`, `TrendingStrategy`, `ContinueWatching`
- **Composite**: `HybridRecommender` — combines multiple strategies with learned weights per user segment
- **Factory**: `RecommendationStrategyFactory` — creates strategy based on user segment + A/B bucket
- **Observer**: Viewing events → async update user profile → invalidate cached recommendations

### Multi-Armed Bandit (Thumbnail Selection)
```
Problem: Show different thumbnails to different users, learn which converts best
Solution: ε-greedy or UCB (Upper Confidence Bound)
  - Explore: Show random thumbnail to 10% of users
  - Exploit: Show best-performing thumbnail to 90%
  - Track: clicks, play starts per thumbnail
  - Converge: to winning thumbnail per user segment
```

### Traps & Follow-ups
- **"Cold start for new user?"** → Show trending content, prompt genre preferences onboarding, quickly adapt after 3-5 views
- **"Stale recommendations?"** → TTL on cached recommendations. Invalidate on major viewing event. Batch recompute nightly
- **"Why rows instead of one ranked list?"** → Rows serve different discovery intents. "Continue Watching" = task completion. "New Releases" = novelty. Single list would be dominated by highly similar content
