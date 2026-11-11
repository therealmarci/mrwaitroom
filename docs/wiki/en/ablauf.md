# How it works & display

## What the waiting room knows about the main server
Every few seconds (`main.poll-seconds`) the waiting room checks whether the Minecraft port of the main server answers and asks MRadmin there what is going on. It keeps the last answer of MRadmin (`plugins/MRwaitroom/state.yml`), so reason, countdown and maintenance times are still there while the main server is down - also after a restart of the waiting room itself.

| State | When | Bossbar (top, renewed every second) |
|---|---|---|
| **Restart** | MRadmin's countdown has ended, server gone | "Restart · about 0:40 left – reason", then "Back in a moment · since …" |
| **Shut down** | `/mradmin stop` has ended | "Server is off · since … – reason" |
| **Not reachable** | Server gone without notice (crash, `/stop`) | "Server is restarting · since …" |
| **Starting** | Port answers, MRadmin does not report "ready" yet | "Server is starting · almost there" |
| **Maintenance** | MRadmin reports a running maintenance - also while the server restarts or is off during it | "Maintenance until 18:00 · about 1:20:00 left – title" or "open end" |
| **Ready** | MRadmin reports "ready" | "Server is ready · going back" |

The bossbar (`display.bossbar`) fills up until the expected return or the end of the maintenance. **On joining** there is a short chat line with details in the hover, plus the warnings that are currently active in MRadmin under **News**.

## The clock and its menu
The **clock in the hotbar** (`display.item`, stays selected on `display.slot`) carries the state in its name, e.g. "⏳ Restart" or "🛠 Maintenance until 18:00". A **right click** opens a menu with all details:

| Field | Content |
|---|---|
| State (top) | like the bossbar |
| Reason | reason of the restart or stop |
| Time | gone since, about left, how long a restart usually takes |
| Maintenance | title, start and end - running or planned |
| Warnings | the active warnings from MRadmin |
| Back to the server | only green when the server is ready (grey otherwise) |

The menu renews every second while it is open.

> [!NOTE]
> The name of the clock only changes when the state changes - every change of the item in hand plays an animation in the game. That is why the countdown runs in the bossbar. The inventory is locked.

## Time left
The waiting room estimates the time left of a restart from the **typical length of the last restarts** (median of the last 10, measured from going down to "ready"). Until two restarts were measured, `return.estimate-seconds` applies (default 90 s). Stops do not count.

## Sending back
- As soon as the main server is **ready**, the waiting room waits `return.delay-seconds` (default 5 s) and then sends everyone back **in batches**: `return.batch-size` players (5) every `return.batch-seconds` (2 s), longest waiting first.
- **During a maintenance** only players with MRadmin's maintenance bypass go back (`mradmin.maintenance.bypass`) - the others wait until the end.
- **Turned away?** If a player does not arrive `return.max-attempts` times (3) (ban, whitelist), the waiting room disconnects them with a note. Whoever bounces back shortly after keeps counting - nobody goes back and forth forever. During a maintenance nobody is disconnected: the player simply stays in the waiting room until the end.
- Players with `mrwaitroom.stay` are never sent back by themselves.
- `/main` sends a player back right away, `/mrwaitroom send <player|all>` the team.

## The room
An empty world (`room.world`) with a small platform at the spawn. No damage, no hunger, adventure mode, blocks and inventory locked, whoever falls off stands at the spawn again. The chat is off (`room.chat`), with `mrwaitroom.chat` one may write anyway. Other players can be hidden (`room.hide-players`).

## Restarts of the waiting room
With MRadmin's starter (`mrserver setup --role waitroom`) the waiting room restarts:

- **by hand:** in MRadmin on **Server → Overview** (tile **Waiting room server**, **Restart**) or with `mrserver restart` in the console of the machine,
- **planned:** every day at the times in `server.restart-times`.

But only when it is not needed: the main server is ready, **no maintenance** runs, **no restart of the main server** comes within the next 15 minutes and **nobody waits**. Otherwise it refuses a restart by hand with the reason (except waiting players - then it restarts once they are back), and a planned restart waits until everything fits. `mrserver stop` stops it without these checks, `mrserver cancel` cancels a waiting restart.
