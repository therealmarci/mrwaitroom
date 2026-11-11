# MRwaitroom

**Kein Rauswurf mehr bei Neustarts und Wartungen:** Hinter einem Velocity-Proxy warten deine Spieler in einem kleinen Warteraum, sehen Grund und Restzeit und kommen automatisch zurück, sobald der Hauptserver wieder bereit ist.

**[⬇ Download](https://mrtechnology.marcelr.at/download/mrwaitroom/)** · **[📖 Wiki](https://mrtechnology.marcelr.at/wiki/mrwaitroom/)** · **[🐞 Fehler melden](https://mrtechnology.marcelr.at/report/mrwaitroom/)**

| Auf einen Blick | |
|---|---|
| Server | Paper 26.3 (oder ein Fork wie Purpur), Java 25 - als zweiter Server hinter Velocity |
| Benötigt | Velocity; empfohlen: MRadmin auf dem Hauptserver |
| Sprachen | Deutsch, Englisch - jeder Spieler automatisch in der Sprache seines Minecraft-Clients |
| Status | Beta (0.x) |

## Was kann MRwaitroom?
- **Warteraum** statt Kick: leere Welt mit Plattform, kein Schaden, kein Hunger.
- **Restzeit im Blick:** Bossbar mit Countdown und Grund, die Uhr in der Hand öffnet ein Menü mit allen Einzelheiten.
- **Automatisch zurück** in kleinen Schüben, sobald der Hauptserver bereit ist - bei Wartung nur mit Bypass.
- **Mit MRadmin:** Spieler wechseln vor dem Neustart selbst hinüber, Grund, Countdown, Wartung und Warnungen inklusive.
- **Xaero-freundlich:** eigene Karten-ID, die Karte des Hauptservers bleibt sauber.

## Schnellstart
1. **[MRwaitroom herunterladen](https://mrtechnology.marcelr.at/download/mrwaitroom/)** und auf dem Warteraum-Server nach `plugins/` legen.
2. In der `velocity.toml` `bungee-plugin-message-channel = true` und `try = ["main", "waitroom"]` setzen.
3. In MRadmin `waiting-room.enabled: true`, Adresse und Schlüssel in `plugins/MRwaitroom/config.yml` (`main.link-url`, `main.link-key`) übernehmen.
4. `/mrwaitroom status` - fertig, wenn dort „bereit“ steht.

**Update:** alte JAR durch die neue ersetzen, Server neu starten - die Config passt sich selbst an.

## Ganze Anleitung im Wiki
| Für Spieler | Für Admins |
|---|---|
| [Im Warteraum](https://mrtechnology.marcelr.at/wiki/mrwaitroom/warten/) | [Einrichten](https://mrtechnology.marcelr.at/wiki/mrwaitroom/einrichten/) · [Ablauf & Anzeige](https://mrtechnology.marcelr.at/wiki/mrwaitroom/ablauf/) · [Befehle](https://mrtechnology.marcelr.at/wiki/mrwaitroom/befehle/) · [Permissions](https://mrtechnology.marcelr.at/wiki/mrwaitroom/permissions/) · [Konfiguration](https://mrtechnology.marcelr.at/wiki/mrwaitroom/konfiguration/) · [Häufige Fragen](https://mrtechnology.marcelr.at/wiki/mrwaitroom/faq/) |

Die Seiten liegen als Markdown in [`docs/wiki/`](docs/wiki/) (Seitenbaum: [`docs/wiki/index.yml`](docs/wiki/index.yml)).

## Lizenz
MRwaitroom steht unter der **[MRtechnology Lizenz](LICENSE)** (source-available, nicht Open Source). Kurz gesagt:

| ✅ Erlaubt | ❌ Nicht erlaubt |
|---|---|
| Nutzen - privat und gewerblich, auch auf Servern mit Spenden, Rängen oder Shops | Weiterveröffentlichen (auch verändert), z.B. auf Modrinth, Spigot, in Modpacks |
| Verändern und neu kompilieren | Verkaufen oder gegen Geld anbieten |
| An dein Team oder deinen Hoster weitergeben | Als eigenes Werk ausgeben, Lizenzhinweis entfernen |
| Verbesserungen per Pull Request einreichen | |

Die Nutzung erfolgt auf eigenes Risiko - leg vor Updates eine Sicherung an. Maßgeblich ist der vollständige Text in [LICENSE](LICENSE).

## Für Entwickler
Selbst bauen mit Java 25: `gradlew build` → `build/libs/MRwaitroom-mc<minecraft>-<version>.jar`. Versionen heißen `mc<minecraft>-BET-<version>-bu<build>` (z.B. `mc26.3-BET-0.1.0-bu001`). Aufbau des Codes: [docs/DOKUMENTATION.md](docs/DOKUMENTATION.md), Branches und Releases: [docs/RELEASES.md](docs/RELEASES.md).
