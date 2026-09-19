package com.example.serverbridge;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private final Logger logger;
    private final HikariDataSource dataSource;

    public DatabaseManager(Logger logger, String host, int port, String database, String user, String password) {
        this.logger = logger;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setPoolName("ServerBridge-Pool");

        this.dataSource = new HikariDataSource(config);

        createTableIfMissing();
    }

    private void createTableIfMissing() {
        String sql = "CREATE TABLE IF NOT EXISTS player_boxes ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "contents MEDIUMTEXT,"
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create player_boxes table", e);
        }
    }

    /**
     * Loads the stored box contents for a player. Runs blocking JDBC - caller
     * must invoke this off the main thread.
     */
    public ItemStack[] loadBoxContents(UUID uuid, int size) {
        String sql = "SELECT contents FROM player_boxes WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String yaml = rs.getString("contents");
                    if (yaml != null && !yaml.isEmpty()) {
                        return deserialize(yaml, size);
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load box contents for " + uuid, e);
        }
        return new ItemStack[size];
    }

    /**
     * Saves box contents for a player. Runs blocking JDBC - caller must
     * invoke this off the main thread.
     */
    public void saveBoxContents(UUID uuid, ItemStack[] contents) {
        String yaml = serialize(contents);
        String sql = "INSERT INTO player_boxes (uuid, contents) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE contents = VALUES(contents)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, yaml);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save box contents for " + uuid, e);
        }
    }

    private String serialize(ItemStack[] contents) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("contents", java.util.Arrays.asList(contents));
        return yaml.saveToString();
    }

    private ItemStack[] deserialize(String yamlString, int size) {
        ItemStack[] result = new ItemStack[size];
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(yamlString);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse stored box contents", e);
            return result;
        }
        List<?> list = yaml.getList("contents");
        if (list == null) {
            return result;
        }
        for (int i = 0; i < size && i < list.size(); i++) {
            Object obj = list.get(i);
            if (obj instanceof ItemStack) {
                result[i] = (ItemStack) obj;
            }
        }
        return result;
    }

    /** A player's MainCore job as stored in MainCore's own schema (see config jobs-schema). */
    public record JobProfile(String job, int u1, int u2, int u3) {
        public int level(int track) {
            return switch (track) {
                case 0 -> u1;
                case 1 -> u2;
                case 2 -> u3;
                default -> 0;
            };
        }
    }

    /** Blocking. Reads MainCore's jobs table cross-schema - the ServerBridge MySQL user needs
     * SELECT on `<schema>.jobs`. Null if the player has no job or the query fails. */
    public JobProfile loadJobProfile(String schema, UUID uuid) {
        String sql = "SELECT job, upgrade_1, upgrade_2, upgrade_3 FROM `" + schema + "`.jobs WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new JobProfile(rs.getString("job"), rs.getInt("upgrade_1"), rs.getInt("upgrade_2"), rs.getInt("upgrade_3"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read MainCore job for " + uuid + " from schema " + schema
                    + " (does the ServerBridge MySQL user have SELECT on it?)", e);
        }
        return null;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
