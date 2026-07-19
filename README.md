# UniqueUUID

Floodgate-aware username and UUID guard for Paper/Spigot servers.

This fork keeps the original `UniqueUUID` plugin name and command so it can replace an existing
`plugins/UniqueUUID.jar` install, but the identity logic has been rebuilt for Geyser/Floodgate
networks.

## What It Protects

- Java players keep using their Mojang/forwarded Java UUID.
- Bedrock players are anchored to their Floodgate/XUID UUID.
- Linked Bedrock accounts can use their linked Java identity.
- Same visible username across Java and Bedrock is blocked cleanly.
- Legacy Bedrock rows accidentally saved as offline/name UUIDs can migrate back to Floodgate UUIDs.
- Login checks use a startup cache instead of opening MySQL on every join.

## Requirements

- Java 17+
- Paper/Spigot-compatible server
- MySQL/MariaDB
- Floodgate on the backend server when Bedrock support is needed
- `send-floodgate-data: true` on the proxy Floodgate config when using Velocity/Bungee

## Build

```powershell
mvn clean verify package
```

The plugin jar is created at:

```text
target/UniqueUUID.jar
```

## Configuration

The plugin preserves the original MySQL keys:

```yaml
mysql:
  host: ''
  port: 3306
  dbname: ''
  username: ''
  password: ''
```

Additional identity settings live under `identity:` and `database:`. Defaults are production-safe
for an availability-first survival server: if the cache cannot load, logins are allowed without
writing any new identity state unless `database.fail-closed-without-cache` is set to `true`.

### Database Settings

| Key | Purpose |
| --- | --- |
| `database.legacy-table` | Existing table used by the original UniqueUUID plugin. |
| `database.meta-table` | Metadata table used by this rebuild for canonical identity details. |
| `database.connect-timeout-ms` | MySQL connection timeout. |
| `database.socket-timeout-ms` | MySQL read/write timeout. |
| `database.fail-closed-without-cache` | Blocks joins if the identity cache cannot load. |
| `database.dry-run` | Logs decisions without writing identity changes. |

### Identity Settings

| Key | Purpose |
| --- | --- |
| `identity.block-cross-platform-name-collisions` | Blocks Java/Bedrock accounts with the same visible name. |
| `identity.migrate-bedrock-offline-uuid-records` | Repairs old Bedrock rows saved as offline/name UUIDs. |
| `identity.detect-bedrock-by-floodgate-name-when-offline-uuid` | Helps classify old Bedrock records when their UUID looks offline-mode. |
| `identity.linked-bedrock-uses-java-identity` | Treats linked Bedrock accounts as their linked Java identity. |
| `identity.remember-allowed-logins` | Stores allowed login observations for faster future checks. |

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/uniqueuuid status` | `uniqueuuid.admin` | Show cache, Floodgate, and database state |
| `/uniqueuuid inspect <player>` | `uniqueuuid.admin` | Show the stored identity for a username |
| `/uniqueuuid reload` | `uniqueuuid.admin` | Reload config and identity cache |

## Notes For Geyser/Floodgate Servers

Floodgate officially supports checking Bedrock players through `FloodgateApi#getPlayer(UUID)` and
`FloodgateApi#isFloodgatePlayer(UUID)`. On proxy networks, backend API access requires
`send-floodgate-data: true` and matching Floodgate keys between proxy and backend servers.

Floodgate also warns that removing the Bedrock username prefix can create duplicate visible-name
edge cases. This plugin handles that by blocking true Java/Bedrock same-name collisions instead of
letting downstream plugins see ambiguous users.

## Operational Notes

- Keep database credentials out of commits and public issue reports.
- Use `/uniqueuuid status` after startup to confirm the cache loaded and Floodgate is detected.
- Use `/uniqueuuid inspect <player>` when investigating a duplicate-name or UUID-mismatch report.
- `database.fail-closed-without-cache: false` is the safer availability-first mode for a live survival server; it avoids mass login failures during a temporary database issue.
