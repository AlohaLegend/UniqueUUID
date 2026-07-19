package com.instacraft.uuidguard;

public record LoginDecision(
        DecisionType type,
        boolean allowed,
        boolean shouldWriteCanonical,
        String reason
) {
    public static LoginDecision allow(DecisionType type, boolean shouldWriteCanonical, String reason) {
        return new LoginDecision(type, true, shouldWriteCanonical, reason);
    }

    public static LoginDecision block(DecisionType type, String reason) {
        return new LoginDecision(type, false, false, reason);
    }
}
