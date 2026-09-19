package com.example.maincore;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainDatabase {

    public record Land(int id, UUID owner, String name, int cellX, int cellZ,
                        boolean reservation, Long reservationExpiresAt, UUID holder) {}

    /** An unclaimed deed the player already paid for but hasn't bound to a cell yet. This DB row
     * is the source of truth (mirrors how a claimed Land works) - the physical paper item handed
     * out is just a disposable, regenerable copy, so dropping/trading/giving it away means
     * nothing on its own; only the owner on record can ever use it to claim land. */
    public record PendingDeed(int id, UUID owner, String name, boolean reservation) {}

    public enum MarketCategory { LAND, ITEM }

    public enum MarketStatus { ACTIVE, SOLD, EXPIRED }

    public record MarketListing(int id, UUID seller, MarketCategory category, Integer landId,
                                 ItemStack item, long price, String memo,
                                 Timestamp listedAt, Timestamp expiresAt, MarketStatus status) {}

    /** Sentinel "owner" a land is set to while it's up for sale on the market. */
    public static final UUID MARKET_OWNER = new UUID(0L, 0L);

    /** Sentinel "owner" for a claimed 토지선점권 (reservation) land - nominally nobody's, so no
     * one (buyer included) can build there until it actually sells. */
    public static final UUID RESERVATION_OWNER = new UUID(0L, 3L);

    private final Logger logger;
    private final HikariDataSource dataSource;

    public MainDatabase(Logger logger, String host, int port, String database, String user, String password) {
        this.logger = logger;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setPoolName("MainCore-Pool");

        this.dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS credits ("
                    + "uuid VARCHAR(36) PRIMARY KEY,"
                    + "balance BIGINT NOT NULL DEFAULT 0"
                    + ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS lands ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "name VARCHAR(64) NOT NULL,"
                    + "cell_x INT NOT NULL,"
                    + "cell_z INT NOT NULL,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE KEY uniq_cell (cell_x, cell_z)"
                    + ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS market_listings ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "seller_uuid VARCHAR(36) NOT NULL,"
                    + "category VARCHAR(10) NOT NULL,"
                    + "land_id INT NULL,"
                    + "item_data MEDIUMTEXT NULL,"
                    + "price BIGINT NOT NULL,"
                    + "memo VARCHAR(20),"
                    + "listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "expires_at TIMESTAMP NOT NULL,"
                    + "status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'"
                    + ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS pending_deeds ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "land_name VARCHAR(64) NULL,"
                    + "is_reservation BOOLEAN NOT NULL DEFAULT FALSE,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create MainCore tables", e);
        }
        migrateStatusColumn();
        migrateReservationColumns();
    }

    // lands existed before 토지선점권 (reservation deed) support was added - migrate it in place.
    private void migrateReservationColumns() {
        addColumnIfMissing("lands", "is_reservation", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("lands", "reservation_expires_at", "BIGINT NULL");
        addColumnIfMissing("lands", "holder_uuid", "VARCHAR(36) NULL");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        String checkSql = "SELECT COUNT(*) AS c FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, table);
            check.setString(2, column);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt("c") == 0) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                        logger.info("Migrated " + table + ": added " + column + " column.");
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to migrate " + table + "." + column, e);
        }
    }

    // market_listings existed before the status column was added - migrate it in place.
    // Plain MySQL (unlike MariaDB) has no "ADD COLUMN IF NOT EXISTS", so check first.
    private void migrateStatusColumn() {
        String checkSql = "SELECT COUNT(*) AS c FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'market_listings' AND column_name = 'status'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt("c") == 0) {
                stmt.execute("ALTER TABLE market_listings ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'");
                logger.info("Migrated market_listings: added status column.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to migrate market_listings status column", e);
        }
    }

    // ---------- market ----------

    private MarketListing readListing(ResultSet rs) throws SQLException {
        String itemData = rs.getString("item_data");
        ItemStack item = itemData == null ? null : deserializeItem(itemData);
        Integer landId = rs.getObject("land_id") == null ? null : rs.getInt("land_id");
        return new MarketListing(
                rs.getInt("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                MarketCategory.valueOf(rs.getString("category")),
                landId,
                item,
                rs.getLong("price"),
                rs.getString("memo"),
                rs.getTimestamp("listed_at"),
                rs.getTimestamp("expires_at"),
                MarketStatus.valueOf(rs.getString("status")));
    }

    /** Blocking. Registers a land for sale; returns the new listing's id. */
    public int insertLandListing(UUID seller, int landId, long price, String memo, long durationMillis) {
        String sql = "INSERT INTO market_listings (seller_uuid, category, land_id, price, memo, expires_at) "
                + "VALUES (?, 'LAND', ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, seller.toString());
            stmt.setInt(2, landId);
            stmt.setLong(3, price);
            stmt.setString(4, memo);
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis() + durationMillis));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to list land " + landId + " for " + seller, e);
        }
        return -1;
    }

    /** Blocking. Registers a plain item for sale; returns the new listing's id. */
    public int insertItemListing(UUID seller, ItemStack item, long price, String memo, long durationMillis) {
        String sql = "INSERT INTO market_listings (seller_uuid, category, item_data, price, memo, expires_at) "
                + "VALUES (?, 'ITEM', ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, seller.toString());
            stmt.setString(2, serializeItem(item));
            stmt.setLong(3, price);
            stmt.setString(4, memo);
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis() + durationMillis));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to list item for " + seller, e);
        }
        return -1;
    }

    public MarketListing getListingById(int id) {
        String sql = "SELECT * FROM market_listings WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return readListing(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load listing " + id, e);
        }
        return null;
    }

    /** Blocking. Atomic claim: returns true only if this call is the one that actually removed the row. */
    public boolean deleteListingIfExists(int id) {
        String sql = "DELETE FROM market_listings WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete listing " + id, e);
            return false;
        }
    }

    /** Blocking. Atomic sale claim: marks an ACTIVE listing SOLD. True only if this call won the race. */
    public boolean markSold(int id) {
        String sql = "UPDATE market_listings SET status = 'SOLD' WHERE id = ? AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to mark listing " + id + " sold", e);
            return false;
        }
    }

    /** Blocking. Atomic expiry claim: marks an ACTIVE listing EXPIRED. True only if this call won the race. */
    public boolean markExpired(int id) {
        String sql = "UPDATE market_listings SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to mark listing " + id + " expired", e);
            return false;
        }
    }

    /** Blocking. Re-lists an EXPIRED listing for another full duration. True only if this call won the race. */
    public boolean reactivateListing(int id, long durationMillis) {
        String sql = "UPDATE market_listings SET status = 'ACTIVE', expires_at = ? WHERE id = ? AND status = 'EXPIRED'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis() + durationMillis));
            stmt.setInt(2, id);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to reactivate listing " + id, e);
            return false;
        }
    }

    /** Blocking. Reverts a SOLD listing back to ACTIVE (buyer couldn't actually afford it after all). */
    public void revertToActive(int id) {
        String sql = "UPDATE market_listings SET status = 'ACTIVE' WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to revert listing " + id + " to active", e);
        }
    }

    /** Blocking. Every listing (active or awaiting payout claim) this seller has ever put up, newest last. */
    public List<MarketListing> getListingsBySeller(UUID seller) {
        List<MarketListing> result = new ArrayList<>();
        String sql = "SELECT * FROM market_listings WHERE seller_uuid = ? ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seller.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(readListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to list seller listings for " + seller, e);
        }
        return result;
    }

    public int countActiveBySeller(UUID seller) {
        String sql = "SELECT COUNT(*) AS c FROM market_listings WHERE seller_uuid = ? AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seller.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to count listings for " + seller, e);
        }
        return 0;
    }

    public int countByCategory(MarketCategory category) {
        String sql = "SELECT COUNT(*) AS c FROM market_listings WHERE category = ? AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to count " + category + " listings", e);
        }
        return 0;
    }

    /** Blocking. Oldest-first page of listings in this category. */
    public List<MarketListing> listByCategory(MarketCategory category, int offset, int limit) {
        List<MarketListing> result = new ArrayList<>();
        String sql = "SELECT * FROM market_listings WHERE category = ? AND status = 'ACTIVE' ORDER BY id ASC LIMIT ? OFFSET ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category.name());
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(readListing(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to list " + category + " listings", e);
        }
        return result;
    }

    /** Blocking. Every listing whose 24h window has passed - for the expiry sweep. */
    public List<MarketListing> getExpiredListings() {
        List<MarketListing> result = new ArrayList<>();
        String sql = "SELECT * FROM market_listings WHERE expires_at <= NOW() AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) result.add(readListing(rs));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load expired listings", e);
        }
        return result;
    }

    private String serializeItem(ItemStack item) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item);
        return yaml.saveToString();
    }

    private ItemStack deserializeItem(String yamlString) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(yamlString);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse stored market item", e);
            return null;
        }
        return yaml.getItemStack("item");
    }

    // ---------- credits ----------

    /** Blocking. Call off the main thread. */
    public long getBalance(UUID uuid) {
        String sql = "SELECT balance FROM credits WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("balance");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read balance for " + uuid, e);
        }
        return 0L;
    }

    /** Blocking. Adds (or subtracts, if negative) to the player's balance and returns the new balance. */
    public synchronized long addBalance(UUID uuid, long delta) {
        long current = getBalance(uuid);
        long updated = current + delta;
        String sql = "INSERT INTO credits (uuid, balance) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE balance = VALUES(balance)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, updated);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update balance for " + uuid, e);
        }
        return updated;
    }

    // ---------- lands ----------

    /** Blocking. Returns null if the cell is unclaimed. */
    private Land readLand(ResultSet rs) throws SQLException {
        Long expiresAt = rs.getObject("reservation_expires_at") == null ? null : rs.getLong("reservation_expires_at");
        String holderStr = rs.getString("holder_uuid");
        UUID holder = holderStr == null ? null : UUID.fromString(holderStr);
        return new Land(rs.getInt("id"), UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("name"), rs.getInt("cell_x"), rs.getInt("cell_z"),
                rs.getBoolean("is_reservation"), expiresAt, holder);
    }

    public Land getLandAt(int cellX, int cellZ) {
        String sql = "SELECT * FROM lands WHERE cell_x = ? AND cell_z = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, cellX);
            stmt.setInt(2, cellZ);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readLand(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to look up land at " + cellX + "," + cellZ, e);
        }
        return null;
    }

    public Land getLandById(int id) {
        String sql = "SELECT * FROM lands WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readLand(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to look up land id " + id, e);
        }
        return null;
    }

    /** Blocking. Updates the owner of an existing land (used for trades). */
    public void updateLandOwner(int landId, UUID newOwner) {
        String sql = "UPDATE lands SET owner_uuid = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newOwner.toString());
            stmt.setInt(2, landId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to transfer land " + landId + " to " + newOwner, e);
        }
    }

    /** Blocking. Turns a claimed 토지선점권 (nominally owned by nobody) into a real deed for
     * whoever just bought/received it - clears the reservation flag, its expiry, and holder. */
    public void convertReservationToNormal(int landId, UUID newOwner) {
        String sql = "UPDATE lands SET owner_uuid = ?, is_reservation = FALSE, "
                + "reservation_expires_at = NULL, holder_uuid = NULL WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newOwner.toString());
            stmt.setInt(2, landId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to convert reservation land " + landId + " to " + newOwner, e);
        }
    }

    /** Blocking. Claims a cell as a 토지선점권 - owned by nobody (RESERVATION_OWNER), with its own
     * independent expiry. holder is who currently controls/can trade it (shows in their
     * /땅 문서, unlike owner which stays the sentinel until it actually sells). Returns the new
     * land's id, or -1 if the cell was already claimed. */
    public synchronized int claimReservationLand(UUID holder, String name, int cellX, int cellZ, long expiresAtMillis) {
        if (getLandAt(cellX, cellZ) != null) {
            return -1;
        }
        String sql = "INSERT INTO lands (owner_uuid, name, cell_x, cell_z, is_reservation, reservation_expires_at, holder_uuid) "
                + "VALUES (?, ?, ?, ?, TRUE, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, RESERVATION_OWNER.toString());
            stmt.setString(2, name);
            stmt.setInt(3, cellX);
            stmt.setInt(4, cellZ);
            stmt.setLong(5, expiresAtMillis);
            stmt.setString(6, holder.toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to claim reservation land at " + cellX + "," + cellZ, e);
        }
        return -1;
    }

    /** Blocking. Renames an existing land (used for /땅 이름변경). */
    public void updateLandName(int landId, String newName) {
        String sql = "UPDATE lands SET name = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setInt(2, landId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to rename land " + landId, e);
        }
    }

    /** Blocking. Permanently removes a claimed land (used for /땅 파기). */
    public void deleteLand(int landId) {
        String sql = "DELETE FROM lands WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, landId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete land " + landId, e);
        }
    }

    /** Blocking. Loads every claimed cell - intended for startup cache warm-up only. */
    public List<Land> getAllLandsBlocking() {
        List<Land> result = new ArrayList<>();
        String sql = "SELECT * FROM lands";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(readLand(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load all lands", e);
        }
        return result;
    }

    /** Blocking. Returns the new land's id, or -1 if the cell was already claimed. */
    public synchronized int claimLand(UUID owner, String name, int cellX, int cellZ) {
        if (getLandAt(cellX, cellZ) != null) {
            return -1;
        }
        String sql = "INSERT INTO lands (owner_uuid, name, cell_x, cell_z) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, name);
            stmt.setInt(3, cellX);
            stmt.setInt(4, cellZ);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to claim land for " + owner, e);
        }
        return -1;
    }

    // ---------- pending deeds ----------

    private PendingDeed readPendingDeed(ResultSet rs) throws SQLException {
        return new PendingDeed(rs.getInt("id"), UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("land_name"), rs.getBoolean("is_reservation"));
    }

    /** Blocking. Returns the new pending deed's id, or -1 on failure. */
    public int createPendingDeed(UUID owner, String name, boolean reservation) {
        String sql = "INSERT INTO pending_deeds (owner_uuid, land_name, is_reservation) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, owner.toString());
            stmt.setString(2, name);
            stmt.setBoolean(3, reservation);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create pending deed for " + owner, e);
        }
        return -1;
    }

    public PendingDeed getPendingDeedById(int id) {
        String sql = "SELECT * FROM pending_deeds WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readPendingDeed(rs);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to look up pending deed id " + id, e);
        }
        return null;
    }

    /** Blocking. Loads every outstanding pending deed - intended for startup cache warm-up only. */
    public List<PendingDeed> getAllPendingDeedsBlocking() {
        List<PendingDeed> result = new ArrayList<>();
        String sql = "SELECT * FROM pending_deeds";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(readPendingDeed(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load all pending deeds", e);
        }
        return result;
    }

    /** Blocking. Consumed by claiming it into real land (or an abandon/refund flow later). */
    public void deletePendingDeed(int id) {
        String sql = "DELETE FROM pending_deeds WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete pending deed " + id, e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
