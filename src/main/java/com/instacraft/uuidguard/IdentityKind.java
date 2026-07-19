package com.instacraft.uuidguard;

public enum IdentityKind {
    JAVA,
    BEDROCK,
    LINKED_BEDROCK,
    UNKNOWN;

    public boolean isBedrock() {
        return this == BEDROCK || this == LINKED_BEDROCK;
    }
}
