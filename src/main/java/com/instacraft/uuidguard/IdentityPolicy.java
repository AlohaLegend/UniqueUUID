package com.instacraft.uuidguard;

import java.util.List;
import java.util.UUID;

public final class IdentityPolicy {
    private final GuardSettings settings;

    public IdentityPolicy(GuardSettings settings) {
        this.settings = settings;
    }

    public LoginDecision evaluate(LoginIdentity login, StoredIdentity stored, boolean cacheReady) {
        if (!cacheReady && settings.failClosedWithoutCache()) {
            return LoginDecision.block(
                    DecisionType.BLOCK_DATABASE_UNAVAILABLE,
                    "identity cache was not loaded"
            );
        }
        if (!cacheReady) {
            return LoginDecision.allow(
                    DecisionType.ALLOW_DATABASE_UNAVAILABLE,
                    false,
                    "identity cache was not loaded; fail-open mode is enabled"
            );
        }

        if (stored == null || stored.canonicalUuid() == null) {
            return LoginDecision.allow(
                    DecisionType.ALLOW_REGISTER,
                    true,
                    "first seen name"
            );
        }

        if (stored.canonicalUuid().equals(login.canonicalUuid())) {
            return LoginDecision.allow(
                    DecisionType.ALLOW_MATCH,
                    settings.rememberAllowedLogins(),
                    "stored canonical UUID matches"
            );
        }

        if (login.isBedrock() && shouldMigrateBedrockOfflineRecord(login, stored)) {
            return LoginDecision.allow(
                    DecisionType.ALLOW_MIGRATE_BEDROCK_OFFLINE_UUID,
                    true,
                    "stored UUID was an offline/name UUID for this Bedrock player"
            );
        }

        if (settings.blockCrossPlatformNameCollisions() && isCrossPlatformCollision(login, stored)) {
            return LoginDecision.block(
                    DecisionType.BLOCK_CROSS_PLATFORM_NAME,
                    "same visible username exists on the other platform"
            );
        }

        return LoginDecision.block(
                DecisionType.BLOCK_UUID_MISMATCH,
                "same username has a different canonical UUID"
        );
    }

    private boolean shouldMigrateBedrockOfflineRecord(LoginIdentity login, StoredIdentity stored) {
        if (!settings.migrateBedrockOfflineUuidRecords()) {
            return false;
        }

        UUID storedUuid = stored.canonicalUuid();
        if (storedUuid == null || UuidUtil.isFloodgateId(storedUuid)) {
            return false;
        }

        if (storedUuid.equals(login.presentedUuid()) && !storedUuid.equals(login.canonicalUuid())) {
            return true;
        }

        return UuidUtil.isOfflineUuidForAny(
                storedUuid,
                List.of(login.playerName(), login.nameKey(), stored.lastSeenName())
        );
    }

    private boolean isCrossPlatformCollision(LoginIdentity login, StoredIdentity stored) {
        IdentityKind storedKind = stored.inferredKind();
        if (login.isBedrock()) {
            return !storedKind.isBedrock() && !UuidUtil.isFloodgateId(stored.canonicalUuid());
        }
        return storedKind.isBedrock() || UuidUtil.isFloodgateId(stored.canonicalUuid());
    }
}
