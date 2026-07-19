package com.instacraft.uuidguard;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageFormatter {
    private final FileConfiguration config;

    public MessageFormatter(FileConfiguration config) {
        this.config = config;
    }

    public String kick(LoginIdentity identity, LoginDecision decision) {
        String path = switch (decision.type()) {
            case BLOCK_CROSS_PLATFORM_NAME -> "messages.duplicate-name";
            case BLOCK_DATABASE_UNAVAILABLE -> "messages.database-unavailable";
            default -> "messages.uuid-mismatch";
        };
        return color(placeholders(config.getString(path, defaultMessage(decision)), identity, decision));
    }

    public String text(String path, String fallback) {
        return color(config.getString(path, fallback));
    }

    public String placeholders(String value, LoginIdentity identity, LoginDecision decision) {
        String result = value == null ? "" : value;
        result = result.replace("{player}", identity.playerName());
        result = result.replace("{uuid}", identity.presentedUuid().toString());
        result = result.replace("{canonical_uuid}", identity.canonicalUuid().toString());
        result = result.replace("{platform}", identity.kind().name());
        result = result.replace("{reason}", decision.reason());
        return result;
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static String defaultMessage(LoginDecision decision) {
        if (decision.type() == DecisionType.BLOCK_CROSS_PLATFORM_NAME) {
            return "&cYour Java and Bedrock username cannot be the same. Please contact staff.";
        }
        if (decision.type() == DecisionType.BLOCK_DATABASE_UNAVAILABLE) {
            return "&cLogin identity checks are temporarily unavailable. Please try again shortly.";
        }
        return "&cYour username is already tied to another account. Please contact staff.";
    }
}
