# Working with MRadmin

MRwaitroom runs **on its own**, the two plugins do not know each other: MRadmin provides info as JSON through a small endpoint (`waiting-room.link`), MRwaitroom reads it. How the plugins work together in general: [Working together](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/).

| Feature | Without MRadmin | With MRadmin on the main server |
|---|---|---|
| Players to the waiting room before a restart | – (they are kicked; on crashes Velocity helps) | ✅ right before going down |
| Waiting room instead of a kick during maintenance | – | ✅ |
| Reason of the restart | – | ✅ |
| Time left | from the measured downtimes | ✅ from the measured restarts |
| Show maintenance with its end, respect the bypass | – | ✅ |
| Warnings from "News" | – | ✅ |
| "Ready" only once the server has fully started | as soon as the port answers | ✅ |

Setting up on MRadmin's side: [Waiting room (proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
