# MRwaitroom

**No more kicks on restarts and maintenance:** behind a proxy (Velocity) your players wait in a small waiting room, see the reason and the time left and go back by themselves as soon as the main server is ready again.

## What can MRwaitroom do?
- **Waiting room** on a second, small Paper server: empty world with a platform, no damage, no hunger, chat off.
- **Time left at a glance:** an item in the hotbar always shows how long it takes ("Restart · about 0:40 left", "Maintenance · until 18:00"), plus a bossbar with reason and progress.
- **Back by themselves:** as soon as the main server is ready, the waiting room sends everyone back in small batches. During maintenance only those with the maintenance bypass.
- **Learns along:** it estimates the time left from the length of the last restarts.
- **Even better with MRadmin:** MRadmin sends players over before a restart instead of letting them be kicked, and reports reason, countdown, maintenance and warnings. Without MRadmin the waiting room only sees whether the main server is up.
- **Xaero-friendly:** its own map ID, so Xaero's Minimap does not draw the empty waiting room over the map of the main server.

| At a glance | |
|---|---|
| Server | Paper 26.3 (or a fork like Purpur), Java 25 - as a second server behind **Velocity** |
| Requires | Velocity; recommended: MRadmin on the main server, LuckPerms and LibertyBans with a shared database |
| Languages | German, English - every player automatically in the language of their Minecraft client |
| Status | beta (0.x) |

Go on with the [quick start](schnellstart.md) or the detailed [setup](einrichten.md).
