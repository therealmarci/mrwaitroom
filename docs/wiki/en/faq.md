# FAQ

**`/mrwaitroom status` shows "MRadmin: no answer (wrong key)".**
`main.link-key` does not match `waiting-room.link.key` in MRadmin's `config.yml`. Copy the value, `/mrwaitroom reload`.

**"MRadmin: no answer (HTTP 429)"?**
Too many wrong keys in a short time - MRadmin then slows down for a minute. Fix the key and wait a moment.

**"MRadmin: no answer (ConnectException)" although the main server runs?**
`waiting-room.link.bind` in MRadmin must be an address the waiting room can reach (not `127.0.0.1` if the servers run on different machines). Open the port (default 8126) in the firewall between the machines.

**Players are kicked on a restart instead of landing in the waiting room.**
`velocity.toml` needs `bungee-plugin-message-channel = true`, and the name in `waiting-room.server` (MRadmin) must match the entry under `[servers]`. With `/stop` in the console Paper kicks everyone - use `/mradmin restart` or `/mradmin stop`.

**Players do not go back.**
`main.server` must be the name of the main server in `velocity.toml`. `/mrwaitroom status` shows whether the main server is "ready". During a maintenance only players with the maintenance bypass go back.

**The countdown does not fit my server.**
On restarts it counts down from `return.estimate-seconds` (default 2 minutes). If your server usually takes longer, raise the value - players go back earlier anyway as soon as it is ready.

**Does the waiting room need plugins like LuckPerms?**
Recommended: LuckPerms and LibertyBans with the same database as the main server - see [Setting up](einrichten.md#5-permissions-and-bans-on-both-servers).
