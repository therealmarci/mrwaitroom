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

The connection always starts at the waiting room, MRadmin never connects to it. MRadmin cannot send it files either - only tell it which official version to load.

Setting up on MRadmin's side: [Waiting room (proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
