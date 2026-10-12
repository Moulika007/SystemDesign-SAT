
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    // =====================================================
    // LIVE SPORTS STREAMING & SCORE PLATFORM
    // Single-file Java implementation for Round 2 and 3
    // =====================================================

    static final int PORT = 8080;

    static final Map<Integer, Match> matches = new ConcurrentHashMap<>();
    static final Map<Integer, Team> teams = new ConcurrentHashMap<>();
    static final Map<Integer, List<Event>> matchEvents = new ConcurrentHashMap<>();

    // Cache for frequently accessed live scores and match details
    static final Map<String, String> cache = new ConcurrentHashMap<>();

    // Messaging system for score updates and match events
    static final BlockingQueue<String> messageQueue =
            new LinkedBlockingQueue<>();

    static final AtomicLong eventCounter = new AtomicLong(100);
    static final ExecutorService executor =
            Executors.newFixedThreadPool(20);

    static class Team {
        int id;
        String name;
        String sport;
        String country;

        Team(int id, String name, String sport, String country) {
            this.id = id;
            this.name = name;
            this.sport = sport;
            this.country = country;
        }

        String json() {
            return "{\"id\":" + id
                    + ",\"name\":" + quote(name)
                    + ",\"sport\":" + quote(sport)
                    + ",\"country\":" + quote(country) + "}";
        }
    }

    static class Match {
        int id;
        int team1Id;
        int team2Id;
        String sport;
        String venue;
        String status;
        String score1;
        String score2;
        String startedAt;

        Match(int id, int team1Id, int team2Id, String sport,
              String venue, String status, String score1, String score2) {
            this.id = id;
            this.team1Id = team1Id;
            this.team2Id = team2Id;
            this.sport = sport;
            this.venue = venue;
            this.status = status;
            this.score1 = score1;
            this.score2 = score2;
            this.startedAt = Instant.now().toString();
        }

        String json() {
            return "{\"id\":" + id
                    + ",\"team1\":" + quote(teamName(team1Id))
                    + ",\"team2\":" + quote(teamName(team2Id))
                    + ",\"team1Id\":" + team1Id
                    + ",\"team2Id\":" + team2Id
                    + ",\"sport\":" + quote(sport)
                    + ",\"venue\":" + quote(venue)
                    + ",\"status\":" + quote(status)
                    + ",\"score1\":" + quote(score1)
                    + ",\"score2\":" + quote(score2)
                    + ",\"startedAt\":" + quote(startedAt) + "}";
        }
    }

    static class Event {
        long id;
        int matchId;
        String type;
        String description;
        String time;

        Event(int matchId, String type, String description) {
            this.id = eventCounter.incrementAndGet();
            this.matchId = matchId;
            this.type = type;
            this.description = description;
            this.time = Instant.now().toString();
        }

        String json() {
            return "{\"id\":" + id
                    + ",\"matchId\":" + matchId
                    + ",\"type\":" + quote(type)
                    + ",\"description\":" + quote(description)
                    + ",\"time\":" + quote(time) + "}";
        }
    }

    // -------------------- INITIAL DATA --------------------

    static void initialize() {
        teams.put(1, new Team(1, "India", "Cricket", "India"));
        teams.put(2, new Team(2, "Australia", "Cricket", "Australia"));
        teams.put(3, new Team(3, "Chennai FC", "Football", "India"));
        teams.put(4, new Team(4, "Mumbai FC", "Football", "India"));
        teams.put(5, new Team(5, "England", "Cricket", "England"));
        teams.put(6, new Team(6, "South Africa", "Cricket", "South Africa"));

        matches.put(101, new Match(
                101, 1, 2, "Cricket", "Chennai",
                "LIVE", "156/4 (18.2 overs)", "0/0"));

        matches.put(102, new Match(
                102, 3, 4, "Football", "Mumbai",
                "LIVE", "2", "1"));

        matches.put(103, new Match(
                103, 5, 6, "Cricket", "London",
                "UPCOMING", "0/0", "0/0"));

        matches.put(104, new Match(
                104, 1, 5, "Cricket", "Delhi",
                "COMPLETED", "185/7", "172/9"));

        for (Match match : matches.values()) {
            matchEvents.put(match.id, new CopyOnWriteArrayList<>());
        }

        addEvent(101, "MATCH_STARTED", "India vs Australia is live!");
        addEvent(102, "GOAL", "Chennai FC vs Mumbai FC: live match");
        addEvent(104, "MATCH_COMPLETED", "India won the match.");
    }

    static String teamName(int id) {
        Team team = teams.get(id);
        return team == null ? "Unknown Team" : team.name;
    }

    // -------------------- JSON HELPERS --------------------

    static String quote(String value) {
        if (value == null) return "null";

        StringBuilder result = new StringBuilder("\"");

        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 32) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.append("\"").toString();
    }

    static String error(String message) {
        return "{\"success\":false,\"error\":" + quote(message) + "}";
    }

    static String success(String message) {
        return "{\"success\":true,\"message\":" + quote(message) + "}";
    }

    static String listJson(Collection<?> items) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;

        for (Object item : items) {
            if (!first) result.append(",");
            first = false;

            if (item instanceof Match) {
                result.append(((Match) item).json());
            } else if (item instanceof Team) {
                result.append(((Team) item).json());
            } else if (item instanceof Event) {
                result.append(((Event) item).json());
            }
        }

        return result.append("]").toString();
    }

    static String scoreJson(Match match) {
        return "{\"matchId\":" + match.id
                + ",\"team1\":" + quote(teamName(match.team1Id))
                + ",\"team2\":" + quote(teamName(match.team2Id))
                + ",\"score1\":" + quote(match.score1)
                + ",\"score2\":" + quote(match.score2)
                + ",\"status\":" + quote(match.status)
                + ",\"lastUpdated\":" + quote(Instant.now().toString())
                + "}";
    }

    static void addEvent(int matchId, String type, String description) {
        List<Event> events = matchEvents.computeIfAbsent(
                matchId, key -> new CopyOnWriteArrayList<>());

        Event event = new Event(matchId, type, description);
        events.add(event);

        // Publish the event to the messaging queue.
        messageQueue.offer(event.json());

        // Keep event history bounded.
        while (events.size() > 500) {
            events.remove(0);
        }
    }

    // -------------------- HTTP HELPERS --------------------

    static void send(HttpExchange exchange, int status, String body)
            throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");

        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendHtml(HttpExchange exchange, String html)
            throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "text/html; charset=UTF-8");

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static Map<String, String> queryParams(String query) {
        Map<String, String> result = new HashMap<>();

        if (query == null || query.isEmpty()) return result;

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);

            try {
                String key = URLDecoder.decode(
                        parts[0], StandardCharsets.UTF_8.name());

                String value = parts.length > 1
                        ? URLDecoder.decode(
                                parts[1], StandardCharsets.UTF_8.name())
                        : "";

                result.put(key, value);
            } catch (Exception ignored) {
                // Ignore malformed query parameters.
            }
        }

        return result;
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String field(String json, String key) {
        // Small helper for the demo's simple JSON requests.
        String marker = "\"" + key + "\"";
        int index = json.indexOf(marker);

        if (index < 0) return null;

        index = json.indexOf(':', index + marker.length());
        if (index < 0) return null;

        index++;

        while (index < json.length()
                && Character.isWhitespace(json.charAt(index))) {
            index++;
        }

        if (index >= json.length() || json.charAt(index) != '"') {
            return null;
        }

        index++;
        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        while (index < json.length()) {
            char c = json.charAt(index++);

            if (escaped) {
                switch (c) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    default: value.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }

        return null;
    }

    static int pathId(String path, String prefix, String suffix) {
        try {
            String value = path.substring(prefix.length());

            if (suffix != null && value.endsWith(suffix)) {
                value = value.substring(
                        0, value.length() - suffix.length());
            }

            if (value.contains("/")) return -1;

            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }

    // -------------------- ROUND 2: REST APIs --------------------

    static void handleApi(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Map<String, String> params =
                queryParams(exchange.getRequestURI().getRawQuery());

        if ("OPTIONS".equals(method)) {
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // GET /api/matches?query=India&sport=Cricket
        if (path.equals("/api/matches") && method.equals("GET")) {
            String query = params.getOrDefault("query", "")
                    .toLowerCase(Locale.ROOT);

            String sport = params.getOrDefault("sport", "")
                    .toLowerCase(Locale.ROOT);

            List<Match> result = new ArrayList<>();

            for (Match match : matches.values()) {
                String searchable = (
                        teamName(match.team1Id) + " "
                        + teamName(match.team2Id) + " "
                        + match.venue
                ).toLowerCase(Locale.ROOT);

                boolean matchesQuery =
                        query.isEmpty() || searchable.contains(query);

                boolean matchesSport =
                        sport.isEmpty()
                        || match.sport.equalsIgnoreCase(sport);

                if (matchesQuery && matchesSport) {
                    result.add(match);
                }
            }

            send(exchange, 200, listJson(result));
            return;
        }

        // GET /api/matches/{id}/score
        if (path.startsWith("/api/matches/")
                && path.endsWith("/score")
                && method.equals("GET")) {

            int id = pathId(path, "/api/matches/", "/score");
            Match match = matches.get(id);

            if (match == null) {
                send(exchange, 404, error("Match not found"));
                return;
            }

            String key = "score:" + id;
            String score = cache.get(key);

            if (score == null) {
                score = scoreJson(match);
                cache.put(key, score);
            }

            send(exchange, 200, score);
            return;
        }

        // GET /api/matches/{id}/events
        if (path.startsWith("/api/matches/")
                && path.endsWith("/events")
                && method.equals("GET")) {

            int id = pathId(path, "/api/matches/", "/events");

            if (!matches.containsKey(id)) {
                send(exchange, 404, error("Match not found"));
                return;
            }

            List<Event> events = matchEvents.getOrDefault(
                    id, Collections.emptyList());

            send(exchange, 200, listJson(events));
            return;
        }

        // POST /api/matches/{id}/score
        // Example: {"score1":"160/4 (19 overs)","score2":"0/0","status":"LIVE"}
        if (path.startsWith("/api/matches/")
                && path.endsWith("/score")
                && method.equals("POST")) {

            int id = pathId(path, "/api/matches/", "/score");
            Match match = matches.get(id);

            if (match == null) {
                send(exchange, 404, error("Match not found"));
                return;
            }

            String body = readBody(exchange);

            synchronized (match) {
                String score1 = field(body, "score1");
                String score2 = field(body, "score2");
                String status = field(body, "status");

                if (score1 != null) match.score1 = score1;
                if (score2 != null) match.score2 = score2;

                if (status != null) {
                    if (!Arrays.asList(
                            "LIVE", "UPCOMING", "COMPLETED")
                            .contains(status.toUpperCase(Locale.ROOT))) {
                        send(exchange, 400,
                                error("Invalid match status"));
                        return;
                    }

                    match.status = status.toUpperCase(Locale.ROOT);
                }

                cache.remove("score:" + id);

                addEvent(id, "SCORE_UPDATE",
                        teamName(match.team1Id) + " "
                        + match.score1 + " - "
                        + teamName(match.team2Id) + " "
                        + match.score2);

                if ("COMPLETED".equals(match.status)) {
                    addEvent(id, "MATCH_COMPLETED",
                            "Match " + id + " has ended.");
                }

                send(exchange, 200, scoreJson(match));
            }

            return;
        }

        // GET /api/matches/{id}
        if (path.startsWith("/api/matches/")
                && method.equals("GET")) {

            int id = pathId(path, "/api/matches/", null);
            Match match = matches.get(id);

            if (match == null) {
                send(exchange, 404, error("Match not found"));
                return;
            }

            send(exchange, 200, match.json());
            return;
        }

        // GET /api/teams/{id}
        if (path.startsWith("/api/teams/")
                && method.equals("GET")) {

            int id = pathId(path, "/api/teams/", null);
            Team team = teams.get(id);

            if (team == null) {
                send(exchange, 404, error("Team not found"));
                return;
            }

            send(exchange, 200, team.json());
            return;
        }

        // POST /api/matches/{id}/events
        // Example: {"type":"WICKET","description":"Batter dismissed"}
        if (path.startsWith("/api/matches/")
                && path.endsWith("/events")
                && method.equals("POST")) {

            int id = pathId(path, "/api/matches/", "/events");

            if (!matches.containsKey(id)) {
                send(exchange, 404, error("Match not found"));
                return;
            }

            String body = readBody(exchange);
            String type = field(body, "type");
            String description = field(body, "description");

            if (type == null || type.isBlank()
                    || description == null || description.isBlank()) {
                send(exchange, 400,
                        error("type and description are required"));
                return;
            }

            addEvent(id, type, description);

            send(exchange, 201,
                    success("Match event published"));
            return;
        }

        // GET /api/health
        if (path.equals("/api/health") && method.equals("GET")) {
            send(exchange, 200,
                    "{\"status\":\"UP\",\"service\":\"Sports Platform\"}");
            return;
        }

        // Unknown routes / unsupported HTTP methods
        if (path.startsWith("/api/")) {
            send(exchange, 404, error("API endpoint not found"));
        } else {
            send(exchange, 405, error("Method not allowed"));
        }
    }

    // -------------------- WEB FRONTEND --------------------

    static String dashboard() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SportPulse | Live Sports</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:Arial,sans-serif;background:#0b1020;color:#f5f7ff}
header{padding:24px 6%;background:#121a30;display:flex;
justify-content:space-between;align-items:center;gap:15px;flex-wrap:wrap}
.brand{font-size:26px;font-weight:bold;color:#63f5bc}
header span{color:#aab5d0}
main{max-width:1150px;margin:auto;padding:28px 18px}
.hero{padding:25px;border-radius:18px;background:linear-gradient(120deg,#182e4c,#17223c)}
.hero h1{margin-top:0;font-size:32px}
.hero p{color:#c3cde2}
.controls{display:flex;gap:10px;flex-wrap:wrap;margin:22px 0}
input,select,button{padding:12px;border-radius:9px;border:1px solid #34435f;
background:#17223c;color:white;font-size:14px}
input{flex:1;min-width:170px}
button{cursor:pointer;background:#19c997;color:#061b18;
font-weight:bold;border:0}
button:hover{filter:brightness(1.1)}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));
gap:14px;margin:22px 0}
.stat,.card{background:#141d32;border:1px solid #283650;
border-radius:14px;padding:18px}
.stat strong{display:block;font-size:27px;margin-top:9px}
.muted{color:#9eabc6}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));
gap:16px}
.card h3{margin-top:0}
.score{font-size:23px;font-weight:bold;margin:16px 0}
.pill{display:inline-block;padding:5px 9px;border-radius:20px;
background:#174936;color:#7fffc8;font-size:12px}
.pill.upcoming{background:#493c20;color:#ffdc85}
.pill.completed{background:#29344c;color:#c2cce0}
.actions{display:flex;gap:8px;flex-wrap:wrap}
.actions button{padding:9px 11px}
section{margin-top:30px}
.event{padding:12px 0;border-bottom:1px solid #283650;color:#cbd4e8}
pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#0a1020;
padding:14px;border-radius:8px;color:#8fffd2}
footer{text-align:center;padding:25px;color:#8290ad}
</style>
</head>
<body>
<header>
  <div class="brand">SPORTPULSE ⚡</div>
  <span>LIVE SPORTS • REAL-TIME SCORES</span>
</header>
<main>
  <div class="hero">
    <h1>Every Match. Every Moment.</h1>
    <p>Search matches, follow live scores, view match events and team details.</p>
    <div class="controls">
      <input id="search" placeholder="Search teams or venue...">
      <select id="sport">
        <option value="">All Sports</option>
        <option>Cricket</option>
        <option>Football</option>
      </select>
      <button onclick="loadMatches()">Search Matches</button>
      <button onclick="loadMatches()">↻ Refresh</button>
    </div>
  </div>

  <div class="stats">
    <div class="stat"><span class="muted">Total Matches</span>
      <strong id="total">0</strong></div>
    <div class="stat"><span class="muted">Live Matches</span>
      <strong id="live">0</strong></div>
    <div class="stat"><span class="muted">Upcoming</span>
      <strong id="upcoming">0</strong></div>
  </div>

  <section>
    <h2>🏆 Matches</h2>
    <div id="matches" class="grid">Loading matches...</div>
  </section>

  <section>
    <h2>📣 Match Events & Notifications</h2>
    <p class="muted">Updates are refreshed automatically every 3 seconds.</p>
    <div id="events" class="card">Select a match to see its events.</div>
  </section>

  <section>
    <h2>🔌 REST API Explorer</h2>
    <p class="muted">Choose an endpoint to inspect its response.</p>
    <div class="controls">
      <select id="endpoint">
        <option value="/api/matches">GET /api/matches</option>
        <option value="/api/matches/101">GET Match Details</option>
        <option value="/api/matches/101/score">GET Live Score</option>
        <option value="/api/matches/101/events">GET Match Events</option>
        <option value="/api/teams/1">GET Team Details</option>
        <option value="/api/health">GET Health Status</option>
      </select>
      <button onclick="exploreApi()">Send Request</button>
    </div>
    <pre id="apiResult">API responses will appear here.</pre>
  </section>
</main>
<footer>SportPulse • Java REST API Demo • Round 2 & Round 3</footer>

<script>
let selectedMatch = 101;

async function api(url, options) {
  const response = await fetch(url, options || {});
  const data = await response.json();
  return {status: response.status, data: data};
}

async function loadMatches() {
  try {
    const q = document.getElementById('search').value;
    const sport = document.getElementById('sport').value;
    const params = new URLSearchParams();
    if (q) params.set('query', q);
    if (sport) params.set('sport', sport);

    const result = await api('/api/matches?' + params.toString());
    const list = result.data;

    document.getElementById('total').textContent = list.length;
    document.getElementById('live').textContent =
      list.filter(m => m.status === 'LIVE').length;
    document.getElementById('upcoming').textContent =
      list.filter(m => m.status === 'UPCOMING').length;

    const container = document.getElementById('matches');

    if (!list.length) {
      container.textContent = 'No matches found.';
      return;
    }

    container.innerHTML = list.map(m => `
      <div class="card">
        <span class="pill ${m.status.toLowerCase()}">${m.status}</span>
        <h3>${escapeHtml(m.team1)} vs ${escapeHtml(m.team2)}</h3>
        <div class="muted">${escapeHtml(m.sport)} • ${escapeHtml(m.venue)}</div>
        <div class="score">${escapeHtml(m.score1)} : ${escapeHtml(m.score2)}</div>
        <div class="actions">
          <button onclick="details(${m.id})">Details</button>
          <button onclick="showScore(${m.id})">Live Score</button>
          <button onclick="showEvents(${m.id})">Events</button>
          <button onclick="showTeam(${m.team1Id})">Team Details</button>
        </div>
      </div>`).join('');
  } catch (e) {
    document.getElementById('matches').textContent =
      'Unable to load matches. Check that the Java server is running.';
  }
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, c => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;',
    '"':'&quot;', "'":'&#39;'
  }[c]));
}

async function details(id) {
  selectedMatch = id;
  await explore('/api/matches/' + id);
}

async function showScore(id) {
  selectedMatch = id;
  await explore('/api/matches/' + id + '/score');
}

async function showEvents(id) {
  selectedMatch = id;
  await explore('/api/matches/' + id + '/events');
  await refreshEvents();
}

async function showTeam(id) {
  await explore('/api/teams/' + id);
}

async function explore(url) {
  try {
    const result = await api(url);
    document.getElementById('endpoint').value = url;
    document.getElementById('apiResult').textContent =
      'HTTP ' + result.status + '\\n' +
      JSON.stringify(result.data, null, 2);
  } catch (e) {
    document.getElementById('apiResult').textContent = String(e);
  }
}

async function exploreApi() {
  await explore(document.getElementById('endpoint').value);
}

async function refreshEvents() {
  try {
    const result = await api('/api/matches/' + selectedMatch + '/events');
    document.getElementById('events').innerHTML =
      result.data.length
        ? result.data.slice().reverse().map(e =>
          `<div class="event"><b>${escapeHtml(e.type)}</b><br>
          ${escapeHtml(e.description)}<br>
          <small class="muted">${escapeHtml(e.time)}</small></div>`
          ).join('')
        : 'No events recorded for this match.';
  } catch (e) {
    document.getElementById('events').textContent = 'Unable to load events.';
  }
}

document.getElementById('search').addEventListener('keydown', e => {
  if (e.key === 'Enter') loadMatches();
});

document.getElementById('sport').addEventListener('change', loadMatches);

loadMatches();
refreshEvents();

setInterval(() => {
  loadMatches();
  refreshEvents();
}, 3000);
</script>
</body>
</html>
""";
    }

    // -------------------- SERVER ENTRY POINT --------------------

    public static void main(String[] args) throws Exception {
        initialize();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT), 0);

        server.setExecutor(executor);

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();

            try {
                if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                    sendHtml(exchange, dashboard());
                } else if (path.startsWith("/api/")) {
                    handleApi(exchange);
                } else {
                    send(exchange, 404, error("Resource not found"));
                }
            } catch (Exception e) {
                try {
                    send(exchange, 500, error("Internal server error"));
                } catch (Exception ignored) {
                    exchange.close();
                }

                System.err.println("Request error: " + e.getMessage());
            }
        });

        // Demonstration consumer for the message queue.
        Thread consumer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = messageQueue.take();
                    System.out.println("[EVENT BUS] " + message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        consumer.setDaemon(true);
        consumer.start();

        server.start();

        System.out.println("========================================");
        System.out.println(" SPORTPULSE LIVE SPORTS PLATFORM");
        System.out.println("========================================");
        System.out.println("Website: http://localhost:" + PORT);
        System.out.println("Matches: http://localhost:" + PORT + "/api/matches");
        System.out.println("Live Score: http://localhost:" + PORT
                + "/api/matches/101/score");
        System.out.println("Health: http://localhost:" + PORT + "/api/health");
        System.out.println("Press Ctrl+C to stop.");
    }
}
