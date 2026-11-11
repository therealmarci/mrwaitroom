# MRwaitroom - Code-Aufbau

Paper-Plugin für einen Warteraum hinter einem Velocity-Proxy. Kein Bezug zu anderen MR-Plugins im Code: MRadmin liefert
über seine Schnittstelle `waiting-room.link` reines JSON, MRwaitroom liest es.

## Pakete
| Klasse | Aufgabe |
|---|---|
| `MRwaitroom` | Hauptklasse: Config, Texte, Start/Stopp der Teile, Xaero-ID beim ersten Start erzeugen |
| `Settings` | alle Werte der `config.yml`, einmal pro (Re)Load geprüft |
| `ConfigUpdater`, `Messages`, `UpdateChecker`, `util/Versions`, `util/LanguageChoice` | gemeinsame Bausteine aller MR-Plugins (von MRsleep übernommen) |
| `link/MainStatus` | die Antwort von MRadmin (`GET /waiting-room/status`), tolerant gelesen (unbekannte Felder egal, fehlende neutral) |
| `link/Remote` | Verwaltung aus MRadmin: Meldung alle 30 s (`POST /waiting-room/report`: Version, web-config.yml, config.yml, Standardwerte, erledigte/fehlgeschlagene Aufträge), Aufträge aus der Statusantwort (`orders`): `config` (Datei übernehmen, Sicherung, Reload), `update` (nur Release-Adresse des eigenen Repos, SHA-256, plugin.yml-Name prüfen, in den Update-Ordner unter eigenem JAR-Namen), Neustart bei Leerlauf |
| `link/MainLink` | fragt alle `main.poll-seconds` (eigener Thread): Port-Check + MRadmin. Zustandsmaschine `Phase` (RESTART, STOP, CRASH, STARTING, MAINTENANCE, READY), Ausfallart aus der letzten Antwort (`kindOf`), Ausfälle werden gemessen (nur für die Konsole), der Countdown der Bossbar ist fest `return.estimate-seconds`, alles in `state.yml` gespeichert. Liefert einen unveränderlichen Schnappschuss `View` |
| `room/Room` | Welt anlegen (Void-Generator + Plattform, einmalig per Marker im World-PDC - MC 26 legt Zusatzwelten im Hauptweltordner ab), Schutz-Listener, Chat |
| `room/Display` | jede Sekunde: Item (neuer Name hält die Anzeige über der Hotbar sichtbar, Anzahl = Minuten) und Bossbar pro Spieler in seiner Sprache; Chatzeile beim Betreten |
| `room/Screen`, `Board`, `Pixels` | Anzeigetafel: 4 × 2 Rahmen mit Karten, `screen.yml` (Lage, Kartennummern - nach dem Start bekommen die Karten ihren Renderer zurück). `Board` zeichnet 512 × 256 Pixel pro Sprache nur bei Änderung (Versionsnummer), `Piece` überträgt je Spieler nur neue Versionen. `Pixels` = Minecraft-Schrift ganzzahlig skaliert ohne Java2D-Fonts; Umlaute/ß selbst gebaut (Bukkits `MinecraftFont` hat außerhalb von ASCII Datenmüll) |
| `room/Lobby` | Lobby als Struktur (`lobby.nbt` + `lobby.yml`: Ursprung, Spawn, Tafel), mitgeliefert unter `resources/lobby/`, eigene unter `plugins/MRwaitroom/lobby/`. Speichern findet den Bau per Chunk-Snapshots (Höhenkarte) asynchron; `place` nur beim Anlegen der Welt, die Tafel 2 s später |
| `room/Returner` | Rückkehr in Schüben über den BungeeCord-Kanal (`Connect`), Wartung und „bleiben“ nach `Rights`, Kick nach `max-attempts` |
| `room/Rights` | Tabelle `waiting-room.rights` aus der Statusantwort von MRadmin (`rights`, sonst MRadmins Standard), Rolle = höchste von `mradmin.groups.*` |
| `room/AdminMode` | Admin-Modus fürs ganze Netzwerk: Item links in der Hotbar, Wünsche per `POST /waiting-room/admin-mode` an MRadmin (wiederholt, bis `adminMode` der Statusantwort passt), Kreativ + Fliegen + Sichtbarkeit (`Room.refreshVisibility`) |
| `room/XaeroWorldId` | schickt bei `PlayerRegisterChannelEvent` für `xaerominimap:main` / `xaeroworldmap:main` Paket 0 + int (Format aus Xaero 26.6.2: `LevelMapProperties`, Index 0) |
| `server/StarterBridge` | Anbindung an MRadmins Starter ohne MRadmin: Herzschlag (jede Sekunde), `action` vor dem Herunterfahren, `launch.properties` aus dem Abschnitt `server`, `request`/`request.answer` (`mrserver restart|stop|cancel`); Ordner `plugins/MRadmin/starter/` aus der Umgebungsvariable `MRADMIN_STARTER` |
| `server/Restarts` | Neustarts von Hand (Auftrag `restart` aus MRadmin, `mrserver restart`) und geplant (`server.restart-times`); `blocked()` = no-starter, maintenance, main-not-ready, main-restart-soon, players-waiting |
| `command/*` | `/mrwaitroom`, `/main` |

## Protokoll mit MRadmin
`GET <link-url>/waiting-room/status` mit `Authorization: Bearer <key>` → JSON (`protocol` 1):
`time`, `name`, `ready`, `stopping`, `online`, `restart {at, restart, reason}`, `maintenance {active, title, start, end, bypass}`,
`alerts [{id, title {de, en}}]`, `orders [{id, plugin, type: config|update, …}]`. Meldung: `POST <link-url>/waiting-room/report`
(Gegenstück `network/RemotePlugins.java`, Aufträge bleiben offen, bis sie als `done`/`failed` gemeldet sind). Gegenstück der Abfrage: `mradmin/src/main/java/at/marcelr/mradmin/network/WaitingRoom.java`.
Neue Felder nur additiv; `protocol` nur bei Änderungen erhöhen, die alte Warteräume nicht lesen können.

## Tests
`LanguageFilesTest` (DE/EN gleiche Schlüssel, Hover), `WebConfigFormTest` (jede Einstellung hat ein Feld),
`MainStatusTest` (JSON lesen, Ausfallart), `VersionsTest` (Versionsschema).
