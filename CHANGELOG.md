# Changelog

Alle wichtigen Änderungen an MRwaitroom. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
veröffentlicht als `mc<minecraft>-STA|BET-<version>-bu<build>` (siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Beta:** Alle 0.x-Versionen sind Testversionen. Bis 1.0.0 können sich Config, Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Rechte aus MRadmin:** Wer im Warteraum bleiben darf, wer während einer Wartung auf den Hauptserver darf und wer den Admin-Modus nutzen darf, bestimmt jetzt die Tabelle `waiting-room.rights` in MRadmin (pro Team-Rolle Nein / Ja / Nur im Admin-Modus). Der Warteraum bekommt sie über die Verbindung und behält sie, solange der Hauptserver aus ist. Standard: Admins nur im Admin-Modus, Staff und Supporter nichts.
- **Admin-Modus im Warteraum:** Item links in der Hotbar (Rechtsklick an/aus): Kreativmodus, fliegen, unsichtbar für Wartende. Der Admin-Modus gilt im ganzen Netzwerk - hier eingeschaltet, kommt man auf dem Hauptserver im Admin-Modus an (braucht MRadmin mit dieser Version).
- **Symmetrische Hotbar:** links Admin-Modus, Mitte Uhr, rechts „Zurück zum Server“ - ohne Recht bleibt der Platz leer.

### Entfernt
- **Permissions `mrwaitroom.stay` und `mrwaitroom.visit`:** ersetzt durch die Rechte „Im Warteraum bleiben“ und „Bei Wartung auf den Server“ der Tabelle in MRadmin. Besuche aus MRadmins Menü braucht der Warteraum nicht mehr anzukündigen.

### Behoben & geändert
- **Während einer Wartung zurück** nur noch mit dem Recht „Bei Wartung auf den Server“ statt mit dem Wartungs-Bypass - ein Admin ohne Admin-Modus wartet wie alle.
- **Wartung flackerte mit „Server startet“:** Startet der Hauptserver während einer Wartung neu, behält der Warteraum die laufende Wartung, bis der Hauptserver bereit ist - keine Anzeige „startet“ dazwischen mehr.
- **Weniger Chatzeilen:** „Es geht zurück zum Server …“ fällt weg (die Zeile mit dem Countdown davor sagt es schon, die Bossbar zeigt es), `/main` meldet dieselbe Absage wie das Item.

## [BET-0.1.0-bu001] - 2026-11-11

### Neu
- **Warteraum hinter einem Velocity-Proxy:** Während der Hauptserver neu startet, gestoppt ist oder in Wartung ist, warten die Spieler hier und kommen automatisch zurück, sobald er bereit ist - in Schüben (`return.batch-size`, `return.batch-seconds`), während einer Wartung nur mit Wartungs-Bypass. Wer mehrmals abgewiesen wird (Bann, Whitelist), wird getrennt (`return.max-attempts`).
- **Restzeit im Blick:** Uhr in der Hotbar mit dem Zustand („⏳ Neustart“, „🛠 Wartung bis 18:00“, `display.item`, `display.slot`), Rechtsklick öffnet ein Menü mit Grund, Zeiten, Wartung und Warnungen; Bossbar mit Countdown, Grund und Fortschritt (`display.bossbar`, „Neustart · noch ca. 0:40“), beim Betreten eine Chatzeile mit Einzelheiten im Hover und den aktiven Warnungen. Bei Neustart und Absturz zählt ein Countdown von 2 Minuten herunter (`return.estimate-seconds`), bei Wartung bis zu ihrem Ende.
- **Verbindung zu MRadmin:** Mit MRadmin auf dem Hauptserver (Abschnitt `waiting-room` dort) kennt der Warteraum Grund, Countdown, Wartung und Warnungen und merkt sie sich, während der Hauptserver aus ist (`main.link-url`, `main.link-key`). Ohne MRadmin sieht er nur, ob der Hauptserver läuft (`main.address`).
- **Verwaltung aus MRadmin:** Der Warteraum meldet sich alle 30 s bei MRadmin. Dort steht er unter **Server → Plugins**, seine Konfiguration lässt sich im Browser bearbeiten (Sicherung `config.backup-remote.yml`), und bestellte Updates lädt er selbst - nur aus den offiziellen Releases, nur mit passender Prüfsumme - und spielt sie ein, sobald niemand wartet und der Hauptserver bereit ist (`updates.restart-when-idle`, braucht z.B. systemd mit `Restart=always`).
- **Mit dem Starter von MRadmin:** `mrserver setup --role waitroom` (oder der Installer der Website) richtet den Warteraum-Server ein; MRwaitroom spricht mit dem Starter (Herzschlag gegen Hänger, Neustart nach Absturz, Paper-Updates, `mrserver restart|stop|cancel`). Arbeitsspeicher, Java-Optionen, Paper-Updates und Hänger-Erkennung im neuen Abschnitt `server` (`config-version` 3).
- **Neustarts nur, wenn der Warteraum nicht gebraucht wird:** von Hand (MRadmin, Kachel **Warteraum-Server**, oder `mrserver restart`) und geplant (`server.restart-times`) - nie während einer Wartung, nie kurz vor oder während eines Neustarts des Hauptservers und erst, wenn niemand wartet. Ein gesperrter geplanter Neustart wartet, bis es passt.
- **Countdown vor der Rückkehr:** Ist der Hauptserver wieder bereit, läuft ein Countdown von 10 Sekunden (Bossbar „zurück in … s“, eine Chatzeile, Ticken in den letzten 5 Sekunden), dann geht es zurück (`return.delay-seconds`, alter Standard 5 wird auf 10 gesetzt, `config-version` 4).
- **Besuch aus dem Admin-Menü:** Wer über **In den Warteraum** im Admin-Menü von MRadmin kommt, wird nicht automatisch zurückgeschickt und hat das grüne Item **Zurück zum Server** (Permission `mrwaitroom.visit`, in `mrwaitroom.groups.admin`).
- **Lobby:** Eine neue Warteraum-Welt startet mit einer fertigen Lobby samt Spawn und Anzeigetafel (`room.lobby`, ohne Lobby leere Welt mit Plattform `room.platform`, `config-version` 6). Eigene Lobby: bauen, `/setworldspawn`, dann `/mrwaitroom lobby save` - gespeichert in `plugins/MRwaitroom/lobby/`, gilt dann für neue Welten (Permission `mrwaitroom.admin.lobby`).
- **Anzeigetafel:** Eine Wand aus 4 × 2 Rahmen zeigt Zustand, Grund, Countdown mit Balken, die Wartung und den Servernamen aus MRadmin - jedem Spieler in seiner Sprache. Aufhängen mit `/mrwaitroom screen set` (auf einen Rahmen schauen), abnehmen mit `screen remove`; die Rahmen sind gesperrt (Permission `mrwaitroom.admin.screen`, Texte `screen-…`).
- **Der Raum:** eigene Welt (`room.world`), jeder landet beim Betreten am Spawn (schon beim Einloggen, ohne Sprung), kein Schaden, kein Hunger, Inventar gesperrt, Chat aus (`room.chat`, Permission `mrwaitroom.chat`), andere Spieler ausblendbar (`room.hide-players`).
- **Xaero's Minimap / World Map:** eigene Karten-ID (`xaero.world-id`), damit Xaero den Warteraum nicht über die Karte des Hauptservers zeichnet.
- **Befehle:** `/main` (sofort zurück, nur Admins: `mrwaitroom.main` in `mrwaitroom.groups.admin` - Spieler gehen automatisch zurück, auch der Knopf im Uhr-Menü ist nur für Admins), `/mrwaitroom status|send|reload|version`. Permission-Gruppen `mrwaitroom.player` (hat jeder), `mrwaitroom.groups.support|staff|admin`, `mrwaitroom.stay` einzeln.
