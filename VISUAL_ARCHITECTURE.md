# Visual Architecture - Sports Betting Settlement System

## System Overview Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT / API CONSUMER                             │
│                       (Postman, cURL, Frontend App)                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP POST
                                    │ /api/events/publish
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SPRING BOOT APPLICATION                            │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    1. API LAYER (Controllers)                         │  │
│  │                                                                       │  │
│  │   EventPublisherController                                           │  │
│  │   • POST /api/events/publish                                         │  │
│  │   • Validates input                                                  │  │
│  │   • Returns confirmation                                             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │               2. APPLICATION LAYER (Services)                        │  │
│  │                                                                       │  │
│  │   EventService                    BetSettlementService               │  │
│  │   • Publishes to Kafka           • Process event outcomes            │  │
│  │                                   • Match with bets                   │  │
│  │                                   • Calculate payouts                 │  │
│  │                                   • Update settlements                │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                 3. DOMAIN LAYER (Business Logic)                     │  │
│  │                                                                       │  │
│  │   Entities:                    Repositories:                         │  │
│  │   • Bet                        • BetRepository                       │  │
│  │   • BetSettlement             • BetSettlementRepository              │  │
│  │   • Event                                                            │  │
│  │                                                                       │  │
│  │   Enums:                                                             │  │
│  │   • BetStatus (PENDING/WON/LOST)                                    │  │
│  │   • SettlementStatus (WON/LOST)                                     │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    ↓                                        │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │            4. INFRASTRUCTURE LAYER (External Systems)                │  │
│  │                                                                       │  │
│  │   Kafka:                       RocketMQ:                             │  │
│  │   • EventMessageProducer      • BetSettlementProducer                │  │
│  │   • EventMessageConsumer      • BetSettlementConsumer                │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                    ↓                                      ↓
       ┌─────────────────────────┐          ┌──────────────────────────┐
       │    APACHE KAFKA         │          │    APACHE ROCKETMQ       │
       │                         │          │                          │
       │  Topic: event-outcomes  │          │  Topic: bet-settlements  │
       │  Port: 9092             │          │  NameServer: 9876        │
       └─────────────────────────┘          │  Broker: 10911           │
                                            └──────────────────────────┘
                                                        
                    ↓
       ┌─────────────────────────────────────────────┐
       │          H2 DATABASE (In-Memory)            │
       │                                             │
       │  Tables:                                    │
       │  • bet (bet records with status)            │
       │  • bet_settlement (settlement records)      │
       │                                             │
       │  Port: 8080/h2-console                      │
       └─────────────────────────────────────────────┘
```

---

## Message Flow Diagram

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│   API    │────▶│  Kafka   │────▶│  Kafka   │────▶│ Business │────▶│ RocketMQ │
│          │     │ Producer │     │ Consumer │     │  Logic   │     │ Producer │
└──────────┘     └──────────┘     └──────────┘     └──────────┘     └──────────┘
     │                │                 │                │                │
     │ 1. POST        │ 2. Publish      │ 3. Consume     │ 4. Process     │ 5. Send
     │ Event          │ to Topic        │ Event          │ Settlements    │ Settlement
     │                │ event-outcomes  │                │                │ Messages
     ↓                ↓                 ↓                ↓                ↓

     ┌──────────┐     ┌──────────┐     ┌──────────┐
     │ RocketMQ │────▶│ RocketMQ │────▶│ Database │
     │ Consumer │     │ Listener │     │  Update  │
     └──────────┘     └──────────┘     └──────────┘
          │                │                │
          │ 6. Consume     │ 7. Process     │ 8. Persist
          │ Settlement     │ Settlement     │ Results
          ↓                ↓                ↓
```

---

## Detailed Settlement Flow

```
Step 1: Publish Event Outcome
┌────────────────────────────────────────────────────┐
│  POST /api/events/publish                          │
│  {                                                 │
│    "eventId": 100,                                 │
│    "eventName": "Champions League Final",          │
│    "eventWinnerId": 201                            │
│  }                                                 │
└────────────────────────────────────────────────────┘
                    ↓
Step 2: Kafka Producer
┌────────────────────────────────────────────────────┐
│  Topic: event-outcomes                             │
│  Key: "100"                                        │
│  Value: Event JSON                                 │
│  Status: SEND_OK                                   │
└────────────────────────────────────────────────────┘
                    ↓
Step 3: Kafka Consumer
┌────────────────────────────────────────────────────┐
│  @KafkaListener(topics = "event-outcomes")         │
│  Receives: Event[eventId=100, winnerId=201]        │
│  Delegates to: BetSettlementService                │
└────────────────────────────────────────────────────┘
                    ↓
Step 4: Query Pending Bets
┌────────────────────────────────────────────────────┐
│  SELECT * FROM bet                                 │
│  WHERE event_id = 100                              │
│    AND status = 'PENDING'                          │
│                                                    │
│  Results:                                          │
│  • Bet 1: userId=1001, betOn=201, amount=50.00    │
│  • Bet 2: userId=1002, betOn=202, amount=100.00   │
│  • Bet 3: userId=1003, betOn=201, amount=75.00    │
└────────────────────────────────────────────────────┘
                    ↓
Step 5: Process Each Bet
┌────────────────────────────────────────────────────┐
│  For Bet 1:                                        │
│  • User bet on 201                                 │
│  • Actual winner: 201                              │
│  • Result: WON ✅                                  │
│  • Payout: 50.00 * 2 = 100.00                     │
│                                                    │
│  For Bet 2:                                        │
│  • User bet on 202                                 │
│  • Actual winner: 201                              │
│  • Result: LOST ❌                                 │
│  • Payout: 0.00                                    │
│                                                    │
│  For Bet 3:                                        │
│  • User bet on 201                                 │
│  • Actual winner: 201                              │
│  • Result: WON ✅                                  │
│  • Payout: 75.00 * 2 = 150.00                     │
└────────────────────────────────────────────────────┘
                    ↓
Step 6: RocketMQ Producer (for each bet)
┌────────────────────────────────────────────────────┐
│  Topic: bet-settlements                            │
│  Message: BetSettlement {                          │
│    betId: 1,                                       │
│    userId: 1001,                                   │
│    eventId: 100,                                   │
│    status: WON,                                    │
│    settlementAmount: 100.00                        │
│  }                                                 │
│  Headers: betId=1, eventId=100, userId=1001        │
└────────────────────────────────────────────────────┘
                    ↓
Step 7: RocketMQ Consumer
┌────────────────────────────────────────────────────┐
│  @RocketMQMessageListener(                         │
│    topic = "bet-settlements"                       │
│  )                                                 │
│  Receives: BetSettlement                           │
│  Delegates to: BetSettlementService                │
└────────────────────────────────────────────────────┘
                    ↓
Step 8: Update Database
┌────────────────────────────────────────────────────┐
│  UPDATE bet                                        │
│  SET status = 'WON',                               │
│      settled_at = NOW()                            │
│  WHERE bet_id = 1;                                 │
│                                                    │
│  INSERT INTO bet_settlement                        │
│  VALUES (1, 1001, 100, 201, 50.00, 100.00,        │
│          'WON', NOW());                            │
│                                                    │
│  COMMIT;                                           │
└────────────────────────────────────────────────────┘
                    ↓
Step 9: Complete ✅
┌────────────────────────────────────────────────────┐
│  Log: Settlement completed successfully            │
│  betId=1, finalStatus=WON, amount=100.00           │
└────────────────────────────────────────────────────┘
```

---

## Database Schema Diagram

```
┌─────────────────────────────────────────────────────────┐
│                       BET TABLE                         │
├─────────────────────────────────────────────────────────┤
│  bet_id              BIGINT (PK, Auto Increment)        │
│  user_id             BIGINT (NOT NULL)                  │
│  event_id            BIGINT (NOT NULL)                  │
│  event_market_id     BIGINT (NOT NULL)                  │
│  event_winner_id     BIGINT (NOT NULL)                  │
│  bet_amount          DECIMAL(19,2) (NOT NULL)           │
│  status              VARCHAR(20) DEFAULT 'PENDING'      │
│  placed_at           TIMESTAMP DEFAULT CURRENT_TIME     │
│  settled_at          TIMESTAMP (nullable)               │
├─────────────────────────────────────────────────────────┤
│  INDEX: idx_event_status (event_id, status)             │
└─────────────────────────────────────────────────────────┘
                          │
                          │ 1:1
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  BET_SETTLEMENT TABLE                   │
├─────────────────────────────────────────────────────────┤
│  settlement_id       BIGINT (PK, Auto Increment)        │
│  bet_id              BIGINT (UNIQUE, FK to Bet)         │
│  user_id             BIGINT (NOT NULL)                  │
│  event_id            BIGINT (NOT NULL)                  │
│  event_winner_id     BIGINT (NOT NULL)                  │
│  bet_amount          DECIMAL(19,2) (NOT NULL)           │
│  settlement_amount   DECIMAL(19,2) (NOT NULL)           │
│  status              VARCHAR(20) (NOT NULL)             │
│  settled_at          TIMESTAMP (NOT NULL)               │
├─────────────────────────────────────────────────────────┤
│  INDEX: idx_event (event_id)                            │
│  INDEX: idx_user (user_id)                              │
│  FK: bet_id → bet.bet_id                                │
└─────────────────────────────────────────────────────────┘
```

---

## Component Interaction Diagram

```
EventPublisherController
          │
          │ calls
          ↓
    EventService
          │
          │ uses
          ↓
  EventMessageProducer ────┐
          │                │
          │                │ publishes to
          │                ↓
          │          [KAFKA TOPIC]
          │          event-outcomes
          │                │
          │                │ consumed by
          │                ↓
          │     EventMessageConsumer
          │                │
          │                │ delegates to
          │                ↓
          │     BetSettlementService ◄─────┐
          │                │                │
          │                │ queries        │
          │                ↓                │
          │         BetRepository           │
          │                │                │
          │                │ uses           │
          │                ↓                │
          │    BetSettlementProducer        │
          │                │                │
          │                │ publishes to   │
          │                ↓                │
          │         [ROCKETMQ TOPIC]        │
          │         bet-settlements         │
          │                │                │
          │                │ consumed by    │
          │                ↓                │
          │    BetSettlementConsumer        │
          │                │                │
          │                │ delegates to   │
          │                └────────────────┘
          │
          │ persists to
          ↓
    [H2 DATABASE]
    bet, bet_settlement
```

---

## Technology Stack Visualization

```
┌────────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                         │
│  • REST API (Spring WebFlux)                                   │
│  • Swagger/OpenAPI Documentation                               │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                         │
│  • Spring Boot 4.0.3                                           │
│  • Spring Context (Dependency Injection)                       │
│  • Transaction Management                                      │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                       BUSINESS LAYER                           │
│  • Domain Entities (JPA)                                       │
│  • Business Logic Services                                     │
│  • Repository Pattern (Spring Data JPA)                        │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                    INTEGRATION LAYER                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │  Spring Kafka    │  │  RocketMQ Spring │  │  Spring Data │ │
│  │  • Producer      │  │  • Producer      │  │  JPA         │ │
│  │  • Consumer      │  │  • Consumer      │  │  • Hibernate │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE LAYER                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │ Apache Kafka │  │   RocketMQ   │  │   H2 Database      │   │
│  │   (Docker)   │  │   (Docker)   │  │   (In-Memory)      │   │
│  └──────────────┘  └──────────────┘  └────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

---

## Deployment Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                        DOCKER HOST                            │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Container: broker (Kafka)                              │ │
│  │  Image: apache/kafka:latest                             │ │
│  │  Ports: 9092:9092, 9093:9093                            │ │
│  │  Network: kafka-network                                 │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Container: rocketmq-namesrv                            │ │
│  │  Image: apache/rocketmq:5.1.4                           │ │
│  │  Ports: 9876:9876                                       │ │
│  │  Network: kafka-network                                 │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Container: rocketmq-broker                             │ │
│  │  Image: apache/rocketmq:5.1.4                           │ │
│  │  Ports: 10911:10911, 10909:10909, 10912:10912          │ │
│  │  Network: kafka-network                                 │ │
│  │  Depends On: rocketmq-namesrv                           │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
└───────────────────────────────────────────────────────────────┘
                              ↕
                     (localhost network)
                              ↕
┌───────────────────────────────────────────────────────────────┐
│                        LOCAL HOST                             │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Spring Boot Application                                │ │
│  │  • Port: 8080                                           │ │
│  │  • Connects to Kafka: localhost:9092                    │ │
│  │  • Connects to RocketMQ: localhost:9876                 │ │
│  │  • H2 Database: In-Memory                               │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

---

## Sequence Diagram - Complete Flow

```
Client      Controller    Service    KafkaP    Kafka    KafkaC    Service    RocketP   RocketMQ   RocketC   Service    Database
  │             │            │          │         │         │         │          │         │          │         │          │
  │─POST────────▶│            │          │         │         │         │          │         │          │         │          │
  │ /publish    │            │          │         │         │         │          │         │          │         │          │
  │             │──publish───▶│          │         │         │         │          │         │          │         │          │
  │             │            │──send────▶│         │         │         │          │         │          │         │          │
  │             │            │          │──pub────▶│         │         │          │         │          │         │          │
  │             │            │          │◀─ack─────│         │         │          │         │          │         │          │
  │◀─200 OK─────│            │          │         │         │         │          │         │          │         │          │
  │             │            │          │         │──poll───▶│         │          │         │          │         │          │
  │             │            │          │         │         │─process─▶│          │         │          │         │          │
  │             │            │          │         │         │         │─query────▶│         │          │         │          │
  │             │            │          │         │         │         │◀─bets─────│         │          │         │          │
  │             │            │          │         │         │         │          │         │          │         │          │
  │             │            │          │         │         │         │─settle───▶│         │          │         │          │
  │             │            │          │         │         │         │          │─send────▶│          │         │          │
  │             │            │          │         │         │         │          │         │──pub─────▶│         │          │
  │             │            │          │         │         │         │          │◀─ack─────│          │         │          │
  │             │            │          │         │         │         │          │         │──poll─────▶│         │          │
  │             │            │          │         │         │         │          │         │          │─process─▶│          │
  │             │            │          │         │         │         │          │         │          │         │─update───▶│
  │             │            │          │         │         │         │          │         │          │         │◀─ok──────│
  │             │            │          │         │         │         │          │         │          │◀─done────│          │
```

---

## Performance & Scalability Considerations

```
┌────────────────────────────────────────────────────────────┐
│                    SCALABILITY POINTS                      │
└────────────────────────────────────────────────────────────┘

1. Kafka Partitions
   ┌─────────┐  ┌─────────┐  ┌─────────┐
   │ Part 0  │  │ Part 1  │  │ Part 2  │
   │ Event 1 │  │ Event 2 │  │ Event 3 │
   └─────────┘  └─────────┘  └─────────┘
        ↓            ↓            ↓
   ┌─────────┐  ┌─────────┐  ┌─────────┐
   │Consumer │  │Consumer │  │Consumer │
   │ Inst 1  │  │ Inst 2  │  │ Inst 3  │
   └─────────┘  └─────────┘  └─────────┘

2. RocketMQ Message Queues
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ Queue 0  │  │ Queue 1  │  │ Queue 2  │
   └──────────┘  └──────────┘  └──────────┘
        ↓             ↓             ↓
   ┌──────────┐  ┌──────────┐  ┌──────────┐
   │Consumer  │  │Consumer  │  │Consumer  │
   │ Thread 1 │  │ Thread 2 │  │ Thread 3 │
   └──────────┘  └──────────┘  └──────────┘

3. Database Connection Pool
   ┌─────────────────────────────┐
   │  HikariCP Connection Pool   │
   │  Max Size: 10 (default)     │
   │  Min Idle: 10               │
   │  Max Lifetime: 30 min       │
   └─────────────────────────────┘

4. Horizontal Scaling
   ┌─────────┐  ┌─────────┐  ┌─────────┐
   │  App    │  │  App    │  │  App    │
   │Instance │  │Instance │  │Instance │
   │    1    │  │    2    │  │    3    │
   └─────────┘  └─────────┘  └─────────┘
        └────────────┬────────────┘
                     ↓
            ┌─────────────────┐
            │  Load Balancer  │
            └─────────────────┘
```

---

This visual architecture document provides clear diagrams for:
- ✅ System overview
- ✅ Component interactions
- ✅ Message flow
- ✅ Database schema
- ✅ Technology stack
- ✅ Deployment architecture
- ✅ Sequence diagram
- ✅ Scalability considerations

Perfect for interview presentations and technical discussions!

