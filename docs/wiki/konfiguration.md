# Konfiguration

Alles steht in `plugins/MRwaitroom/config.yml`, jede Option ist dort kommentiert. Änderungen übernimmt `/mrwaitroom reload`; nur `room.*` braucht einen Neustart.

| Abschnitt | Die wichtigsten Einstellungen | Mehr dazu |
|---|---|---|
| `general` | `language` (Standard `auto`), `fallback-language`, `server-language`, `timezone` | [Sprachen & Texte](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `main` | `server` (Name im Proxy), `address`, `link-url` + `link-key` (MRadmin), `poll-seconds` | [Einrichten](einrichten.md#4-verbindung-zu-mradmin) |
| `return` | Wartezeit, Schubgröße, Versuche, erwartete Neustartdauer | [Ablauf](ablauf.md#zurückschicken) |
| `room` | Welt, Plattform, Chat, andere ausblenden | [Ablauf](ablauf.md#der-raum) |
| `display` | Item, Platz in der Hotbar, Bossbar und Farbe | [Ablauf](ablauf.md) |
| `xaero` | eigene Karten-ID für Xaero's Minimap/World Map | [Einrichten](einrichten.md#6-xaeros-minimap--world-map) |
| `updates` | an/aus, Quelle, Prüfintervall, Hinweis beim Betreten | |

<!-- auto:config -->

- **`config-version`** bitte nicht ändern - daran erkennt MRwaitroom, ob die Datei aktualisiert werden muss.
- **Umlaute kaputt** („Ã¤“ statt „ä“)? Stell die Kodierung im Editor auf **UTF-8**.
