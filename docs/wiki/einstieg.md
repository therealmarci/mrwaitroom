# MRwaitroom

**Kein Rauswurf mehr bei Neustarts und Wartungen:** Hinter einem Proxy (Velocity) warten deine Spieler in einem kleinen Warteraum, sehen Grund und Restzeit und kommen automatisch zurück, sobald der Hauptserver wieder bereit ist.

## Was kann MRwaitroom?
- **Warteraum** auf einem zweiten, kleinen Paper-Server: leere Welt mit Plattform, kein Schaden, kein Hunger, Chat aus.
- **Restzeit im Blick:** Über der Hotbar steht dauerhaft, wie lange es noch dauert („Neustart · noch ca. 0:40“, „Wartung · bis 18:00“), dazu eine Bossbar mit Grund und Fortschritt.
- **Automatisch zurück:** Sobald der Hauptserver bereit ist, schickt der Warteraum alle in kleinen Schüben zurück. Bei Wartungen nur, wer den Wartungs-Bypass hat.
- **Lernt mit:** Die Restzeit schätzt er aus der Dauer der letzten Neustarts.
- **Mit MRadmin noch besser:** MRadmin schickt die Spieler vor dem Neustart rüber, statt dass sie fliegen, und meldet Grund, Countdown, Wartung und Warnungen. Ohne MRadmin sieht der Warteraum nur, ob der Hauptserver läuft.
- **Xaero-freundlich:** eigene Karten-ID, damit Xaero's Minimap die leere Warteraum-Welt nicht über die Karte des Hauptservers zeichnet.

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 - als zweiter Server hinter **Velocity** |
| Benötigt | Velocity; empfohlen: MRadmin auf dem Hauptserver, LuckPerms und LibertyBans mit gemeinsamer Datenbank |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | Beta (0.x) |

Weiter mit dem [Schnellstart](schnellstart.md) oder der ausführlichen [Einrichtung](einrichten.md).
