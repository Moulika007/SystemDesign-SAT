# 🏆 SportPulse – Live Sports Streaming & Score Platform

A Java-based Live Sports Platform designed to search matches, view match details, track live scores, access team information, and receive match event updates through REST APIs and a responsive web dashboard.

This project is developed as part of the **Round 2 – Low-Level Design Challenge** and **Round 3 – High-Level System Design Challenge**.

## 📌 Project Overview

SportPulse provides a centralized platform for accessing live sports information. It demonstrates REST API design, concurrent request handling, in-memory caching, event messaging, and frontend-backend communication using Java.

### 🎯 Objectives

- Search matches by team name and sport.
- View complete match details.
- Retrieve live scores and match status.
- Access team information.
- View match events such as goals, wickets, and match completion.
- Update scores through REST APIs.
- Demonstrate caching and event-driven communication.
- Provide a browser-based dashboard for users.

## ✨ Features

- **Match Search:** Search matches using team names, venues, and sports.
- **Match Details:** View participating teams, venue, sport, score, and status.
- **Live Score API:** Retrieve current scores and match information.
- **Match Events:** View and publish match events.
- **Team Details:** Retrieve information about individual teams.
- **REST API Explorer:** Test available API endpoints directly from the dashboard.
- **Automatic Refresh:** Refresh match scores and events every three seconds.
- **Concurrent Requests:** Handle multiple HTTP requests using a thread pool.
- **In-Memory Cache:** Cache frequently accessed live scores.
- **Event Messaging:** Publish score updates and match events to a local message queue.
- **Responsive Dashboard:** Access the application through a web browser.

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Java | Backend development |
| Java HTTP Server | HTTP server and REST API handling |
| HTML | Dashboard structure |
| CSS | User interface styling |
| JavaScript | Frontend interactions and API calls |
| JSON | Request and response format |
| ConcurrentHashMap | Thread-safe in-memory data storage |
| BlockingQueue | Event messaging |
| ExecutorService | Concurrent request handling |

**Dependencies:** Java standard library only. No external frameworks or database installation is required for this demo.

## 🏗️ System Architecture

```text
              ┌──────────────────────┐
              │     User / Browser   │
              └──────────┬───────────┘
                         │
                   HTTP Requests
                         │
              ┌──────────▼───────────┐
              │   Java HTTP Server   │
              │      Port 8080       │
              └──────────┬───────────┘
                         │
              ┌──────────▼───────────┐
              │      REST APIs       │
              ├──────────────────────┤
              │ Match Search         │
              │ Match Details        │
              │ Live Scores          │
              │ Match Events         │
              │ Team Details         │
              └──────────┬───────────┘
                         │
              ┌──────────▼───────────┐
              │  In-Memory Storage   │
              │  Matches and Teams   │
              └──────────┬───────────┘
                         │
              ┌──────────▼───────────┐
              │ Cache + Event Queue  │
              └──────────────────────┘
```

*Note: This architecture represents the current single-process demonstration. Distributed databases, caches, and message brokers are proposed extensions for production deployment.*

## 📂 Project Structure

```text
SportPulse/
│
├── Main.java
├── README.md
└── .gitignore
```

The application is implemented in a single Java file, `Main.java`, which contains the server, data models, REST APIs, sample data, and embedded web dashboard.

## ⚙️ Prerequisites

- Java Development Kit (JDK) 11 or later.
- Visual Studio Code, IntelliJ IDEA, or another Java-compatible IDE.
- A modern web browser.
- Git for version control.

## 🚀 Installation and Execution

### Step 1: Clone the repository

```bash
git clone YOUR_GITHUB_REPOSITORY_URL
cd SportPulse
```

Replace `YOUR_GITHUB_REPOSITORY_URL` with your actual GitHub repository URL.

### Step 2: Compile the Java program

```bash
javac Main.java
```

### Step 3: Run the application

```bash
java Main
```

### Step 4: Open the dashboard

Open the following URL in your browser:

```text
http://localhost:8080
```

The application starts with sample teams and matches so that the APIs and dashboard can be tested immediately.

## 🔌 REST API Documentation

Base URL:

```text
http://localhost:8080
```

### 1. Search Matches

**Endpoint:** `GET /api/matches`

Example:

```text
GET /api/matches?query=India
```

Optional sport filter:

```text
GET /api/matches?sport=Cricket
```

Returns a list of matches matching the supplied filters.

### 2. Get Match Details

**Endpoint:** `GET /api/matches/{id}`

Example:

```text
GET /api/matches/101
```

Returns team names, sport, venue, scores, and match status.

### 3. Get Live Score

**Endpoint:** `GET /api/matches/{id}/score`

Example:

```text
GET /api/matches/101/score
```

Example response:

```json
{
  "matchId": 101,
  "team1": "India",
  "team2": "Australia",
  "score1": "156/4 (18.2 overs)",
  "score2": "0/0",
  "status": "LIVE"
}
```

The response also includes a `lastUpdated` timestamp.

### 4. Get Match Events

**Endpoint:** `GET /api/matches/{id}/events`

Example:

```text
GET /api/matches/101/events
```

Returns the available event history for the specified match.

### 5. Update Live Score

**Endpoint:** `POST /api/matches/{id}/score`

Example request body:

```json
{
  "score1": "165/4 (19 overs)",
  "score2": "0/0",
  "status": "LIVE"
}
```

Updates the supplied score fields, invalidates the cached score, and publishes a score-update event.

### 6. Publish a Match Event

**Endpoint:** `POST /api/matches/{id}/events`

Example request body:

```json
{
  "type": "WICKET",
  "description": "A batter has been dismissed"
}
```

Returns HTTP `201 Created` when the event is successfully published.

### 7. Get Team Details

**Endpoint:** `GET /api/teams/{id}`

Example:

```text
GET /api/teams/1
```

Returns the team's ID, name, sport, and country.

### 8. Health Check

**Endpoint:** `GET /api/health`

Checks whether the API server is responding.

## 📊 HTTP Status Codes

| Status Code | Description |
|---|---|
| `200 OK` | Successful GET or score update |
| `201 Created` | Match event created |
| `204 No Content` | OPTIONS request handled |
| `400 Bad Request` | Invalid match status or missing event fields |
| `404 Not Found` | Unknown match, team, or API endpoint |
| `405 Method Not Allowed` | Unsupported method on a non-API route |
| `500 Internal Server Error` | Unexpected server-side error |

## 🔄 Application Workflow

1. The user opens the SportPulse dashboard.
2. The browser requests match information from the Java backend.
3. The user searches for a match or selects a match card.
4. The application requests match details, live scores, or event history.
5. Score updates invalidate the relevant cache entry.
6. Match events are published to the in-memory messaging queue.
7. The dashboard refreshes match information and events automatically.

## 🧠 System Design Concepts Demonstrated

### Low-Level Design – Round 2

- RESTful API endpoints and HTTP methods.
- Match, Team, and Event data models.
- JSON request and response structures.
- HTTP status codes and error handling.
- Match search, score retrieval, and event publication.
- Match lifecycle and score update workflow.

### High-Level System Design – Round 3

- Separation of responsibilities through API handlers and data models.
- Concurrent request handling using a thread pool.
- Thread-safe in-memory data structures.
- Caching for frequently accessed live scores.
- Queue-based messaging for score updates and match events.
- Browser-to-backend HTTP communication.
- Architectural foundations for horizontal scaling.

## 📈 Future Enhancements

- Integrate a persistent database such as PostgreSQL or MySQL.
- Add Redis for distributed caching.
- Use Apache Kafka or RabbitMQ for distributed event messaging.
- Implement WebSockets or Server-Sent Events for push-based live updates.
- Add user authentication, authorization, and personalized notifications.
- Integrate a verified live sports data provider.
- Deploy multiple backend instances behind a load balancer.
- Introduce monitoring, logging, rate limiting, and automated tests.
- Use a CDN and distributed infrastructure for high-traffic sporting events.

## ⚠️ Limitations

- The current implementation uses sample data stored in memory.
- Data and events are lost when the server restarts.
- The cache and event queue are local to the running process.
- The dashboard polls for updates rather than using a persistent streaming connection.
- The application is a design challenge demonstration, not a production-ready system for millions of users.
- Score updates are manually submitted; a live sports data provider is not integrated.

## 👩‍💻 Author

**Moulika Selva**

Project: SportPulse – Live Sports Streaming & Score Platform

## 📄 License

This project is intended for educational and demonstration purposes. Add a suitable open-source license if you plan to distribute it publicly.
