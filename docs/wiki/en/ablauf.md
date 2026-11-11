# How it works & display

## What the waiting room knows about the main server
Every few seconds (`main.poll-seconds`) the waiting room checks whether the Minecraft port of the main server answers and asks MRadmin there what is going on. It keeps the last answer of MRadmin (`plugins/MRwaitroom/state.yml`), so reason, countdown and maintenance times are still there while the main server is down - also after a restart of the waiting room itself.

| State | When | Display (item) |
|---|---|---|
| **Restart** | MRadmin's countdown has ended, server gone | "⏳ Restart · about 0:40 left", then "Back in a moment · since …" |
| **Shut down** | `/mradmin stop` has ended | "■ Server is off · since …" |
| **Not reachable** | Server gone without notice (crash, `/stop`) | "↻ Server is restarting · since …" |
| **Starting** | Port answers, MRadmin does not report "ready" yet | "↻ Server is starting · almost there" |
| **Maintenance** | Server runs, MRadmin reports a running maintenance | "🛠 Maintenance · until 18:00" or "open end" |
| **Ready** | MRadmin reports "ready" | "➜ Back to the server …" |

Plus the **bossbar** (`display.bossbar`) with reason or maintenance title and progress, and **on joining** a short chat line with details in the hover and the warnings that are currently active in MRadmin under **News**.

> [!NOTE]
> The item gets a new name every second - that way the name stays visible above the hotbar. It stays selected (`display.slot`), the inventory is locked.

## Time left
The waiting room estimates the time left of a restart from the **typical length of the last restarts** (median of the last 10, measured from going down to "ready"). Until two restarts were measured, `return.estimate-seconds` applies (default 90 s). Stops do not count.

## Sending back
- As soon as the main server is **ready**, the waiting room waits `return.delay-seconds` (default 5 s) and then sends everyone back **in batches**: `return.batch-size` players (5) every `return.batch-seconds` (2 s), longest waiting first.
- **During a maintenance** only players with MRadmin's maintenance bypass go back (`mradmin.maintenance.bypass`) - the others wait until the end.
- **Turned away?** If a player does not arrive `return.max-attempts` times (3) (ban, whitelist), the waiting room disconnects them with a note.
- Players with `mrwaitroom.stay` are never sent back by themselves.
- `/main` sends a player back right away, `/mrwaitroom send <player|all>` the team.

## The room
An empty world (`room.world`) with a small platform at the spawn. No damage, no hunger, adventure mode, blocks and inventory locked, whoever falls off stands at the spawn again. The chat is off (`room.chat`), with `mrwaitroom.chat` one may write anyway. Other players can be hidden (`room.hide-players`).
