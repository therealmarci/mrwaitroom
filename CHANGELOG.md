# Changelog

Alle wichtigen Änderungen an MRwaitroom. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
veröffentlicht als `mc<minecraft>-STA|BET-<version>-bu<build>` (siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Beta:** Alle 0.x-Versionen sind Testversionen. Bis 1.0.0 können sich Config, Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Warteraum hinter einem Velocity-Proxy:** Während der Hauptserver neu startet, gestoppt ist oder in Wartung ist, warten die Spieler hier und kommen automatisch zurück, sobald er bereit ist - in Schüben (`return.batch-size`, `return.batch-seconds`), während einer Wartung nur mit Wartungs-Bypass. Wer mehrmals abgewiesen wird (Bann, Whitelist), wird getrennt (`return.max-attempts`).
- **Restzeit im Blick:** Uhr in der Hotbar mit dem Zustand („⏳ Neustart“, „🛠 Wartung bis 18:00“, `display.item`, `display.slot`), Rechtsklick öffnet ein Menü mit Grund, Zeiten, Wartung und Warnungen; Bossbar mit Countdown, Grund und Fortschritt (`display.bossbar`, „Neustart · noch ca. 0:40“), beim Betreten eine Chatzeile mit Einzelheiten im Hover und den aktiven Warnungen. Bei Neustart und Absturz zählt ein Countdown von 2 Minuten herunter (`return.estimate-seconds`), bei Wartung bis zu ihrem Ende.
- **Verbindung zu MRadmin:** Mit MRadmin auf dem Hauptserver (Abschnitt `waiting-room` dort) kennt der Warteraum Grund, Countdown, Wartung und Warnungen und merkt sie sich, während der Hauptserver aus ist (`main.link-url`, `main.link-key`). Ohne MRadmin sieht er nur, ob der Hauptserver läuft (`main.address`).
- **Verwaltung aus MRadmin:** Der Warteraum meldet sich alle 30 s bei MRadmin. Dort steht er unter **Server → Plugins**, seine Konfiguration lässt sich im Browser bearbeiten (Sicherung `config.backup-remote.yml`), und bestellte Updates lädt er selbst - nur aus den offiziellen Releases, nur mit passender Prüfsumme - und spielt sie ein, sobald niemand wartet und der Hauptserver bereit ist (`updates.restart-when-idle`, braucht z.B. systemd mit `Restart=always`).
- **Mit dem Starter von MRadmin:** `mrserver setup --role waitroom` (oder der Installer der Website) richtet den Warteraum-Server ein; MRwaitroom spricht mit dem Starter (Herzschlag gegen Hänger, Neustart nach Absturz, Paper-Updates, `mrserver restart|stop|cancel`). Arbeitsspeicher, Java-Optionen, Paper-Updates und Hänger-Erkennung im neuen Abschnitt `server` (`config-version` 3).
- **Neustarts nur, wenn der Warteraum nicht gebraucht wird:** von Hand (MRadmin, Kachel **Warteraum-Server**, oder `mrserver restart`) und geplant (`server.restart-times`) - nie während einer Wartung, nie kurz vor oder während eines Neustarts des Hauptservers und erst, wenn niemand wartet. Ein gesperrter geplanter Neustart wartet, bis es passt.
- **Countdown vor der Rückkehr:** Ist der Hauptserver wieder bereit, läuft ein Countdown von 10 Sekunden (Bossbar „zurück in … s“, eine Chatzeile, Ticken in den letzten 5 Sekunden), dann geht es zurück (`return.delay-seconds`, alter Standard 5 wird auf 10 gesetzt, `config-version` 4).
- **Besuch aus dem Admin-Menü:** Wer über **In den Warteraum** im Admin-Menü von MRadmin kommt, wird nicht automatisch zurückgeschickt und hat das grüne Item **Zurück zum Server** (Permission `mrwaitroom.visit`, in `mrwaitroom.groups.admin`).
- **Der Raum:** leere Welt mit Plattform (`room.world`, `room.platform`), kein Schaden, kein Hunger, Inventar gesperrt, Chat aus (`room.chat`, Permission `mrwaitroom.chat`), andere Spieler ausblendbar (`room.hide-players`).
- **Xaero's Minimap / World Map:** eigene Karten-ID (`xaero.world-id`), damit Xaero den Warteraum nicht über die Karte des Hauptservers zeichnet.
- **Befehle:** `/main` (sofort zurück, nur Admins: `mrwaitroom.main` in `mrwaitroom.groups.admin` - Spieler gehen automatisch zurück, auch der Knopf im Uhr-Menü ist nur für Admins), `/mrwaitroom status|send|reload|version`. Permission-Gruppen `mrwaitroom.player` (hat jeder), `mrwaitroom.groups.support|staff|admin`, `mrwaitroom.stay` einzeln.
