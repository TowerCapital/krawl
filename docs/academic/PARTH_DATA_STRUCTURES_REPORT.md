# Academic Data Structures Report: Krawl / MeshLink
**Author:** Parth Keche  
**Module:** Core Custom Data Structures Implementation (Phase 1 & Phase 2)  
**Project:** Offline P2P Campus Communication & File Sharing Application  
**Target Environment:** Java Standard Library (JDK 8+), Zero External Dependencies  

---

## 1. Executive Summary & Problem Context

In decentralized offline campus networks (such as college Wi-Fi/LAN/hotspots without internet access), communication tools require specialized internal data structures to solve core computational and architectural challenges:

1. **Physical Context & Spatial Hierarchy:** Navigating multi-level campus locations (Campus $\to$ Buildings $\to$ Floors $\to$ Labs/Classrooms).
2. **Instant Search & Autocomplete:** Enabling students and faculty to rapidly locate classrooms and labs by room code (e.g., searching `"B10"` to discover `B101`, `B107`, `B108`).
3. **Discontinuous Network Connectivity:** Seamlessly buffering outgoing messages in FIFO order when peer devices roam out of Wi-Fi range or disconnect, and automatically flushing them upon reconnection.
4. **Peer Presence & Online Status Tracking:** Tracking which campus peers are online, offline, or newly discovered with constant-time lookup.
5. **Emergency Message Prioritization:** Guaranteeing that urgent security/campus alerts are dispatched and processed ahead of routine peer chats.
6. **Chronological Conversation Threads:** Maintaining bidirectional message histories per peer with efficient tail-appends and backwards pagination.
7. **Ad-Hoc Network Topology & Relay Routing:** Mapping active peer connections as an undirected graph and discovering multi-hop relay routes when direct peer-to-peer links are partitioned.

To satisfy rigorous academic criteria, all data structures were implemented **from scratch** using raw pointer/reference architectures without relying on pre-packaged `java.util.Stack`, `java.util.Queue`, `java.util.PriorityQueue`, or external libraries.

---

## 2. Architecture & 8-Data-Structure Taxonomy

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION CLI / GUI                                     │
│            DiscoveryTest (CLI) / JavaFX GUI (Ronak) / Persistence (Soham)              │
├────────────────────┬────────────────────┬────────────────────┬─────────────────────────┤
│  /locate & /search │  /nav & /back      │  /pending          │  /whois                 │
│  /urgent           │  /history          │  /network          │  /route                 │
└─────────┬──────────┴─────────┬──────────┴─────────┬──────────┴────────────┬────────────┘
          │                    │                    │                       │
          ▼                    ▼                    ▼                       ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌─────────────────────────┐
│ 1. GENERAL TREE  │ │ 2. CUSTOM STACK  │ │ 3. CUSTOM TRIE   │ │ 4. CUSTOM QUEUE (FIFO)  │
│  CampusTree      │ │  LocationStack   │ │  LocationTrie    │ │  PendingMessageQueue    │
│  LocationNode    │ │  CampusNavigator │ │  TrieNode        │ │  PeerMessageBuffer      │
└──────────────────┘ └──────────────────┘ └──────────────────┘ └─────────────────────────┘
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌─────────────────────────┐
│ 5. HASH MAP      │ │ 6. MIN-HEAP PQ   │ │ 7. DOUBLY-LINKED │ │ 8. ADJACENCY-LIST GRAPH │
│  PeerRegistry    │ │  PriorityMessage-│ │    LIST          │ │  PeerGraph              │
│  PeerEntry       │ │  Queue           │ │  Conversation-   │ │  (BFS Shortest Relay    │
│  PresenceStatus  │ │  MessagePriority │ │  Thread/Manager  │ │   Path Discovery)       │
└──────────────────┘ └──────────────────┘ └──────────────────┘ └─────────────────────────┘
```

---

## 3. Data Structure Specifications & Complexity Analysis

### 3.1. General Tree (Campus Location Hierarchy)
* **Files:** `LocationNode.java`, `CampusTree.java`, `LocationType.java`
* **Real-World Purpose:** Represents the multi-way administrative and physical hierarchy:  
  `VIT Pune (Campus) → B Building (Building) → First Floor (Floor) → B107 (Lab)`.
* **Key Design Characteristics:**
  * **Bidirectional Links:** Nodes store an upward `parent` reference and a downward `children` list.
  * **$O(H)$ Path Resolution:** Ascends `parent` pointers to root, producing full human-readable paths without traversing the rest of the tree.
  * **Dual Traversal Engines:** Supports both Level-Order Breadth-First Search (BFS) and Pre-Order Depth-First Search (DFS).

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Add Location** | `addLocation(parentId, child)` | $O(N)$ | $O(H)$ | Searches for parent in $N$ nodes, then performs $O(1)$ child append. |
| **Find Location** | `findLocation(id)` | $O(N)$ | $O(H)$ | Explores branches recursively; recursion stack bounded by tree height $H$. |
| **Get Full Path** | `getFullPath(id, delimiter)` | $O(H)$ | $O(H)$ | Climbs `parent` pointers directly to root. For a 4-level campus tree ($H \le 4$), this is practically $O(1)$. |
| **BFS Traversal** | `bfsTraversal()` | $O(N)$ | $O(W)$ | Enqueues/dequeues each node once. Space bounded by maximum level width $W$. |
| **DFS Traversal** | `dfsTraversal()` | $O(N)$ | $O(H)$ | Pre-order exploration of all subtrees. Space bounded by height $H$. |

---

### 3.2. Custom Stack (Navigation History & Iterative Traversal)
* **Files:** `LocationStack.java`, `CampusNavigator.java`
* **Real-World Purpose:**
  1. **Breadcrumb / History Engine:** Dual-stack (`backStack` and `forwardStack`) navigation allowing students to navigate through campus nodes with back/forward history.
  2. **Iterative Traversal:** Eliminates JVM call-stack limitations by performing DFS iteratively on the heap via `dfsIterative()`.
* **Key Design Characteristics:**
  * Custom singly-linked `Node<E>` structure managing `top` pointer.
  * Zero dynamic array reallocation overhead; strictly $O(1)$ operations.

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Push** | `push(item)` | $O(1)$ | $O(1)$ | Instantiates new linked node and re-points `top`. |
| **Pop** | `pop()` | $O(1)$ | $O(1)$ | Extracts `top.data` and advances `top = top.next`. |
| **Peek** | `peek()` | $O(1)$ | $O(1)$ | Direct read from `top.data` without mutation. |
| **Iterative DFS** | `dfsIterative()` | $O(N)$ | $O(H)$ | Explicit LIFO stack traversal equivalent to recursive pre-order. |
| **Breadcrumbs** | `getBreadcrumbPath(sep)` | $O(K)$ | $O(K)$ | Formats $K$ stack history nodes into a linear path. |

---

### 3.3. Custom Trie (Location & Room Prefix Search)
* **Files:** `TrieNode.java`, `LocationTrie.java`
* **Real-World Purpose:** High-performance autocomplete search for classrooms, labs, and faculty rooms.
  * Query: `"b10"` $\to$ `B101 - Lecture Hall`, `B107 - Operating Systems Lab`, `B108 - Network Lab`.
  * Keyword Search: `"iot"` $\to$ `A202 - IoT Systems Lab`.
* **Key Design Characteristics:**
  * Each `TrieNode` dynamically branches characters and points directly to the corresponding `LocationNode`.
  * `indexCampusTree(tree)` crawls the campus tree and indexes every room ID and name token.

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Insert Key** | `insert(key, location)` | $O(L)$ | $O(L)$ | Traverses character by character; allocates nodes only for novel character paths ($L$ = key length). |
| **Search Exact** | `searchExact(key)` | $O(L)$ | $O(1)$ | Matches characters down to terminal node. |
| **Prefix Check** | `startsWith(prefix)` | $O(P)$ | $O(1)$ | Walks down prefix path of length $P$. |
| **Prefix Search** | `searchPrefix(prefix)` | $O(P + M)$ | $O(M)$ | Navigates prefix path ($O(P)$), then executes DFS subtree collection ($O(M)$ where $M$ is total matching characters). |
| **Tree Indexing** | `indexCampusTree(tree)` | $O(N \cdot L)$ | $O(N \cdot L)$ | Indexes $N$ locations with average keyword length $L$. |

---

### 3.4. Custom Queue (Pending Message Management)
* **Files:** `PendingMessageQueue.java`, `PeerMessageBuffer.java`
* **Real-World Purpose:** Disconnected peer message buffering. When a peer disconnects, outgoing messages are queued in a FIFO buffer and flushed in chronological order upon LAN reconnection.
* **Key Design Characteristics:**
  * Singly-linked list maintaining separate `head` (dequeue) and `tail` (enqueue) references.
  * `PeerMessageBuffer` maintains isolated, thread-safe queues per peer (`bob`, `alice`, `carol`).

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Enqueue** | `enqueue(item)` | $O(1)$ | $O(1)$ | Appends node to `tail.next`, updates `tail`. |
| **Dequeue** | `dequeue()` | $O(1)$ | $O(1)$ | Removes and advances `head = head.next`. |
| **Peek** | `peek()` | $O(1)$ | $O(1)$ | Inspects `head.data`. |
| **Buffer Message**| `bufferMessage(peer, msg)` | $O(1)$ | $O(1)$ | Enqueues to peer-specific FIFO queue. |
| **Flush Batch** | `flushPending(peer)` | $O(K)$ | $O(K)$ | Drains $K$ pending messages in chronological sequence. |

---

### 3.5. Hash Map (Peer Presence & Online Status Registry)
* **Files:** `PeerRegistry.java`
* **Real-World Purpose:** Tracks the dynamic availability state (`ONLINE`, `OFFLINE`, `AWAY`, `UNKNOWN`), IP address, and port of all campus peers discovered via UDP broadcasts or TCP handshakes.
* **Key Design Characteristics:**
  * Fast $O(1)$ lookup by peer ID regardless of network size.
  * Thread-safe snapshot methods for UI and CLI queries (`getOnlinePeers()`, `getAllPeers()`).

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Register** | `register(peerId, ip, port)` | $O(1)$ avg | $O(1)$ | Hashes peer ID string; inserts or updates entry. |
| **Status Change**| `markOnline / markOffline` | $O(1)$ avg | $O(1)$ | Updates presence enum and timestamp. |
| **Lookup** | `getPeer(peerId)` | $O(1)$ avg | $O(1)$ | Direct bucket hash lookup. |
| **Query Online**| `getOnlinePeers()` | $O(P)$ | $O(P)$ | Filters entries to produce collection of active peers ($P$ = peer count). |

---

### 3.6. Priority Queue Min-Heap (Message Priority & Scheduling)
* **Files:** `PriorityMessageQueue.java`, `PriorityMessage.java`, `MessagePriority.java`
* **Real-World Purpose:** Prioritizes critical campus emergency messages and administrative notifications (`URGENT` weight 1) over standard peer messages (`NORMAL` weight 2) and background file transfers (`LOW` weight 3).
* **Key Design Characteristics:**
  * Custom dynamic array-backed binary Min-Heap.
  * Parent at $\lfloor(i-1)/2\rfloor$, children at $2i+1$ and $2i+2$.
  * Preserves FIFO tie-breaking for equal priority levels via monotonic sequence numbering.

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Insert** | `insert(msg, priority)` | $O(\log K)$ | $O(1)$ | Appends to heap array, then sifts up. |
| **Extract Min** | `extractMin()` | $O(\log K)$ | $O(1)$ | Swaps root with last element, pops, then sifts down. |
| **Peek Min** | `peekMin()` | $O(1)$ | $O(1)$ | Directly inspects root at index 0. |
| **Sift Up / Down**| Internal rebalancing | $O(\log K)$ | $O(1)$ | Height of binary heap is $\lfloor\log_2 K\rfloor$. |

---

### 3.7. Doubly-Linked List (Conversation History Thread)
* **Files:** `ChatNode.java`, `ConversationThread.java`, `ConversationManager.java`
* **Real-World Purpose:** Maintains ordered chat conversation threads per peer for messaging and UI scrolling.
* **Key Design Characteristics:**
  * Bidirectional links (`prev` and `next`) with dummy `head` and `tail` sentinel nodes to eliminate edge-case pointer bugs.
  * $O(1)$ insertion at tail for incoming/outgoing messages.
  * $O(K)$ reverse traversal for pagination (fetching last $K$ messages for UI).

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Append** | `append(message)` | $O(1)$ | $O(1)$ | Inserts before `tail` sentinel and adjusts 4 pointers. |
| **Get Latest** | `getLatest()` | $O(1)$ | $O(1)$ | Accesses `tail.prev.message`. |
| **Get Oldest** | `getOldest()` | $O(1)$ | $O(1)$ | Accesses `head.next.message`. |
| **Get Last N** | `getLastN(n)` | $O(N)$ | $O(N)$ | Walks backward from `tail` by $N$ steps, reverses into forward order. |
| **Get All** | `getAll()` | $O(M)$ | $O(M)$ | Forward linear scan from `head.next` to `tail.prev`. |

---

### 3.8. Graph with Adjacency List (Peer Network Topology & Relay Routing)
* **Files:** `PeerGraph.java`
* **Real-World Purpose:** Models the decentralized mesh network topology. Tracks which peers are connected to which peers, visualizes the network map, and runs BFS to find the shortest relay path when peer A cannot directly communicate with peer C.
* **Key Design Characteristics:**
  * Adjacency list representation using vertex map with hash set neighbors.
  * Undirected edge model: if Alice connects to Bob, Bob is connected to Alice.
  * Breadth-First Search (BFS) computes the unweighted shortest relay path with minimal hop count.

| Operation | Method | Time Complexity | Auxiliary Space | Academic Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Add Vertex** | `addPeer(peerId)` | $O(1)$ avg | $O(1)$ | Inserts key into vertex adjacency map. |
| **Add Edge** | `addEdge(u, v)` | $O(1)$ avg | $O(1)$ | Adds $v$ to $u$'s neighbor set and $u$ to $v$'s set. |
| **Remove Edge** | `removeEdge(u, v)` | $O(1)$ avg | $O(1)$ | Removes from both adjacency sets upon disconnection. |
| **Remove Vertex**| `removePeer(peerId)` | $O(\text{deg}(v))$ | $O(\text{deg}(v))$ | Cleans up all incident edges across neighbors. |
| **BFS Shortest Path**| `bfsPath(start, dest)` | $O(V + E)$ | $O(V)$ | Classic level-by-level BFS queue exploration with `parentMap`. |
| **Reachability** | `isReachable(u, v)` | $O(V + E)$ | $O(V)$ | Determines if a connected component exists between $u$ and $v$. |

---

## 4. Test Suite & Verification Results

All 8 data structures include independent, standalone test suites runnable without external frameworks:

```powershell
javac *.java
java CampusTreeTest
java LocationStackTest
java LocationTrieTest
java PendingMessageQueueTest
java PeerRegistryTest
java PriorityMessageQueueTest
java ConversationTest
java PeerGraphTest
java MeshLinkTest
java CodecTest
```

### Cumulative Test Results:

| Test Suite | Data Structure / Module Tested | Tests Run | Passed | Failed |
| :--- | :--- | :--- | :--- | :--- |
| **`CampusTreeTest`** | General Tree (Hierarchy, Path, BFS/DFS) | 56 | 56 | 0 |
| **`LocationStackTest`** | Custom Stack & CampusNavigator (incl. 10k stress) | 10,054 | 10,054 | 0 |
| **`LocationTrieTest`** | Custom Trie & Room Prefix Autocomplete | 51 | 51 | 0 |
| **`PendingMessageQueueTest`** | Custom Queue & Offline Buffer (incl. 10k stress) | 10,048 | 10,048 | 0 |
| **`PeerRegistryTest`** | HashMap Peer Presence & Status Registry | 26 | 26 | 0 |
| **`PriorityMessageQueueTest`** | Binary Min-Heap Priority Queue | 25 | 25 | 0 |
| **`ConversationTest`** | Doubly-Linked List Conversation Thread | 32 | 32 | 0 |
| **`PeerGraphTest`** | Adjacency-List Graph & BFS Relay Routing | 31 | 31 | 0 |
| **`MeshLinkTest`** | End-to-End Message Store & Lifecycle | 42 | 42 | 0 |
| **`CodecTest`** | Wire Protocol Serialization / Deserialization | 8 | 8 | 0 |
| **TOTAL** | **Entire Application & DS Layer** | **20,353** | **20,353** | **0** |

---

## 5. Team Separation of Concerns Verification

- **Networking Layer (UDP Discovery, TCP Sockets):** Preserved untouched.
- **Message Layer & Wire Protocol:** Preserved untouched.
- **Soham's Database / Persistence Layer:** Preserved untouched.
- **Ronak's GUI / Jay's Packaging:** Ready for integration with simple, clean APIs (`CampusTree`, `LocationTrie`, `CampusNavigator`, `PeerMessageBuffer`, `PeerRegistry`, `PriorityMessageQueue`, `ConversationManager`, `PeerGraph`).
