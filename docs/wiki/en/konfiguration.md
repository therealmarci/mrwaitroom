# Configuration

Everything is in `plugins/MRwaitroom/config.yml`, every option is commented there. `/mrwaitroom reload` applies changes; only `room.*` needs a restart.

| Section | The most important settings | More |
|---|---|---|
| `general` | `language` (default `auto`), `fallback-language`, `server-language`, `timezone` | [Languages & texts](https://mrtechnology.marcelr.at/wiki/allgemein/sprachen/) |
| `main` | `server` (name in the proxy), `address`, `link-url` + `link-key` (MRadmin), `poll-seconds` | [Setting up](einrichten.md#4-connection-to-mradmin) |
| `return` | Wait, batch size, tries, expected restart length | [How it works](ablauf.md#sending-back) |
| `room` | World, platform, chat, hide others | [How it works](ablauf.md#the-room) |
| `display` | Item, slot in the hotbar, bossbar and colour | [How it works](ablauf.md) |
| `xaero` | Own map ID for Xaero's Minimap/World Map | [Setting up](einrichten.md#6-xaeros-minimap--world-map) |
| `updates` | on/off, source, check interval, notice on join | |

<!-- auto:config -->

- Please do not change **`config-version`** - MRwaitroom uses it to see whether the file needs updating.
- **Broken umlauts** ("Ã¤" instead of "ä")? Set the encoding in your editor to **UTF-8**.
