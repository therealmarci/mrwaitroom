# How it works & display

## What the waiting room knows about the main server
Every few seconds (`main.poll-seconds`) the waiting room checks whether the Minecraft port of the main server answers and asks MRadmin there what is going on. It keeps the last answer of MRadmin (`plugins/MRwaitroom/state.yml`), so reason, countdown and maintenance times are still there while the main server is down - also after a restart of the waiting room itself.

| State | When | Bossbar (top, renewed every second) |
|---|---|---|
| **Restart** | MRadmin's countdown has ended, server gone | "Restart | 1:42" (countdown from 2:00), then "Restart | back in a moment" |
| **Shut down** | `/mradmin stop` has ended | "Server off | open end" |
| **Not reachable** | Server gone without notice (crash, `/stop`) | "Server restarting | 1:35" (countdown from 2:00) |
| **Starting** | Port answers, MRadmin does not report "ready" yet | "Server starting | almost there" |
| **Maintenance** | MRadmin reports a running maintenance - also while the server restarts or is off during it | "Maintenance | 1:20:00" (countdown to its end) or "Maintenance | open end" |
| **Ready** | MRadmin reports "ready" | "Server ready | 0:08" |

The bossbar (`display.bossbar`) always has the same layout - **state | countdown** -, reason and details are in the clock menu. It fills up until the expected return or the end of the maintenance. **On joining** there is a short chat line with details in the hover, plus the warnings that are currently active in MRadmin under **News**.

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

## Countdown of restarts
On a restart or crash the bossbar counts down from `return.estimate-seconds` (default **2:00**). If the main server is back earlier, players go back earlier; if it takes longer, it says "back in a moment".

## Sending back
- As soon as the main server is **ready**, a **countdown** of `return.delay-seconds` runs (default 10 s): bossbar "Server ready | 0:08", one chat line and a tick in the last 5 seconds. Then the waiting room sends everyone back **in batches**: `return.batch-size` players (5) every `return.batch-seconds` (2 s), longest waiting first.
- **During a maintenance** only team members with the right "On the server during maintenance" go back (see [rights](#rights-and-admin-mode)) - the others wait until the end.
- **Turned away?** If a player does not arrive `return.max-attempts` times (3) (ban, whitelist), the waiting room disconnects them with a note. Whoever bounces back shortly after keeps counting - nobody goes back and forth forever. During a maintenance nobody is disconnected: the player simply stays in the waiting room until the end.
- Whoever has the right "Stay in the waiting room" is never sent back by themselves.
- Going back by hand only for the team: with the green item **Back to the server** (hotbar right) or the button in the clock menu - whoever may "Stay in the waiting room" or be "On the server during maintenance" -, admins with `/main` (`mrwaitroom.main`), staff with `/mrwaitroom send <player|all>`. Players cannot trigger it themselves.

## Rights and admin mode
What the team may do here comes from the table **Rights per role** in MRadmin (`waiting-room.rights`, [guide](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/#rights-around-the-waiting-room)) - per role Admin, Staff, Supporter each **No / Yes / Only in admin mode**. The waiting room keeps the table, so it also applies while the main server is down. The role comes from `mradmin.groups.admin|staff|support` (with LuckPerms on a shared database the same as on the main server). Default: admins only in admin mode, staff and supporters nothing - an admin without admin mode waits like everyone.

**The hotbar** is symmetric, places without the right stay empty:

| Place | Item | Who |
|---|---|---|
| left (1) | **Admin mode** on/off (right click) | right "Admin mode in the waiting room" |
| middle (`display.slot`, 5) | **Clock** with the state and the menu | everyone |
| right (9) | **Back to the server** | right "Stay in the waiting room" or "On the server during maintenance" |

**In admin mode** you are in creative mode, can fly (e.g. to build on the lobby) and are invisible to waiting players; you see everyone. The admin mode counts across the **whole network**: switched on here, you arrive on the main server in admin mode; switched off here, you get your inventory back there. Whoever is in admin mode on the main server is in admin mode here too.

## The room
A world of its own (`room.world`) with the [lobby](lobby.md) and the [board](lobby.md#the-board). **Everyone lands at the spawn on joining** - wherever they stood last time. No damage, no hunger, adventure mode, blocks and inventory locked, whoever falls off stands at the spawn again. The chat is off (`room.chat`), with `mrwaitroom.chat` one may write anyway. Other players can be hidden (`room.hide-players`).

## Restarts of the waiting room
With MRadmin's starter (`mrserver setup --role waitroom`) the waiting room restarts:

- **by hand:** in MRadmin on **Server → Overview** (tile **Waiting room server**, **Restart**) or with `mrserver restart` in the console of the machine,
- **planned:** every day at the times in `server.restart-times`.

But only when it is not needed: the main server is ready, **no maintenance** runs, **no restart of the main server** comes within the next 15 minutes and **nobody waits**. Otherwise it refuses a restart by hand with the reason (except waiting players - then it restarts once they are back), and a planned restart waits until everything fits. `mrserver stop` stops it without these checks, `mrserver cancel` cancels a waiting restart.
