# Befehle

| Befehl | Was er macht | Permission |
|---|---|---|
| `/main` (`/hauptserver`) | Sofort zurück zum Hauptserver - nur, wenn er bereit ist | `mrwaitroom.main` |
| `/mrwaitroom status` | Zustand des Hauptservers, Port, Verbindung zu MRadmin, typische Neustartdauer (Einzelheiten im Hover) | `mrwaitroom.admin.status` |
| `/mrwaitroom send <spieler\|all>` | Spieler jetzt zum Hauptserver schicken | `mrwaitroom.admin.send` |
| `/mrwaitroom reload` | `config.yml` und Sprachdateien neu laden | `mrwaitroom.admin.reload` |
| `/mrwaitroom version` | Version und Update-Status | `mrwaitroom.version` |

Kurzformen: `/waitroom`, `/warteraum`.

Mit dem Starter (auf dem Rechner, im Ordner des Warteraums):

| Befehl | Was er macht |
|---|---|
| `mrserver restart` | Neu starten, sobald es erlaubt ist ([Regeln](ablauf.md#neustarts-des-warteraums)) |
| `mrserver stop` | Anhalten (ohne Prüfung) |
| `mrserver cancel` | Einen wartenden Neustart abbrechen |
| `mrserver status` / `console <befehl>` / `attach` | Wie beim Hauptserver ([Starter](https://mrtechnology.marcelr.at/wiki/mradmin/starter/)) |
