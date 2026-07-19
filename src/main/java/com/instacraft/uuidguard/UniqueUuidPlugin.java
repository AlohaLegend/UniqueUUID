package com.instacraft.uuidguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class UniqueUuidPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private MysqlIdentityStore store;
    private IdentityPolicy policy;
    private FloodgateIdentityResolver resolver;
    private GuardSettings guardSettings;
    private MessageFormatter messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        rebuildRuntime();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("uniqueuuid") != null) {
            getCommand("uniqueuuid").setExecutor(this);
            getCommand("uniqueuuid").setTabCompleter(this);
        }

        getLogger().info("UniqueUUID Floodgate-aware guard enabled.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.shutdown();
        }
        getLogger().info("UniqueUUID guard disabled.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        Player player = event.getPlayer();
        LoginIdentity identity = resolver.resolve(player);
        StoredIdentity stored = store.lookup(identity.nameKey());
        LoginDecision decision = policy.evaluate(identity, stored, store.isCacheLoaded());

        if (decision.allowed()) {
            store.remember(identity, decision);
            if (decision.type() == DecisionType.ALLOW_MIGRATE_BEDROCK_OFFLINE_UUID) {
                getLogger().info("Migrated Bedrock offline UUID record for "
                        + identity.playerName()
                        + " to canonical Floodgate identity "
                        + identity.canonicalUuid());
            }
            return;
        }

        getLogger().warning("Blocked login for "
                + identity.playerName()
                + " platform="
                + identity.kind()
                + " presented="
                + identity.presentedUuid()
                + " canonical="
                + identity.canonicalUuid()
                + " decision="
                + decision.type()
                + " reason="
                + decision.reason());
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, messages.kick(identity, decision));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uniqueuuid.admin")) {
            sender.sendMessage(MessageFormatter.color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                getConfig().options().copyDefaults(true);
                saveConfig();
                rebuildRuntime();
                sender.sendMessage(MessageFormatter.color("&aUniqueUUID reloaded."));
                return true;
            }
            case "status" -> {
                sendStatus(sender);
                return true;
            }
            case "inspect" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageFormatter.color("&eUsage: /" + label + " inspect <player>"));
                    return true;
                }
                sendInspect(sender, args[1]);
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("uniqueuuid.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String option : List.of("help", "inspect", "reload", "status")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(option);
                }
            }
            return completions;
        }
        return List.of();
    }

    private void rebuildRuntime() {
        if (store != null) {
            store.shutdown();
        }

        guardSettings = ConfigLoader.guard(getConfig());
        DatabaseSettings databaseSettings = ConfigLoader.database(getConfig());
        messages = new MessageFormatter(getConfig());
        store = new MysqlIdentityStore(databaseSettings, guardSettings, getLogger());
        policy = new IdentityPolicy(guardSettings);
        resolver = new FloodgateIdentityResolver(getServer().getPluginManager(), guardSettings, getLogger());

        boolean loaded = store.loadAll();
        if (!loaded && !guardSettings.failClosedWithoutCache()) {
            getLogger().warning("UniqueUUID identity cache is unavailable; logins will fail open until reload/startup succeeds.");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageFormatter.color("&6UniqueUUID &7- Floodgate-aware identity guard"));
        sender.sendMessage(MessageFormatter.color("&e/" + label + " status &7View cache and Floodgate state"));
        sender.sendMessage(MessageFormatter.color("&e/" + label + " inspect <player> &7View stored identity for a name"));
        sender.sendMessage(MessageFormatter.color("&e/" + label + " reload &7Reload config and identity cache"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(MessageFormatter.color("&6UniqueUUID status"));
        sender.sendMessage(MessageFormatter.color("&7Version: &f" + getDescription().getVersion()));
        sender.sendMessage(MessageFormatter.color("&7Cache loaded: &f" + store.isCacheLoaded()));
        sender.sendMessage(MessageFormatter.color("&7Cached identities: &f" + store.cacheSize()));
        sender.sendMessage(MessageFormatter.color("&7Floodgate enabled: &f" + resolver.floodgatePluginEnabled()));
        sender.sendMessage(MessageFormatter.color("&7Floodgate prefix: &f" + resolver.floodgatePrefix()));
        sender.sendMessage(MessageFormatter.color("&7Dry run: &f" + guardSettings.dryRun()));
        sender.sendMessage(MessageFormatter.color("&7Fail closed without cache: &f" + guardSettings.failClosedWithoutCache()));
        if (store.lastError() != null) {
            sender.sendMessage(MessageFormatter.color("&7Last database error: &c" + store.lastError()));
        }
    }

    private void sendInspect(CommandSender sender, String playerName) {
        String nameKey = LoginIdentity.key(playerName);
        StoredIdentity stored = store.lookup(nameKey);
        if (stored == null) {
            sender.sendMessage(MessageFormatter.color("&eNo identity is stored for &f" + nameKey + "&e."));
            return;
        }

        sender.sendMessage(MessageFormatter.color("&6UniqueUUID identity for &f" + nameKey));
        sender.sendMessage(MessageFormatter.color("&7Last seen name: &f" + stored.lastSeenName()));
        sender.sendMessage(MessageFormatter.color("&7Kind: &f" + stored.inferredKind()));
        sender.sendMessage(MessageFormatter.color("&7Canonical UUID: &f" + stored.canonicalUuid()));
        if (stored.floodgateUuid() != null) {
            sender.sendMessage(MessageFormatter.color("&7Floodgate UUID: &f" + stored.floodgateUuid()));
        }
        if (stored.lastObservedUuid() != null) {
            sender.sendMessage(MessageFormatter.color("&7Last observed UUID: &f" + stored.lastObservedUuid()));
        }
    }
}
