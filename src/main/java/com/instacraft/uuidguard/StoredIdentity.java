package com.instacraft.uuidguard;

import java.util.UUID;

public record StoredIdentity(
        String nameKey,
        String lastSeenName,
        UUID canonicalUuid,
        IdentityKind kind,
        UUID floodgateUuid,
        UUID lastObservedUuid,
        String xuid
) {
    public IdentityKind inferredKind() {
        if (kind != null && kind != IdentityKind.UNKNOWN) {
            return kind;
        }
        if (canonicalUuid != null && UuidUtil.isFloodgateId(canonicalUuid)) {
            return IdentityKind.BEDROCK;
        }
        return IdentityKind.UNKNOWN;
    }

    public StoredIdentity withObservation(LoginIdentity identity) {
        return new StoredIdentity(
                identity.nameKey(),
                identity.playerName(),
                identity.canonicalUuid(),
                identity.kind(),
                identity.floodgateUuid(),
                identity.presentedUuid(),
                identity.xuid()
        );
    }
}
