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
| Konfiguration und Updates im Browser (**Server → Warteraum → Plugins**) | – | ✅ |

## Verwaltung aus MRadmin
Der Warteraum meldet sich alle 30 Sekunden bei MRadmin (Version, Konfiguration, Formular). Dann steht er dort unter **Server → Warteraum → Plugins**:

- **Konfiguration bearbeiten** wie bei jedem Plugin. Der Warteraum holt die neue `config.yml` bei seiner nächsten Abfrage ab, legt eine Sicherung an (`config.backup-remote.yml`) und lädt neu.
- **Aktualisieren** bestellt die neue Version. Der Warteraum lädt sie selbst - nur von `github.com/therealmarci/mrwaitroom/releases/…` und nur mit passender SHA-256-Prüfsumme - in den Update-Ordner und startet neu, sobald niemand wartet und der Hauptserver bereit ist (`updates.restart-when-idle`).

> [!WARNING]
> Den Neustart muss etwas anderes ausführen, z.B. systemd mit `Restart=always` - sonst bleibt der Warteraum nach dem Update aus. Ohne so einen Dienst `updates.restart-when-idle: false` setzen; das Update kommt dann beim nächsten Neustart von Hand.

Konfiguration und Updates holt der Warteraum selbst ab. MRadmin kann ihm keine fremden Dateien schicken - nur sagen, welche offizielle Version er laden soll.

## Leistung, Protokoll und Konsole in MRadmin
MRadmin zeigt den Warteraum wie den Hauptserver - unter **Server → Warteraum**: **Leistung** (TPS, Tick-Zeit, CPU, Arbeitsspeicher, Spieler, Welten, mit Aufzeichnung und Lag-Warnung), **Protokoll** (Ereignisse dieses Servers und sein Server-Log der letzten 14 Tage) und **Konsole** (Ausgabe live, Befehle mit denselben Regeln wie beim Hauptserver; `agent.console-commands: false` schaltet Befehle hier ab). Dafür öffnet der Warteraum einen eigenen Port (`agent.port`, Standard 8127, `0` = aus), den MRadmin jede Sekunde abfragt - geschützt mit demselben Schlüssel (`main.link-key`). Den Port meldet der Warteraum selbst, MRadmin braucht keine Einstellung.

> [!TIP]
> Mit `agent.bind` auf der internen Adresse (z.B. `10.0.0.6`) ist der Port von außen nicht erreichbar. In der Firewall nur den Hauptserver zum Warteraum durchlassen.

Einrichten auf der MRadmin-Seite: [Warteraum (Proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
