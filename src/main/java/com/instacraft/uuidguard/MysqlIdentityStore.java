package com.instacraft.uuidguard;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MysqlIdentityStore {
    private final DatabaseSettings databaseSettings;
    private final GuardSettings guardSettings;
    private final Logger logger;
    private final Map<String, StoredIdentity> cache = new ConcurrentHashMap<>();
    private final ExecutorService writer;
    private volatile boolean cacheLoaded;
    private volatile String lastError;

    public MysqlIdentityStore(DatabaseSettings databaseSettings, GuardSettings guardSettings, Logger logger) {
        this.databaseSettings = databaseSettings;
        this.guardSettings = guardSettings;
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "UniqueUUID-MySQL-Writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean loadAll() {
        if (!databaseSettings.complete()) {
            cacheLoaded = false;
            lastError = "database configuration is incomplete";
            logger.warning("UniqueUUID database configuration is incomplete.");
            return false;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            cacheLoaded = false;
            lastError = "mysql driver not found";
            logger.log(Level.SEVERE, "UniqueUUID could not load the MySQL driver.", exception);
            return false;
        }

        try (Connection connection = connect()) {
            ensureTables(connection);

            Map<String, StoredIdentity> loaded = new HashMap<>();
            loadLegacyTable(connection, loaded);
            loadMetaTable(connection, loaded);

            cache.clear();
            cache.putAll(loaded);
            cacheLoaded = true;
            lastError = null;
            logger.info("UniqueUUID loaded " + cache.size() + " stored identities.");
            return true;
        } catch (SQLException | RuntimeException exception) {
            cacheLoaded = false;
            lastError = exception.getMessage();
            logger.log(Level.SEVERE, "UniqueUUID could not load identity data from MySQL.", exception);
            return false;
        }
    }

    public StoredIdentity lookup(String nameKey) {
        return cache.get(nameKey);
    }

    public void remember(LoginIdentity identity, LoginDecision decision) {
        if (!decision.allowed() || !decision.shouldWriteCanonical()) {
            return;
        }

        if (guardSettings.dryRun()) {
            return;
        }

        StoredIdentity record = new StoredIdentity(
                identity.nameKey(),
                identity.playerName(),
                identity.canonicalUuid(),
                identity.kind(),
                identity.floodgateUuid(),
                identity.presentedUuid(),
                identity.xuid()
        );
        cache.put(identity.nameKey(), record);

        writer.submit(() -> {
            try (Connection connection = connect()) {
                upsertLegacy(connection, record);
                upsertMeta(connection, record);
            } catch (SQLException exception) {
                lastError = exception.getMessage();
                logger.log(Level.SEVERE, "UniqueUUID could not persist identity for " + record.nameKey(), exception);
            }
        });
    }

    public boolean isCacheLoaded() {
        return cacheLoaded;
    }

    public int cacheSize() {
        return cache.size();
    }

    public String lastError() {
        return lastError;
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private Connection connect() throws SQLException {
        DriverManager.setLoginTimeout(Math.max(1, guardSettings.connectTimeoutMs() / 1000));
        String url = "jdbc:mysql://"
                + databaseSettings.host()
                + ":"
                + databaseSettings.port()
                + "/"
                + databaseSettings.database()
                + "?useUnicode=true"
                + "&characterEncoding=utf8"
                + "&useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&connectTimeout="
                + guardSettings.connectTimeoutMs()
                + "&socketTimeout="
                + guardSettings.socketTimeoutMs();
        return DriverManager.getConnection(url, databaseSettings.username(), databaseSettings.password());
    }

    private void ensureTables(Connection connection) throws SQLException {
        String legacyTable = quoteIdentifier(guardSettings.legacyTable());
        String metaTable = quoteIdentifier(guardSettings.metaTable());

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + legacyTable
                    + "( `id` INT NOT NULL AUTO_INCREMENT,"
                    + " `username` TEXT NOT NULL,"
                    + " `UUID` TEXT NOT NULL,"
                    + " PRIMARY KEY (`id`))");

            statement.execute("CREATE TABLE IF NOT EXISTS " + metaTable
                    + "( `name_key` VARCHAR(64) NOT NULL,"
                    + " `last_seen_name` VARCHAR(64) NOT NULL,"
                    + " `identity_kind` VARCHAR(32) NOT NULL,"
                    + " `canonical_uuid` CHAR(36) NOT NULL,"
                    + " `floodgate_uuid` CHAR(36) NULL,"
                    + " `last_observed_uuid` CHAR(36) NULL,"
                    + " `xuid` VARCHAR(32) NULL,"
                    + " `first_seen` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " `last_seen` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (`name_key`),"
                    + " INDEX `idx_canonical_uuid` (`canonical_uuid`),"
                    + " INDEX `idx_floodgate_uuid` (`floodgate_uuid`)"
                    + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void loadLegacyTable(Connection connection, Map<String, StoredIdentity> loaded) throws SQLException {
        String legacyTable = quoteIdentifier(guardSettings.legacyTable());
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT `username`, `UUID` FROM " + legacyTable + " ORDER BY `id` ASC")) {
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                UUID uuid = UuidUtil.parseUuid(resultSet.getString("UUID"));
                if (username == null || uuid == null) {
                    continue;
                }

                String nameKey = LoginIdentity.key(username);
                loaded.putIfAbsent(nameKey, new StoredIdentity(
                        nameKey,
                        username,
                        uuid,
                        IdentityKind.UNKNOWN,
                        UuidUtil.isFloodgateId(uuid) ? uuid : null,
                        null,
                        null
                ));
            }
        }
    }

    private void loadMetaTable(Connection connection, Map<String, StoredIdentity> loaded) throws SQLException {
        String metaTable = quoteIdentifier(guardSettings.metaTable());
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT `name_key`, `last_seen_name`, `identity_kind`, `canonical_uuid`,"
                             + " `floodgate_uuid`, `last_observed_uuid`, `xuid` FROM " + metaTable)) {
            while (resultSet.next()) {
                String nameKey = resultSet.getString("name_key");
                UUID canonicalUuid = UuidUtil.parseUuid(resultSet.getString("canonical_uuid"));
                if (nameKey == null || canonicalUuid == null) {
                    continue;
                }

                IdentityKind kind = parseKind(resultSet.getString("identity_kind"));
                loaded.put(LoginIdentity.key(nameKey), new StoredIdentity(
                        LoginIdentity.key(nameKey),
                        resultSet.getString("last_seen_name"),
                        canonicalUuid,
                        kind,
                        UuidUtil.parseUuid(resultSet.getString("floodgate_uuid")),
                        UuidUtil.parseUuid(resultSet.getString("last_observed_uuid")),
                        resultSet.getString("xuid")
                ));
            }
        }
    }

    private void upsertLegacy(Connection connection, StoredIdentity record) throws SQLException {
        String legacyTable = quoteIdentifier(guardSettings.legacyTable());
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + legacyTable + " SET `UUID`=? WHERE LOWER(`username`)=?")) {
            update.setString(1, record.canonicalUuid().toString());
            update.setString(2, record.nameKey());
            int updated = update.executeUpdate();
            if (updated > 0) {
                return;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + legacyTable + "(`username`, `UUID`) VALUES (?, ?)")) {
            insert.setString(1, record.nameKey());
            insert.setString(2, record.canonicalUuid().toString());
            insert.executeUpdate();
        }
    }

    private void upsertMeta(Connection connection, StoredIdentity record) throws SQLException {
        String metaTable = quoteIdentifier(guardSettings.metaTable());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + metaTable
                        + " (`name_key`, `last_seen_name`, `identity_kind`, `canonical_uuid`,"
                        + " `floodgate_uuid`, `last_observed_uuid`, `xuid`)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE"
                        + " `last_seen_name`=VALUES(`last_seen_name`),"
                        + " `identity_kind`=VALUES(`identity_kind`),"
                        + " `canonical_uuid`=VALUES(`canonical_uuid`),"
                        + " `floodgate_uuid`=VALUES(`floodgate_uuid`),"
                        + " `last_observed_uuid`=VALUES(`last_observed_uuid`),"
                        + " `xuid`=VALUES(`xuid`),"
                        + " `last_seen`=CURRENT_TIMESTAMP")) {
            statement.setString(1, record.nameKey());
            statement.setString(2, record.lastSeenName());
            statement.setString(3, record.kind().name());
            statement.setString(4, record.canonicalUuid().toString());
            setNullableUuid(statement, 5, record.floodgateUuid());
            setNullableUuid(statement, 6, record.lastObservedUuid());
            statement.setString(7, record.xuid());
            statement.executeUpdate();
        }
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setString(index, null);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private static IdentityKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return IdentityKind.UNKNOWN;
        }
        try {
            return IdentityKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IdentityKind.UNKNOWN;
        }
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe MySQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }
}
