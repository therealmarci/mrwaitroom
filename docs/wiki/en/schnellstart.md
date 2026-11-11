# Quick start

You need: a **Velocity** proxy, your **main server** and a **second Paper server** for the waiting room (2 GB of memory are enough).

1. **[Download MRwaitroom](https://mrtechnology.marcelr.at/download/mrwaitroom/)** and put the JAR into `plugins/` of the **waiting room server**. Start the server once.
2. **Velocity** (`velocity.toml`):
   ```toml
   bungee-plugin-message-channel = true

   [servers]
   main = "10.0.0.5:25565"
   waitroom = "10.0.0.6:25565"
   try = ["main", "waitroom"]
   ```
3. **On the main server with MRadmin** set `waiting-room.enabled: true` and `waiting-room.link.bind` to its internal address in `config.yml`, then `/mradmin reload` - see [Waiting room in MRadmin](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
4. **In the waiting room** in `plugins/MRwaitroom/config.yml`:
   ```yaml
   main:
     server: "main"                    # name in velocity.toml
     address: "10.0.0.5:25565"         # main server in the internal network
     link-url: "http://10.0.0.5:8126"  # waiting-room.link of MRadmin
     link-key: "…"                     # waiting-room.link.key of MRadmin
   ```
   Then `/mrwaitroom reload` and `/mrwaitroom status` - it should say "ready" and "MRadmin: connected".

Done. On the next `/mradmin restart` everyone moves to the waiting room and comes back by themselves afterwards.

> [!TIP]
> Velocity forwarding, LuckPerms and LibertyBans for both servers: [Setting up](einrichten.md).
