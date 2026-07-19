package com.instacraft.uuidguard;

public record GuardSettings(
        boolean blockCrossPlatformNameCollisions,
        boolean migrateBedrockOfflineUuidRecords,
        boolean detectBedrockByFloodgateNameWhenOfflineUuid,
        boolean linkedBedrockUsesJavaIdentity,
        boolean rememberAllowedLogins,
        boolean failClosedWithoutCache,
        boolean dryRun,
        String legacyTable,
        String metaTable,
        int connectTimeoutMs,
        int socketTimeoutMs
) {
    public static GuardSettings defaults() {
        return new GuardSettings(
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                "player_uuid_data",
                "uuid_guard_identity_meta",
                5000,
                5000
        );
    }
}
