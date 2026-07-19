package com.instacraft.uuidguard;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

public final class FloodgateIdentityResolver {
    private final PluginManager pluginManager;
    private final GuardSettings settings;
    private final Logger logger;
    private volatile boolean warnedAboutFloodgateError;

    public FloodgateIdentityResolver(PluginManager pluginManager, GuardSettings settings, Logger logger) {
        this.pluginManager = pluginManager;
        this.settings = settings;
        this.logger = logger;
    }

    public LoginIdentity resolve(Player player) {
        FloodgatePlayer floodgatePlayer = resolveFloodgatePlayer(player);
        if (floodgatePlayer == null) {
            return LoginIdentity.javaPlayer(player.getName(), player.getUniqueId());
        }

        boolean linked = floodgatePlayer.isLinked();
        UUID floodgateUuid = floodgatePlayer.getJavaUniqueId();
        UUID canonicalUuid = linked && settings.linkedBedrockUsesJavaIdentity()
                ? floodgatePlayer.getCorrectUniqueId()
                : floodgateUuid;
        IdentityKind kind = linked && settings.linkedBedrockUsesJavaIdentity()
                ? IdentityKind.LINKED_BEDROCK
                : IdentityKind.BEDROCK;

        return new LoginIdentity(
                player.getName(),
                LoginIdentity.key(player.getName()),
                player.getUniqueId(),
                canonicalUuid,
                kind,
                floodgateUuid,
                floodgatePlayer.getXuid(),
                "floodgate-api"
        );
    }

    public boolean floodgatePluginEnabled() {
        return pluginManager.isPluginEnabled("floodgate");
    }

    public String floodgatePrefix() {
        try {
            if (!floodgatePluginEnabled()) {
                return "(floodgate disabled)";
            }
            return FloodgateApi.getInstance().getPlayerPrefix();
        } catch (Throwable throwable) {
            return "(unavailable)";
        }
    }

    private FloodgatePlayer resolveFloodgatePlayer(Player player) {
        if (!floodgatePluginEnabled()) {
            return null;
        }

        try {
            FloodgateApi api = FloodgateApi.getInstance();
            UUID presentedUuid = player.getUniqueId();

            FloodgatePlayer direct = api.getPlayer(presentedUuid);
            if (direct != null) {
                return direct;
            }

            if (!settings.detectBedrockByFloodgateNameWhenOfflineUuid()) {
                return null;
            }

            if (!presentedUuid.equals(UuidUtil.offlineUuid(player.getName()))) {
                return null;
            }

            String eventName = normalize(player.getName());
            for (FloodgatePlayer candidate : api.getPlayers()) {
                if (matches(candidate, eventName)) {
                    return candidate;
                }
            }
        } catch (Throwable throwable) {
            if (!warnedAboutFloodgateError) {
                warnedAboutFloodgateError = true;
                logger.log(Level.WARNING, "UniqueUUID could not query Floodgate API; treating login as Java.", throwable);
            }
        }

        return null;
    }

    private boolean matches(FloodgatePlayer player, String eventName) {
        String rawUsername = player.getUsername();
        return eventName.equals(normalize(player.getCorrectUsername()))
                || eventName.equals(normalize(player.getJavaUsername()))
                || eventName.equals(normalize(rawUsername))
                || eventName.equals(normalize(rawUsername == null ? null : rawUsername.replace(' ', '_')));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
