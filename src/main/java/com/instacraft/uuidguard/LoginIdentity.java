package com.instacraft.uuidguard;

import java.util.Locale;
import java.util.UUID;

public record LoginIdentity(
        String playerName,
        String nameKey,
        UUID presentedUuid,
        UUID canonicalUuid,
        IdentityKind kind,
        UUID floodgateUuid,
        String xuid,
        String source
) {
    public static LoginIdentity javaPlayer(String playerName, UUID presentedUuid) {
        return new LoginIdentity(
                playerName,
                key(playerName),
                presentedUuid,
                presentedUuid,
                IdentityKind.JAVA,
                null,
                null,
                "bukkit"
        );
    }

    public boolean isBedrock() {
        return kind.isBedrock();
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
