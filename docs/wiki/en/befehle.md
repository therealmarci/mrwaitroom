# Commands

| Command | What it does | Permission |
|---|---|---|
| `/main` (`/hauptserver`) | Back to the main server right away when it is ready - admins only (players go back automatically) | `mrwaitroom.main` |
| `/mrwaitroom status` | State of the main server, port, connection to MRadmin, typical restart length (details in the hover) | `mrwaitroom.admin.status` |
| `/mrwaitroom send <player\|all>` | Send players to the main server now | `mrwaitroom.admin.send` |
| `/mrwaitroom screen set|remove` | Hang up the [board](lobby.md#the-board) (look at a frame of the wall) or take it down | `mrwaitroom.admin.screen` |
| `/mrwaitroom lobby save [x1 y1 z1 x2 y2 z2]` | Save the build as the [lobby](lobby.md#your-own-lobby) of new waiting room worlds | `mrwaitroom.admin.lobby` |
| `/mrwaitroom reload` | Reload `config.yml` and the language files | `mrwaitroom.admin.reload` |
| `/mrwaitroom version` | Version and update status | `mrwaitroom.version` |

Short forms: `/waitroom`, `/warteraum`.

With the starter (on the machine, in the folder of the waiting room):

| Command | What it does |
|---|---|
| `mrserver restart` | Restart as soon as it is allowed ([rules](ablauf.md#restarts-of-the-waiting-room)) |
| `mrserver stop` | Stop (without checks) |
| `mrserver cancel` | Cancel a waiting restart |
| `mrserver status` / `console <command>` / `attach` | Like on the main server ([Starter](https://mrtechnology.marcelr.at/wiki/mradmin/starter/)) |
