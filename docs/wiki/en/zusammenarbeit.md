# Working with MRadmin

MRwaitroom runs **on its own**, the two plugins do not know each other: MRadmin provides info as JSON through a small endpoint (`waiting-room.link`), MRwaitroom reads it. How the plugins work together in general: [Working together](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/).

| Feature | Without MRadmin | With MRadmin on the main server |
|---|---|---|
| Players to the waiting room before a restart | – (they are kicked; on crashes Velocity helps) | ✅ right before going down |
| Waiting room instead of a kick during maintenance | – | ✅ |
| Reason of the restart | – | ✅ |
| Countdown | 2:00 on every outage | ✅ 2:00 on restarts, during maintenance until its end |
| Show maintenance with its end, respect the bypass | – | ✅ |
| Warnings from "News" | – | ✅ |
| "Ready" only once the server has fully started | as soon as the port answers | ✅ |
| Configuration and updates in the browser (**Server → Plugins**) | – | ✅ |

## Management from MRadmin
The waiting room reports to MRadmin every 30 seconds (version, configuration, form). It then shows up there under **Server → Plugins**:

- **Edit the configuration** like for every plugin. The waiting room picks up the new `config.yml` on its next question, keeps a backup (`config.backup-remote.yml`) and reloads.
- **Update** orders the new version. The waiting room downloads it itself - only from `github.com/therealmarci/mrwaitroom/releases/…` and only with the right SHA-256 checksum - into the update folder and restarts as soon as nobody waits and the main server is ready (`updates.restart-when-idle`).

> [!WARNING]
> Something else has to do the restart, e.g. systemd with `Restart=always` - otherwise the waiting room stays off after the update. Without such a service set `updates.restart-when-idle: false`; the update then comes with your next restart by hand.

The waiting room fetches configuration and updates itself. MRadmin cannot send it any other files - only tell it which official version to download.

## Performance in MRadmin
MRadmin shows the performance (TPS, tick time, CPU, memory, players, worlds) of the waiting room like the one of the main server - under **Server → Waiting room → Performance**, with recording and lag warning. For this the waiting room opens a port of its own (`agent.port`, default 8127, `0` = off) that MRadmin asks every second - protected with the same key (`main.link-key`). The waiting room reports the port itself, MRadmin needs no setting.

> [!TIP]
> With `agent.bind` on the internal address (e.g. `10.0.0.6`) the port cannot be reached from outside. In the firewall only let the main server through to the waiting room.

Setting up on MRadmin's side: [Waiting room (proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
