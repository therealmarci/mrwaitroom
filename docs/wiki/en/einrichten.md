# Setting up (Velocity & servers)

This is the layout:

```text
Players ──► Velocity (proxy) ──► main server  (MRadmin, your plugins)
                          └────► waiting room (MRwaitroom)
```

Players always connect to the proxy. If the main server is not running they land in the waiting room; MRadmin sends them over itself before a restart, MRwaitroom sends them back afterwards.

## 1. Velocity
In `velocity.toml`:

```toml
player-info-forwarding-mode = "modern"
bungee-plugin-message-channel = true
# If the connection to the main server breaks unexpectedly (crash), players go to the waiting room.
failover-on-unexpected-server-disconnect = true

[servers]
main = "10.0.0.5:25565"
waitroom = "10.0.0.6:25565"
# Main server first, then the waiting room.
try = ["main", "waitroom"]
```

- `bungee-plugin-message-channel = true` is **required**: MRadmin and MRwaitroom move players between the servers through it.
- The names (`main`, `waitroom`) must match `waiting-room.server` in MRadmin and `main.server` in MRwaitroom.

## 2. Both Paper servers behind the proxy
On **both** servers:
- `server.properties`: `online-mode=false` (Velocity checks the accounts).
- `config/paper-global.yml`: `proxies.velocity.enabled: true`, `online-mode: true` and the same `secret` as in Velocity's `forwarding.secret`.
- Make the servers reachable only in the internal network - from outside only Velocity is open.

## 3. The waiting room server
- **Memory:** 2 GB are plenty.
- `server.properties`: small `view-distance` and `simulation-distance` (e.g. 4 and 2), `spawn-protection=0`.
- MRwaitroom creates the world `waitroom` (empty, with a platform) itself and sends every player there.
- So the waiting room comes back by itself after a crash or update, run it as a service (e.g. systemd with `Restart=always`).

## 4. Connection to MRadmin
On the main server switch on the section `waiting-room` in MRadmin's `config.yml` ([guide](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/)). MRadmin creates a key. Then in the waiting room:

```yaml
main:
  server: "main"
  address: "10.0.0.5:25565"
  link-url: "http://10.0.0.5:8126"
  link-key: "<waiting-room.link.key from MRadmin>"
```

`/mrwaitroom status` shows whether everything fits: main server "ready", port "reachable", MRadmin "connected".

> [!TIP]
> It also works without MRadmin: leave `link-url` empty. The waiting room then only sees whether the main server is up and shows "Server is restarting · since …" without reason and without maintenance times.

## 5. Permissions and bans on both servers
So the waiting room behaves like the main server, **LuckPerms** and **LibertyBans** should use the same database on both servers (e.g. a MariaDB on the proxy machine):
- **LuckPerms:** `storage-method: mariadb` (or `mysql`) with the same credentials and `messaging-service: sql`, so changes apply on both servers right away. Existing data: `/lp export` before, `/lp import` after.
- **LibertyBans:** on both servers with the same database. Then banned players do not even get into the waiting room.
- During a maintenance the waiting room checks the maintenance bypass (`mradmin.maintenance.bypass`) before sending someone back - with a shared LuckPerms it is the same as on the main server.

> [!NOTE]
> The waiting room needs no whitelist of its own: whoever is turned away by the main server several times is disconnected (`return.max-attempts`).

## 6. Xaero's Minimap / World Map
Velocity shows both servers under the same address. MRwaitroom therefore sends Xaero its own map ID (`xaero.world-id`, created on the first start) - so the empty waiting room does not end up on the map of the main server.
