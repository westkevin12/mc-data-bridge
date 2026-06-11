package com.digitalserverhost.plugins.managers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class MetricsManager {

    private static final MetricsManager INSTANCE = new MetricsManager();

    public static MetricsManager getInstance() {
        return INSTANCE;
    }

    private final LongAdder lockContentionRetries = new LongAdder();
    private final LongAdder syncFailures = new LongAdder();
    private final AtomicInteger lastLockAcquisitionLatency = new AtomicInteger();

    private HttpServer server;
    private DatabaseManager databaseManager;

    private MetricsManager() {}

    public void start(JavaPlugin plugin, DatabaseManager databaseManager, int port, String path) {
        this.databaseManager = databaseManager;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, new MetricsHandler());
            server.setExecutor(null); // default executor
            server.start();
            plugin.getLogger().info("Prometheus metrics server started on port " + port + " at path " + path);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Prometheus metrics server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        databaseManager = null;
    }

    public void incrementLockContentionRetries() {
        lockContentionRetries.increment();
    }

    public void incrementSyncFailures() {
        syncFailures.increment();
    }

    public void recordLockAcquisitionLatency(long ms) {
        lastLockAcquisitionLatency.set((int) ms);
    }

    // For testing / assertion purposes
    public long getLockContentionRetries() {
        return lockContentionRetries.sum();
    }

    public long getSyncFailures() {
        return syncFailures.sum();
    }

    public int getLastLockAcquisitionLatency() {
        return lastLockAcquisitionLatency.get();
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            int activeConnections = 0;
            int pendingThreads = 0;
            if (databaseManager != null) {
                activeConnections = databaseManager.getActiveConnections();
                pendingThreads = databaseManager.getPendingThreads();
            }

            StringBuilder sb = new StringBuilder();

            sb.append("# HELP databridge_hikari_active_connections Number of active connections currently executing reads/writes.\n");
            sb.append("# TYPE databridge_hikari_active_connections gauge\n");
            sb.append("databridge_hikari_active_connections ").append(activeConnections).append("\n\n");

            sb.append("# HELP databridge_hikari_pending_threads Number of threads blocked waiting for an available database socket.\n");
            sb.append("# TYPE databridge_hikari_pending_threads gauge\n");
            sb.append("databridge_hikari_pending_threads ").append(pendingThreads).append("\n\n");

            sb.append("# HELP databridge_lock_acquisition_latency_ms Time spent inside acquireLock transactions.\n");
            sb.append("# TYPE databridge_lock_acquisition_latency_ms gauge\n");
            sb.append("databridge_lock_acquisition_latency_ms ").append(lastLockAcquisitionLatency.get()).append("\n\n");

            sb.append("# HELP databridge_lock_contention_retries_total Count of polling hitches where Server B must sleep and loop.\n");
            sb.append("# TYPE databridge_lock_contention_retries_total counter\n");
            sb.append("databridge_lock_contention_retries_total ").append(lockContentionRetries.sum()).append("\n\n");

            sb.append("# HELP databridge_sync_failures_total Count of identity hash mismatches or data checksum rejections.\n");
            sb.append("# TYPE databridge_sync_failures_total counter\n");
            sb.append("databridge_sync_failures_total ").append(syncFailures.sum()).append("\n");

            byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
