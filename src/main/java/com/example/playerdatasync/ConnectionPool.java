package com.example.playerdatasync;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple connection pool implementation for PlayerDataSync
 */
public class ConnectionPool {
    private final PlayerDataSync plugin;
    private final ConcurrentLinkedQueue<Connection> availableConnections;
    private final AtomicInteger connectionCount;
    private final int maxConnections;
    private final String databaseUrl;
    private final String username;
    private final String password;
    private volatile boolean shutdown = false;

    public ConnectionPool(PlayerDataSync plugin, String databaseUrl, String username, String password, int maxConnections) {
        this.plugin = plugin;
        this.databaseUrl = databaseUrl;
        this.username = username;
        this.password = password;
        this.maxConnections = maxConnections;
        this.availableConnections = new ConcurrentLinkedQueue<>();
        this.connectionCount = new AtomicInteger(0);
    }

    /**
     * Get a connection from the pool with improved error handling
     */
    public Connection getConnection() throws SQLException {
        if (shutdown) {
            throw new SQLException("Connection pool is shut down");
        }

        Connection connection = availableConnections.poll();
        
        if (connection != null && isConnectionValid(connection)) {
            return connection;
        }

        // If no valid connection available, create a new one if under limit
        if (connectionCount.get() < maxConnections) {
            connection = createNewConnection();
            if (connection != null) {
                connectionCount.incrementAndGet();
                plugin.getLogger().fine("Created new database connection. Pool size: " + connectionCount.get());
                return connection;
            }
        }

        // Wait for a connection to become available with exponential backoff
        long startTime = System.currentTimeMillis();
        long waitTime = 10; // Start with 10ms
        final long maxWaitTime = 100; // Max 100ms between attempts
        final long totalTimeout = 10000; // 10 second total timeout
        
        while (System.currentTimeMillis() - startTime < totalTimeout) {
            connection = availableConnections.poll();
            if (connection != null && isConnectionValid(connection)) {
                return connection;
            }
            
            try {
                Thread.sleep(waitTime);
                waitTime = Math.min(waitTime * 2, maxWaitTime); // Exponential backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for connection");
            }
        }

        // Log pool statistics before throwing exception
        plugin.getLogger().severe("Connection pool exhausted. " + getStats());
        throw new SQLException("Unable to obtain database connection within timeout (" + totalTimeout + "ms)");
    }

    /**
     * Return a connection to the pool
     */
    public void returnConnection(Connection connection) {
        if (connection == null || shutdown) {
            try {
                if (connection != null) {
                    connection.close();
                    connectionCount.decrementAndGet();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing connection: " + e.getMessage());
            }
            return;
        }

        if (isConnectionValid(connection)) {
            availableConnections.offer(connection);
        } else {
            try {
                connection.close();
                connectionCount.decrementAndGet();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing invalid connection: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a connection is valid
     */
    private boolean isConnectionValid(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Create a new database connection
     */
    private Connection createNewConnection() {
        try {
            if (username != null && password != null) {
                return DriverManager.getConnection(databaseUrl, username, password);
            } else {
                return DriverManager.getConnection(databaseUrl);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create new database connection: " + e.getMessage());
            return null;
        }
    }

    /**
     * Initialize the pool with initial connections
     */
    public void initialize() {
        int initialConnections = Math.min(3, maxConnections);
        for (int i = 0; i < initialConnections; i++) {
            Connection connection = createNewConnection();
            if (connection != null) {
                availableConnections.offer(connection);
                connectionCount.incrementAndGet();
            }
        }
        plugin.getLogger().info("Connection pool initialized with " + availableConnections.size() + " connections");
    }

    /**
     * Shutdown the connection pool
     */
    public void shutdown() {
        shutdown = true;
        
        Connection connection;
        while ((connection = availableConnections.poll()) != null) {
            try {
                connection.close();
                connectionCount.decrementAndGet();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing connection during shutdown: " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Connection pool shut down. Remaining connections: " + connectionCount.get());
    }

    /**
     * Get pool statistics
     */
    public String getStats() {
        return String.format("Pool stats: %d/%d connections, %d available", 
            connectionCount.get(), maxConnections, availableConnections.size());
    }
}
