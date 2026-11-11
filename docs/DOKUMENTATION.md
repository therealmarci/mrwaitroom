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
| `link/MainLink` | fragt alle `main.poll-seconds` (eigener Thread): Port-Check + MRadmin. Zustandsmaschine `Phase` (RESTART, STOP, CRASH, STARTING, MAINTENANCE, READY), Ausfallart aus der letzten Antwort (`kindOf`), Neustartdauer gemessen (Median der letzten 10), alles in `state.yml` gespeichert. Liefert einen unveränderlichen Schnappschuss `View` |
| `room/Room` | Welt anlegen (Void-Generator + Plattform, einmalig per Marker im World-PDC - MC 26 legt Zusatzwelten im Hauptweltordner ab), Schutz-Listener, Chat |
| `room/Display` | jede Sekunde: Item (neuer Name hält die Anzeige über der Hotbar sichtbar, Anzahl = Minuten) und Bossbar pro Spieler in seiner Sprache; Chatzeile beim Betreten |
| `room/Returner` | Rückkehr in Schüben über den BungeeCord-Kanal (`Connect`), Wartung nur mit dem gemeldeten Bypass-Recht, Kick nach `max-attempts` |
| `room/XaeroWorldId` | schickt bei `PlayerRegisterChannelEvent` für `xaerominimap:main` / `xaeroworldmap:main` Paket 0 + int (Format aus Xaero 26.6.2: `LevelMapProperties`, Index 0) |
| `command/*` | `/mrwaitroom`, `/main` |

## Protokoll mit MRadmin
`GET <link-url>/waiting-room/status` mit `Authorization: Bearer <key>` → JSON (`protocol` 1):
`time`, `name`, `ready`, `stopping`, `online`, `restart {at, restart, reason}`, `maintenance {active, title, start, end, bypass}`,
`alerts [{id, title {de, en}}]`. Gegenstück: `mradmin/src/main/java/at/marcelr/mradmin/network/WaitingRoom.java`.
Neue Felder nur additiv; `protocol` nur bei Änderungen erhöhen, die alte Warteräume nicht lesen können.

## Tests
`LanguageFilesTest` (DE/EN gleiche Schlüssel, Hover), `WebConfigFormTest` (jede Einstellung hat ein Feld),
`MainStatusTest` (JSON lesen, Ausfallart), `VersionsTest` (Versionsschema).
