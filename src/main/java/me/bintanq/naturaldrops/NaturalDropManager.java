package me.bintanq.naturaldrops;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.VisantaraEventHandler;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;

public class NaturalDropManager {

    private final VisantaraEventHandler plugin;
    private final Set<String> playerPlaced = ConcurrentHashMap.newKeySet();
    private final HikariDataSource dataSource;
    private final ThreadPoolExecutor dbExecutor;

    public NaturalDropManager(VisantaraEventHandler plugin) {
        this.plugin = plugin;

        File dbFile = new File(plugin.getDataFolder(), "player_placed_blocks.db");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(30);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(3000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(120000);
        config.setPoolName("VEH-BlockDB");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        this.dataSource = new HikariDataSource(config);

        this.dbExecutor = new ThreadPoolExecutor(
                2, 30,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "VEH-BlockDB-worker");
                    t.setDaemon(true);
                    return t;
                }
        );

        try {
            initDatabase();
            loadFromDatabase();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database", e);
        }
    }

    private void initDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_placed_blocks (
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
            """);
        }
    }

    private void loadFromDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT world, x, y, z FROM player_placed_blocks")) {
            while (rs.next()) {
                playerPlaced.add(toKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
            }
        }
        plugin.getLogger().info("Loaded " + playerPlaced.size() + " player-placed blocks from database.");
    }

    public boolean isPlayerPlaced(Location loc) {
        return playerPlaced.contains(toKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public void markPlayerPlaced(Location loc) {
        playerPlaced.add(toKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        String world = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        dbExecutor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR IGNORE INTO player_placed_blocks (world, x, y, z) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to insert player-placed block", e);
            }
        });
    }

    public void unmarkPlayerPlaced(Location loc) {
        playerPlaced.remove(toKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        String world = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        dbExecutor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM player_placed_blocks WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete player-placed block", e);
            }
        });
    }

    public void forceMarkNatural(Location loc) {
        unmarkPlayerPlaced(loc);
    }

    public int getTotalTracked() {
        return playerPlaced.size();
    }

    public void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("DB executor did not terminate cleanly, forcing shutdown.");
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private String toKey(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }
}