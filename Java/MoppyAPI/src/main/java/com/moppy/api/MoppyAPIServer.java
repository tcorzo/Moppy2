package com.moppy.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moppy.core.comms.bridge.NetworkBridge;
import com.moppy.core.status.StatusBus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class MoppyAPIServer {

    private final HttpServer server;
    private final PlaybackService playbackService;
    private final NetworkManager networkManager;
    private final ObjectMapper objectMapper;

    public MoppyAPIServer(int port) throws IOException {
        // Initialize core components
        StatusBus statusBus = new StatusBus();

        // Initialize network manager (replaces simple BridgeUDP)
        networkManager = new NetworkManager(statusBus);
        NetworkBridge<?> networkBridge = networkManager.getPrimaryBridge();

        playbackService = new PlaybackService(statusBus, networkBridge);
        objectMapper = new ObjectMapper();

        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        setupRoutes();
    }

    private void setupRoutes() {
        // Playback control endpoints
        server.createContext("/api/play", new PlayHandler());
        server.createContext("/api/pause", new PauseHandler());
        server.createContext("/api/stop", new StopHandler());
        server.createContext("/api/load", new LoadHandler());

        // State query endpoints
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/status", new StatusHandler());

        // Playback parameter endpoints
        server.createContext("/api/position", new PositionHandler());
        server.createContext("/api/tempo", new TempoHandler());
        server.createContext("/api/volume", new VolumeHandler());
        server.createContext("/api/loop", new LoopHandler());

        // Network management endpoints
        server.createContext("/api/network/status", new NetworkStatusHandler());
        server.createContext("/api/network/devices", new NetworkDevicesHandler());

        // Health check
        server.createContext("/api/health", new HealthHandler());

        // CORS preflight handler
        server.createContext("/api/", new CorsHandler());
    }

    public void start() {
        // Start the network manager first
        networkManager.start();

        server.start();
        System.out.println("Moppy API Server started on port " + server.getAddress().getPort());
        System.out.println("API endpoints available at http://localhost:" + server.getAddress().getPort() + "/api/");
        System.out.println(
                "Network manager started with " + networkManager.getConnectedBridgeCount() + " connected bridges");
    }

    public void stop() {
        try {
            playbackService.shutdown();
        } catch (IOException e) {
            System.err.println("Error shutting down playback service: " + e.getMessage());
        }

        // Stop the network manager
        try {
            networkManager.close();
        } catch (IOException e) {
            System.err.println("Error shutting down network manager: " + e.getMessage());
        }

        server.stop(5);
    }

    private void sendJsonResponse(HttpExchange exchange, Object response, int statusCode) throws IOException {
        addCorsHeaders(exchange);
        String jsonResponse = objectMapper.writeValueAsString(response);
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, String message, int statusCode) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", statusCode);
        sendJsonResponse(exchange, error, statusCode);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Handler classes
    private class PlayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            try {
                playbackService.play();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Playback started");
                sendJsonResponse(exchange, response, 200);
            } catch (Exception e) {
                sendErrorResponse(exchange, e.getMessage(), 400);
            }
        }
    }

    private class PauseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            playbackService.pause();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Playback paused");
            sendJsonResponse(exchange, response, 200);
        }
    }

    private class StopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            playbackService.stop();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Playback stopped");
            sendJsonResponse(exchange, response, 200);
        }
    }

    private class LoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            try {
                String requestBody = readRequestBody(exchange);
                @SuppressWarnings("unchecked")
                Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
                String filePath = (String) request.get("filePath");

                if (filePath == null || filePath.trim().isEmpty()) {
                    sendErrorResponse(exchange, "filePath is required", 400);
                    return;
                }

                playbackService.loadSong(filePath);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Song loaded successfully");
                response.put("fileName", playbackService.getState().getFileName());
                sendJsonResponse(exchange, response, 200);
            } catch (Exception e) {
                sendErrorResponse(exchange, e.getMessage(), 400);
            }
        }
    }

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            playbackService.updateState();
            sendJsonResponse(exchange, playbackService.getState(), 200);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            PlaybackState state = playbackService.getState();
            Map<String, Object> status = new HashMap<>();
            status.put("state", state.getPlaybackState());
            status.put("isPlaying", state.getPlaybackState() == PlaybackState.State.PLAYING);
            status.put("fileName", state.getFileName());
            status.put("progress", state.getProgress());
            status.put("position", state.getFormattedPosition());
            status.put("duration", state.getFormattedDuration());

            sendJsonResponse(exchange, status, 200);
        }
    }

    private class PositionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> response = new HashMap<>();
                response.put("position", playbackService.getState().getPosition());
                response.put("formattedPosition", playbackService.getState().getFormattedPosition());
                sendJsonResponse(exchange, response, 200);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
                    Integer position = (Integer) request.get("position");

                    if (position == null) {
                        sendErrorResponse(exchange, "position is required", 400);
                        return;
                    }

                    playbackService.setPosition(position);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("position", position);
                    sendJsonResponse(exchange, response, 200);
                } catch (Exception e) {
                    sendErrorResponse(exchange, e.getMessage(), 400);
                }
            } else {
                sendErrorResponse(exchange, "Method not allowed", 405);
            }
        }
    }

    private class TempoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> response = new HashMap<>();
                response.put("tempo", playbackService.getState().getTempo());
                sendJsonResponse(exchange, response, 200);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
                    Number tempoNumber = (Number) request.get("tempo");

                    if (tempoNumber == null) {
                        sendErrorResponse(exchange, "tempo is required", 400);
                        return;
                    }

                    float tempo = tempoNumber.floatValue();
                    playbackService.setTempo(tempo);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("tempo", tempo);
                    sendJsonResponse(exchange, response, 200);
                } catch (Exception e) {
                    sendErrorResponse(exchange, e.getMessage(), 400);
                }
            } else {
                sendErrorResponse(exchange, "Method not allowed", 405);
            }
        }
    }

    private class VolumeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> response = new HashMap<>();
                response.put("volume", playbackService.getState().getVolume());
                sendJsonResponse(exchange, response, 200);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
                    Number volumeNumber = (Number) request.get("volume");

                    if (volumeNumber == null) {
                        sendErrorResponse(exchange, "volume is required", 400);
                        return;
                    }

                    double volume = volumeNumber.doubleValue();
                    playbackService.setVolume(volume);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("volume", volume);
                    sendJsonResponse(exchange, response, 200);
                } catch (Exception e) {
                    sendErrorResponse(exchange, e.getMessage(), 400);
                }
            } else {
                sendErrorResponse(exchange, "Method not allowed", 405);
            }
        }
    }

    private class LoopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                Map<String, Object> response = new HashMap<>();
                response.put("loop", playbackService.getState().isLoop());
                sendJsonResponse(exchange, response, 200);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                try {
                    String requestBody = readRequestBody(exchange);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
                    Boolean loop = (Boolean) request.get("loop");

                    if (loop == null) {
                        sendErrorResponse(exchange, "loop is required", 400);
                        return;
                    }

                    playbackService.setLoop(loop);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("loop", loop);
                    sendJsonResponse(exchange, response, 200);
                } catch (Exception e) {
                    sendErrorResponse(exchange, e.getMessage(), 400);
                }
            } else {
                sendErrorResponse(exchange, "Method not allowed", 405);
            }
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            Map<String, Object> health = new HashMap<>();
            health.put("status", "healthy");
            health.put("service", "MoppyAPI");
            health.put("version", "2.2.0");
            sendJsonResponse(exchange, health, 200);
        }
    }

    private class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(200, -1);
            }
        }
    }

    private class NetworkStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            Map<String, Object> networkStatus = new HashMap<>();
            networkStatus.put("isStarted", networkManager.isStarted());
            networkStatus.put("connectedBridges", networkManager.getConnectedBridgeCount());
            networkStatus.put("discoveredDevices", networkManager.getDiscoveredDeviceCount());
            networkStatus.put("availableBridges", networkManager.getAvailableNetworkBridges().keySet());

            sendJsonResponse(exchange, networkStatus, 200);
        }
    }

    private class NetworkDevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, "Method not allowed", 405);
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("devices", networkManager.getRecentlySeenDevices());
            response.put("count", networkManager.getDiscoveredDeviceCount());

            sendJsonResponse(exchange, response, 200);
        }
    }

    public static void main(String[] args) {
        int port = 8080;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[i + 1]);
                    System.exit(1);
                }
                break;
            }
        }

        try {
            MoppyAPIServer server = new MoppyAPIServer(port);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down Moppy API Server...");
                server.stop();
            }));

            server.start();

            System.out.println("\nAPI Endpoints:");
            System.out.println("  POST /api/load     - Load a MIDI file");
            System.out.println("  POST /api/play     - Start playback");
            System.out.println("  POST /api/pause    - Pause playback");
            System.out.println("  POST /api/stop     - Stop playback");
            System.out.println("  GET  /api/state    - Get full playback state");
            System.out.println("  GET  /api/status   - Get playback status");
            System.out.println("  GET/PUT /api/position - Get/set playback position");
            System.out.println("  GET/PUT /api/tempo    - Get/set tempo");
            System.out.println("  GET/PUT /api/volume   - Get/set volume");
            System.out.println("  GET/PUT /api/loop     - Get/set loop mode");
            System.out.println("  GET  /api/network/status  - Get network status");
            System.out.println("  GET  /api/network/devices - Get discovered devices");
            System.out.println("  GET  /api/health   - Health check");
            System.out.println("\nPress Ctrl+C to stop");

            // Keep the main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Failed to start Moppy API Server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
