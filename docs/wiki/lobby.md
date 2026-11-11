# Lobby & Anzeigetafel

## Die Lobby

Legt MRwaitroom die Warteraum-Welt neu an (`room.world`), baut es die **Lobby** hinein - mit Spawn, Blickrichtung und Anzeigetafel. Mitgeliefert ist eine fertige Lobby; du kannst sie durch deine eigene ersetzen.

> [!NOTE]
> Die Lobby wird nur in eine **neue** Welt gebaut. Eine bestehende Warteraum-Welt bleibt, wie sie ist. Ohne Lobby (`room.lobby: false`) entsteht eine leere Welt mit kleiner Plattform (`room.platform`).

### Eigene Lobby

1. Bau deine Lobby in der Warteraum-Welt (z.B. per Schematic mit Litematica oder WorldEdit, im Kreativmodus).
2. Stell dich an den Spawn, schau in die gewünschte Richtung und setz ihn: `/setworldspawn ~ ~ ~ ~ 0`.
3. Optional: Anzeigetafel aufhängen (siehe unten).
4. `/mrwaitroom lobby save` - MRwaitroom findet den Bau selbst (alle Blöcke in den geladenen Chunks um dich herum). Ist die Lobby größer als deine Sichtweite, gib den Bereich an: `/mrwaitroom lobby save x1 y1 z1 x2 y2 z2`.

Gespeichert wird in `plugins/MRwaitroom/lobby/` (`lobby.nbt` = Blöcke und Rahmen, `lobby.yml` = Lage, Spawn, Anzeigetafel). Diese Lobby gilt dann statt der mitgelieferten - praktisch, wenn du den Warteraum-Server neu aufsetzt. Ordner löschen = wieder die mitgelieferte.

## Die Anzeigetafel

Eine Wand aus **4 × 2 Rahmen** zeigt den Wartenden, was los ist - jeder sieht sie in seiner Sprache:

- links groß der Zustand (Neustart, Wartung, Server aus, bereit …) in seiner Farbe, darunter Text und Grund bzw. Ende der Wartung,
- rechts der Countdown mit Balken,
- unten die nächste bzw. laufende Wartung und der **Name deines Servers** (aus MRadmin, Seite Aussehen).

### Aufhängen

1. Häng 8 Rahmen an eine Wand: 4 nebeneinander, 2 übereinander, alle in dieselbe Richtung (leer).
2. Schau auf einen der Rahmen: `/mrwaitroom screen set`.

MRwaitroom legt die Karten selbst hinein und sperrt die Rahmen (nicht drehen, nicht herausnehmen, nicht zerstören). Abnehmen: `/mrwaitroom screen remove`. Gibt es die Tafel schon, zieht `screen set` sie an die neue Wand um.

> [!TIP]
> Die Texte stehen in den Sprachdateien unter `screen-…` - pro Zustand fünf Zeilen (Überschrift, Text, Einzelheit, Countdown, Beschriftung). Die Tafel zeichnet nur, wenn sich etwas ändert, und nur für Spieler, die sie sehen.
