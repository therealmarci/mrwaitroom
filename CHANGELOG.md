# Changelog

Alle wichtigen Änderungen an MRwaitroom. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
veröffentlicht als `mc<minecraft>-STA|BET-<version>-bu<build>` (siehe [docs/RELEASES.md](docs/RELEASES.md)).

> **Beta:** Alle 0.x-Versionen sind Testversionen. Bis 1.0.0 können sich Config, Befehle und Permissions noch ändern.

## [Unveröffentlicht]

### Neu
- **Warteraum hinter einem Velocity-Proxy:** Während der Hauptserver neu startet, gestoppt ist oder in Wartung ist, warten die Spieler hier und kommen automatisch zurück, sobald er bereit ist - in Schüben (`return.batch-size`, `return.batch-seconds`), während einer Wartung nur mit Wartungs-Bypass. Wer mehrmals abgewiesen wird (Bann, Whitelist), wird getrennt (`return.max-attempts`).
- **Restzeit im Blick:** Uhr in der Hotbar mit dem Zustand („⏳ Neustart“, „🛠 Wartung bis 18:00“, `display.item`, `display.slot`), Rechtsklick öffnet ein Menü mit Grund, Zeiten, Wartung und Warnungen; Bossbar mit Countdown, Grund und Fortschritt (`display.bossbar`, „Neustart · noch ca. 0:40“), beim Betreten eine Chatzeile mit Einzelheiten im Hover und den aktiven Warnungen. Die Restzeit kommt aus der typischen Dauer der letzten Neustarts (`return.estimate-seconds` bis genug gemessen ist).
- **Verbindung zu MRadmin:** Mit MRadmin auf dem Hauptserver (Abschnitt `waiting-room` dort) kennt der Warteraum Grund, Countdown, Wartung und Warnungen und merkt sie sich, während der Hauptserver aus ist (`main.link-url`, `main.link-key`). Ohne MRadmin sieht er nur, ob der Hauptserver läuft (`main.address`).
- **Der Raum:** leere Welt mit Plattform (`room.world`, `room.platform`), kein Schaden, kein Hunger, Inventar gesperrt, Chat aus (`room.chat`, Permission `mrwaitroom.chat`), andere Spieler ausblendbar (`room.hide-players`).
- **Xaero's Minimap / World Map:** eigene Karten-ID (`xaero.world-id`), damit Xaero den Warteraum nicht über die Karte des Hauptservers zeichnet.
- **Befehle:** `/main` (sofort zurück, `mrwaitroom.main`), `/mrwaitroom status|send|reload|version`. Permission-Gruppen `mrwaitroom.player` (hat jeder), `mrwaitroom.groups.support|staff|admin`, `mrwaitroom.stay` einzeln.
