import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class SimpleWebServer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/uuid4write/", new Uuid4WriteHandler());
        server.createContext("/api/uuid7write/", new Uuid7WriteHandler());
        server.createContext("/api/uuid4read/", new Uuid4ReadHandler());
        server.createContext("/api/uuid7read/", new Uuid7ReadHandler());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Server started on port 8080 with virtual threads");
    }

    static class Uuid4WriteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                int nRows = Integer.parseInt(path.split("/")[3]);
		long totalDuration = 0;
		long duration = 0;
                try (Connection conn = getConnection()) {
                    for (int i = 0; i < nRows; i++) {
                        String uuid = UUID.randomUUID().toString();
                        String text = "Prev row took " + duration + " ns";
                        long startTime = System.nanoTime();
                        insertRow(conn, "uuidv4_table", uuid, text);
                        duration = (System.nanoTime() - startTime);
			totalDuration += duration;
                    }
		    long averageDuration = (totalDuration / nRows) / 1000;
                    sendResponse(exchange, 200, "Inserted " + nRows + " rows, average duration was " + averageDuration + " us");
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    static class Uuid7WriteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                int nRows = Integer.parseInt(path.split("/")[3]);
                try (Connection conn = getConnection()) {
                    long duration=0;
                    long totalDuration=0;
                    for (int i = 0; i < nRows; i++) {
                        String uuid = generateUUIDv7();
                        String text = "Prev row took " + duration + " ns";
                        long startTime = System.nanoTime();
                        insertRow(conn, "uuidv7_table", uuid, text);
                        duration = System.nanoTime() - startTime;
			totalDuration += duration;
		    }
                    long averageDuration = (totalDuration / nRows) / 1000L;
                    sendResponse(exchange, 200, "Inserted " + nRows + " rows, average duration was " + averageDuration + " us");
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    static class Uuid4ReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String[] pathParts = exchange.getRequestURI().getPath().split("/");
                long timestamp = Long.parseLong(pathParts[3]);
                int nRows = Integer.parseInt(pathParts[4]);

                try (Connection conn = getConnection()) {
                    List<Map<String, Object>> rows = readRows(conn, "uuidv4_table", timestamp, nRows);
                    String jsonResponse = objectMapper.writeValueAsString(rows);
                    sendResponse(exchange, 200, jsonResponse);
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }
    // Handler for GET /api/uuid7read/:timestamp/:nRows
    static class Uuid7ReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String[] pathParts = exchange.getRequestURI().getPath().split("/");
                long timestamp = Long.parseLong(pathParts[3]);
                int nRows = Integer.parseInt(pathParts[4]);

                try (Connection conn = getConnection()) {
                    List<Map<String, Object>> rows = readRows(conn, "uuidv7_table", timestamp, nRows);
                    String jsonResponse = objectMapper.writeValueAsString(rows);
                    sendResponse(exchange, 200, jsonResponse);
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "Database error: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private static Connection getConnection() throws SQLException {
        // Load JDBC info from environment variables
        String url = System.getenv("JDBC_URL");
        String user = System.getenv("JDBC_USER");
        String password = System.getenv("JDBC_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static void insertRow(Connection conn, String tableName, String uuid, String text) throws SQLException {
        String sql = "INSERT INTO " + tableName + " (id, text) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid);
            pstmt.setString(2, text);
            pstmt.executeUpdate();
        }
    }

    private static List<Map<String, Object>> readRows(Connection conn, String tableName, long timestamp, int nRows) throws SQLException {
        Timestamp sqlTimestamp = new Timestamp(timestamp * 1000);
        String sql = "SELECT * FROM " + tableName + " WHERE createdate >= ? ORDER BY createdate LIMIT ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, sqlTimestamp);
            pstmt.setInt(2, nRows);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("text", rs.getString("text"));
                    row.put("createdate", rs.getTimestamp("createdate"));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static String generateUUIDv7() {
        // Current timestamp in milliseconds (48 bits)
        long timestampMillis = Instant.now().toEpochMilli();
        
        // Generate 74 random bits for the remaining part
        Random random = new Random();
        long randomBits1 = random.nextLong();
        int randomBits2 = random.nextInt();

        // Format the UUIDv7: 48-bit timestamp + 4-bit version + 62 bits of randomness
        return String.format("%08x-%04x-%04x-%04x-%08x%04x",
                (timestampMillis >>> 16),                    // First 32 bits of the timestamp
                (timestampMillis & 0xFFFF),                  // Remaining 16 bits of the timestamp
                0x7000 | ((randomBits1 >>> 48) & 0x0FFF),    // Version 7 and random bits
                (randomBits1 >>> 32) & 0xFFFF,               // Random bits
                randomBits1 & 0xFFFFFFFFL,                   // More random bits
                randomBits2 & 0xFFFF                         // Remaining 16 random bits
        );
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

