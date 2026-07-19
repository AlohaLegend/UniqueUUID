package com.instacraft.uuidguard;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

public final class UuidUtil {
    private UuidUtil() {
    }

    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isOfflineUuidForAny(UUID uuid, Collection<String> names) {
        if (uuid == null) {
            return false;
        }
        for (String name : names) {
            if (name != null && uuid.equals(offlineUuid(name))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFloodgateId(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }

    public static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
