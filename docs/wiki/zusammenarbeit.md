# Zusammenarbeit mit MRadmin

MRwaitroom läuft **allein**, die beiden Plugins kennen sich nicht: MRadmin liefert über eine kleine Schnittstelle (`waiting-room.link`) Infos im JSON-Format, MRwaitroom liest sie. Wie die Plugins allgemein zusammenarbeiten: [Zusammenarbeit](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/).

| Funktion | Ohne MRadmin | Mit MRadmin auf dem Hauptserver |
|---|---|---|
| Spieler vor dem Neustart in den Warteraum | – (sie fliegen; bei Abstürzen hilft Velocity) | ✅ kurz vor dem Herunterfahren |
| Bei Wartung in den Warteraum statt Kick | – | ✅ |
| Grund des Neustarts | – | ✅ |
| Countdown | 2:00 bei jedem Ausfall | ✅ 2:00 bei Neustarts, bei Wartung bis zu ihrem Ende |
| Wartung mit Ende anzeigen, Bypass beachten | – | ✅ |
| Warnungen aus „Neuigkeiten“ | – | ✅ |
| „Bereit“ erst, wenn der Server fertig gestartet ist | sobald der Port antwortet | ✅ |
| Konfiguration und Updates im Browser (**Server → Plugins**) | – | ✅ |

## Verwaltung aus MRadmin
Der Warteraum meldet sich alle 30 Sekunden bei MRadmin (Version, Konfiguration, Formular). Dann steht er dort unter **Server → Plugins**:

- **Konfiguration bearbeiten** wie bei jedem Plugin. Der Warteraum holt die neue `config.yml` bei seiner nächsten Abfrage ab, legt eine Sicherung an (`config.backup-remote.yml`) und lädt neu.
- **Aktualisieren** bestellt die neue Version. Der Warteraum lädt sie selbst - nur von `github.com/therealmarci/mrwaitroom/releases/…` und nur mit passender SHA-256-Prüfsumme - in den Update-Ordner und startet neu, sobald niemand wartet und der Hauptserver bereit ist (`updates.restart-when-idle`).

> [!WARNING]
> Den Neustart muss etwas anderes ausführen, z.B. systemd mit `Restart=always` - sonst bleibt der Warteraum nach dem Update aus. Ohne so einen Dienst `updates.restart-when-idle: false` setzen; das Update kommt dann beim nächsten Neustart von Hand.

Die Verbindung geht immer vom Warteraum aus, MRadmin verbindet sich nie zu ihm. MRadmin kann ihm auch keine fremden Dateien schicken - nur sagen, welche offizielle Version er laden soll.

Einrichten auf der MRadmin-Seite: [Warteraum (Proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
