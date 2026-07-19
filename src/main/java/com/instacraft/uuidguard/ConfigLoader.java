package com.instacraft.uuidguard;

import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static DatabaseSettings database(FileConfiguration config) {
        return new DatabaseSettings(
                config.getString("mysql.host", ""),
                config.getInt("mysql.port", 3306),
                config.getString("mysql.dbname", ""),
                config.getString("mysql.username", ""),
                config.getString("mysql.password", "")
        );
    }

    public static GuardSettings guard(FileConfiguration config) {
        GuardSettings defaults = GuardSettings.defaults();
        return new GuardSettings(
                config.getBoolean(
                        "identity.block-cross-platform-name-collisions",
                        defaults.blockCrossPlatformNameCollisions()
                ),
                config.getBoolean(
                        "identity.migrate-bedrock-offline-uuid-records",
                        defaults.migrateBedrockOfflineUuidRecords()
                ),
                config.getBoolean(
                        "identity.detect-bedrock-by-floodgate-name-when-offline-uuid",
                        defaults.detectBedrockByFloodgateNameWhenOfflineUuid()
                ),
                config.getBoolean(
                        "identity.linked-bedrock-uses-java-identity",
                        defaults.linkedBedrockUsesJavaIdentity()
                ),
                config.getBoolean(
                        "identity.remember-allowed-logins",
                        defaults.rememberAllowedLogins()
                ),
                config.getBoolean(
                        "database.fail-closed-without-cache",
                        defaults.failClosedWithoutCache()
                ),
                config.getBoolean("database.dry-run", defaults.dryRun()),
                config.getString("database.legacy-table", defaults.legacyTable()),
                config.getString("database.meta-table", defaults.metaTable()),
                config.getInt("database.connect-timeout-ms", defaults.connectTimeoutMs()),
                config.getInt("database.socket-timeout-ms", defaults.socketTimeoutMs())
        );
    }
}
