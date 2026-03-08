# 🎰 Sports Betting Settlement System

A complete event-driven sports betting settlement system built with **Spring Boot 4**, **Apache Kafka**, and **Apache RocketMQ**.

> **🚨 First Time Setup:** Run `chmod +x quick-start.sh test-flow.sh mvnw` before executing any scripts.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [Simulating a Bet Settlement](#-simulating-a-bet-settlement)
- [Sample Data](#-sample-data)
- [API Reference](#-api-reference)
- [Database Access](#-database-access)
- [Troubleshooting](#-troubleshooting)
- [Project Structure](#-project-structure)
- [Technology Stack](#-technology-stack)

---

## 🎯 Overview

This system processes sports betting settlements through an event-driven pipeline:

1. **REST API** receives event outcomes (game results)
2. **Kafka** streams the event to consumers
3. **Settlement Service** matches outcomes with pending bets
4. **RocketMQ** queues settlement notifications
5. **Database** stores final bet results

---

## 🏗 Architecture

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐     ┌─────────────┐     ┌──────────┐
│   REST API  │────▶│    Kafka    │────▶│ Settlement Logic │────▶│  RocketMQ   │────▶│ Database │
│   :9999     │     │  event-     │     │    - Match bets  │     │    bet-     │     │   H2     │
│             │     │  outcomes   │     │    - Calculate   │     │ settlements │     │          │
└─────────────┘     └─────────────┘     └──────────────────┘     └─────────────┘     └──────────┘
```

**Data Flow:**
- Event published → Kafka topic `event-outcomes`
- Kafka consumer processes event → finds matching bets
- Settlement created → sent to RocketMQ topic `bet-settlements`
- RocketMQ consumer → updates database with final status

---

## 📋 Prerequisites

Before starting, ensure you have installed:

| Tool | Version | Check Command |
|------|---------|---------------|
| **Java** | 17+ | `java -version` |
| **Maven** | 3.6+ | `mvn -version` |
| **Docker** | Latest | `docker --version` |
| **Docker Compose** | Latest | `docker-compose --version` |

---

## 🚀 Quick Start

### ⚠️ First Time Setup - Grant Execute Permissions

Before running any scripts, you must grant execute permissions:

```bash
chmod +x quick-start.sh test-flow.sh mvnw
```

### Option 1: Using Quick Start Script (Recommended)

```bash
./quick-start.sh
```

This script will:
1. Start Kafka and RocketMQ via Docker
2. Wait for services to initialize
3. Build the project
4. Start the Spring Boot application

### Option 2: Manual Setup

#### Step 1: Start Infrastructure Services

```bash
# Start Kafka and RocketMQ containers
docker-compose up -d

# Wait for services to be ready (approximately 30-45 seconds)
sleep 45

# Verify containers are running
docker ps
```

**Expected output:**
```
CONTAINER ID   IMAGE                     STATUS          PORTS
xxxx           apache/kafka:latest       Up X minutes    0.0.0.0:9092-9093->9092-9093/tcp
xxxx           apache/rocketmq:5.3.2     Up X minutes    0.0.0.0:9876->9876/tcp
xxxx           apache/rocketmq:5.3.2     Up X minutes    0.0.0.0:10909-10912->10909-10912/tcp
xxxx           apache/rocketmq:5.3.2     Up X minutes    0.0.0.0:8080-8081->8080-8081/tcp
```

#### Step 2: Build the Application

```bash
# Build without running tests
./mvnw clean install -DskipTests
```

#### Step 3: Run the Application

```bash
./mvnw spring-boot:run
```

**Application is ready when you see:**
```
Started SportyTaskApplication in X.XXX seconds
```

**Application URL:** `http://localhost:9999`

---

## 🎲 Simulating a Bet Settlement

### Understanding the Test Data

The system comes pre-loaded with 5 pending bets:

| Bet ID | User ID | Event ID | Bet On (Winner ID) | Amount | Status |
|--------|---------|----------|-------------------|--------|--------|
| 1 | 1001 | **100** | **201** | $50.00 | PENDING |
| 2 | 1002 | **100** | 202 | $100.00 | PENDING |
| 3 | 1003 | **100** | **201** | $75.00 | PENDING |
| 4 | 1004 | 101 | 301 | $200.00 | PENDING |
| 5 | 1005 | 101 | **302** | $150.00 | PENDING |

### Simulation Scenario 1: Event 100 - Winner is 201

**Publish the event outcome:**

```bash
curl -X POST http://localhost:9999/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": 100,
    "eventName": "Champions League Final - Team A vs Team B",
    "eventWinnerId": 201
  }'
```

**Expected Response:**
```
Event published successfully
```

**What happens:**
| Bet ID | User's Pick | Actual Winner | Result | Payout |
|--------|-------------|---------------|--------|--------|
| 1 | 201 | 201 | ✅ **WON** | $100.00 (2x) |
| 2 | 202 | 201 | ❌ **LOST** | $0.00 |
| 3 | 201 | 201 | ✅ **WON** | $150.00 (2x) |

### Simulation Scenario 2: Event 101 - Winner is 302

```bash
curl -X POST http://localhost:9999/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": 101,
    "eventName": "Premier League - Team C vs Team D",
    "eventWinnerId": 302
  }'
```

**What happens:**
| Bet ID | User's Pick | Actual Winner | Result | Payout |
|--------|-------------|---------------|--------|--------|
| 4 | 301 | 302 | ❌ **LOST** | $0.00 |
| 5 | 302 | 302 | ✅ **WON** | $300.00 (2x) |

### Using the Automated Test Script

```bash
./test-flow.sh
```

This script automatically:
1. Publishes Event 100 with Winner 201
2. Waits for processing
3. Publishes Event 101 with Winner 302
4. Provides instructions for verifying results

---

## 📊 Verifying Results

### Option 1: Check Application Logs

Look for settlement messages in the terminal:

```
INFO  - Processing event outcome: eventId=100, winner=201
INFO  - Found 3 pending bets for event 100
INFO  - Bet 1: WON - Payout: 100.00
INFO  - Bet 2: LOST - Payout: 0.00
INFO  - Bet 3: WON - Payout: 150.00
INFO  - Settlement sent to RocketMQ for bet 1
```

### Option 2: H2 Database Console

1. Open browser: **http://localhost:9999/h2-console**
2. Enter connection details:
   - **JDBC URL:** `jdbc:h2:mem:sportytask`
   - **Username:** `sa`
   - **Password:** `sa`
3. Click **Connect**

**Query settled bets:**
```sql
SELECT * FROM bet WHERE status != 'PENDING';
```

**Query settlements:**
```sql
SELECT * FROM bet_settlement ORDER BY settlement_id;
```

**Full summary query:**
```sql
SELECT 
    b.bet_id,
    b.user_id,
    b.event_id,
    b.event_winner_id AS user_pick,
    bs.event_winner_id AS actual_winner,
    b.bet_amount,
    bs.settlement_amount AS payout,
    bs.status AS result
FROM bet b
JOIN bet_settlement bs ON b.bet_id = bs.bet_id
ORDER BY b.event_id, b.bet_id;
```

---

## 🎮 Sample Data Reference

### Pre-loaded Bets (schema.sql)

```sql
-- Event 100: Champions League Final
INSERT INTO bet (user_id, event_id, event_market_id, event_winner_id, bet_amount, status)
VALUES
    (1001, 100, 5001, 201, 50.00, 'PENDING'),   -- Bet on Team 201
    (1002, 100, 5001, 202, 100.00, 'PENDING'),  -- Bet on Team 202
    (1003, 100, 5002, 201, 75.00, 'PENDING');   -- Bet on Team 201

-- Event 101: Premier League Match
INSERT INTO bet (user_id, event_id, event_market_id, event_winner_id, bet_amount, status)
VALUES
    (1004, 101, 5003, 301, 200.00, 'PENDING'),  -- Bet on Team 301
    (1005, 101, 5003, 302, 150.00, 'PENDING');  -- Bet on Team 302
```

---

## 📡 API Reference

### Publish Event Outcome

Publishes the result of a sports event to trigger bet settlements.

**Endpoint:** `POST /api/events/publish`

**Request Body:**
```json
{
    "eventId": 100,
    "eventName": "Champions League Final - Team A vs Team B",
    "eventWinnerId": 201
}
```

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | `long` | Unique identifier for the sports event |
| `eventName` | `string` | Human-readable event name |
| `eventWinnerId` | `long` | ID of the winning team/outcome |

**Response:**
- **200 OK:** `"Event published successfully"`
- **500 Error:** Internal server error details

**Example with curl:**
```bash
curl -X POST http://localhost:9999/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{"eventId": 100, "eventName": "Test Event", "eventWinnerId": 201}'
```

---

## 💾 Database Access

### H2 Console

| Property | Value |
|----------|-------|
| **URL** | http://localhost:9999/h2-console |
| **JDBC URL** | `jdbc:h2:mem:sportytask` |
| **Username** | `sa` |
| **Password** | `sa` |

### Database Schema

**BET Table:**
| Column | Type | Description |
|--------|------|-------------|
| bet_id | BIGINT | Primary key |
| user_id | BIGINT | User who placed the bet |
| event_id | BIGINT | Sports event ID |
| event_market_id | BIGINT | Market/category ID |
| event_winner_id | BIGINT | User's predicted winner |
| bet_amount | DECIMAL | Wager amount |
| status | VARCHAR | PENDING, WON, LOST |
| placed_at | TIMESTAMP | When bet was placed |
| settled_at | TIMESTAMP | When bet was settled |

**BET_SETTLEMENT Table:**
| Column | Type | Description |
|--------|------|-------------|
| settlement_id | BIGINT | Primary key |
| bet_id | BIGINT | Reference to bet |
| user_id | BIGINT | User ID |
| event_id | BIGINT | Sports event ID |
| event_winner_id | BIGINT | Actual winner |
| bet_amount | DECIMAL | Original wager |
| settlement_amount | DECIMAL | Payout amount |
| status | ENUM | WON, LOST |
| settled_at | TIMESTAMP | Settlement time |

---

## 🔧 Troubleshooting

### Application Won't Start

**Check port availability:**
```bash
# Check if port 9999 is in use
lsof -i :9999

# Kill process if needed
kill -9 <PID>
```

### Kafka Connection Failed

**Verify Kafka is running:**
```bash
docker ps | grep broker
docker logs broker
```

**Restart Kafka:**
```bash
docker-compose restart kafka-broker
sleep 30
```

### RocketMQ Connection Failed

**Check RocketMQ services:**
```bash
docker ps | grep rmq
docker logs rmqnamesrv
docker logs rmqbroker
```

**Restart RocketMQ:**
```bash
docker-compose restart namesrv broker proxy
sleep 45
```

### No Bets Found for Event

**Verify data exists:**
```bash
# Access H2 Console and run:
SELECT COUNT(*) FROM bet WHERE status = 'PENDING';
# Should return 5
```

**Check if event ID matches:**
```sql
SELECT DISTINCT event_id FROM bet;
# Returns: 100, 101
```

### Full System Reset

```bash
# Stop everything
docker-compose down

# Remove volumes
docker-compose down -v

# Restart infrastructure
docker-compose up -d

# Wait for initialization
sleep 45

# Rebuild and run application
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

---

## 📁 Project Structure

```
sporty-task/
├── src/main/java/com/sportytask/
│   ├── SportyTaskApplication.java     # Main application entry
│   ├── api/
│   │   └── controller/
│   │       └── EventPublisherController.java  # REST endpoint
│   ├── domain/
│   │   ├── entities/
│   │   │   ├── Bet.java               # Bet entity
│   │   │   ├── BetSettlement.java     # Settlement entity
│   │   │   ├── BetStatus.java         # PENDING, WON, LOST
│   │   │   ├── Event.java             # Event record
│   │   │   └── SettlementStatus.java  # WON, LOST
│   │   ├── repository/                # JPA repositories
│   │   └── service/                   # Business logic
│   └── infrastructure/
│       ├── config/                    # Kafka & RocketMQ config
│       ├── consumer/                  # Message consumers
│       └── producer/                  # Message producers
├── src/main/resources/
│   ├── application.yaml               # Configuration
│   └── schema.sql                     # Database schema + test data
├── docker-compose.yaml                # Infrastructure setup
├── quick-start.sh                     # Quick start script
├── test-flow.sh                       # Test automation script
└── pom.xml                            # Maven dependencies
```

---

## 🛠 Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Framework** | Spring Boot | 4.0.3 |
| **Language** | Java | 17 |
| **Build Tool** | Maven | 3.6+ |
| **Messaging** | Apache Kafka | Latest |
| **Messaging** | Apache RocketMQ | 5.3.2 |
| **Database** | H2 (In-Memory) | Latest |
| **ORM** | Hibernate/JPA | - |
| **Container** | Docker | Latest |

---

## ⚙️ Configuration

### Application Ports

| Service | Port |
|---------|------|
| Spring Boot Application | 9999 |
| Kafka Broker | 9092 |
| RocketMQ NameServer | 9876 |
| RocketMQ Broker | 10909, 10911, 10912 |
| RocketMQ Proxy | 8080, 8081 |

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `event-outcomes` | Sports event results |

### RocketMQ Topics

| Topic | Purpose |
|-------|---------|
| `bet-settlements` | Settlement notifications |

---

## 🧪 Running Tests

```bash
# Run all tests
./mvnw test

# Run with verbose output
./mvnw test -X

# Run specific test class
./mvnw test -Dtest=BetRepositoryTest
```

---

## 📞 Support

For issues or questions:
1. Check the [Troubleshooting](#-troubleshooting) section
2. Review application logs for detailed error messages
3. Verify all Docker containers are running with `docker ps`

---

## 📄 License

This project was created for technical demonstration purposes.

---

**Last Updated:** March 2026  
**Version:** 1.0.0
