# Ablauf & Anzeige

## Was der Warteraum über den Hauptserver weiß
Alle paar Sekunden (`main.poll-seconds`) prüft der Warteraum, ob der Minecraft-Port des Hauptservers antwortet, und fragt MRadmin dort, was los ist. Die letzte Antwort von MRadmin hebt er auf (`plugins/MRwaitroom/state.yml`), damit Grund, Countdown und Wartungszeiten auch dann noch da sind, wenn der Hauptserver aus ist - auch nach einem Neustart des Warteraums selbst.

| Zustand | Wann | Zeile über der Hotbar |
|---|---|---|
| **Neustart** | MRadmin-Countdown ist abgelaufen, Server weg | „Neustart · noch ca. 0:40“, danach „Gleich wieder da · seit …“ |
| **Heruntergefahren** | `/mradmin stop` ist abgelaufen | „Server ist aus · seit …“ |
| **Nicht erreichbar** | Server weg ohne Ankündigung (Absturz, `/stop`) | „Server startet neu · seit …“ |
| **Startet** | Port antwortet, MRadmin meldet noch nicht „bereit“ | „Server startet · gleich geht es los“ |
| **Wartung** | MRadmin meldet eine laufende Wartung - auch wenn der Server währenddessen neu startet oder aus ist | „Wartung · noch ca. 1:20:00“ bzw. „Ende offen“ |
| **Bereit** | MRadmin meldet „bereit“ | „Zurück zum Server …“ |

Dazu die **Bossbar** (`display.bossbar`) mit Grund bzw. Wartungstitel und Fortschritt, und **beim Betreten** eine kurze Chatzeile mit Einzelheiten im Hover sowie die Warnungen, die in MRadmin unter **Neuigkeiten** gerade aktiv sind.

> [!NOTE]
Dazu die **Uhr in der Hotbar** (`display.item`, bleibt ausgewählt auf `display.slot`) mit dem Zustand im Namen, z.B. „⏳ Neustart“ oder „🛠 Wartung bis 18:00“.

> [!NOTE]
> Der Name der Uhr ändert sich nur, wenn sich der Zustand ändert - jede Änderung am Item in der Hand spielt im Spiel eine Animation ab. Der Countdown läuft deshalb in der Zeile darüber (Actionbar). Das Inventar ist gesperrt.

## Restzeit
Die Restzeit eines Neustarts schätzt der Warteraum aus der **typischen Dauer der letzten Neustarts** (Median der letzten 10, gemessen vom Herunterfahren bis „bereit“). Bis zwei Neustarts gemessen sind, gilt `return.estimate-seconds` (Standard 90 s). Stopps zählen nicht mit.

## Zurückschicken
- Sobald der Hauptserver **bereit** ist, wartet der Warteraum `return.delay-seconds` (Standard 5 s) und schickt dann alle **in Schüben** zurück: `return.batch-size` Spieler (5) alle `return.batch-seconds` (2 s), wer am längsten wartet zuerst.
- **Während einer Wartung** gehen nur Spieler mit dem Wartungs-Bypass von MRadmin zurück (`mradmin.maintenance.bypass`) - die anderen warten bis zum Ende.
- **Abgewiesen?** Kommt ein Spieler `return.max-attempts`-mal (3) nicht an (Bann, Whitelist), trennt ihn der Warteraum mit einem Hinweis. Auch wer kurz hintereinander zurückprallt, zählt weiter - niemand pendelt endlos. Während einer Wartung wird nicht getrennt: Der Spieler bleibt dann einfach bis zum Ende im Warteraum.
- Spieler mit `mrwaitroom.stay` werden nie automatisch zurückgeschickt.
- `/main` schickt einen Spieler sofort zurück, `/mrwaitroom send <spieler|all>` das Team.

## Der Raum
Eine leere Welt (`room.world`) mit einer kleinen Plattform am Spawn. Kein Schaden, kein Hunger, Abenteuermodus, Blöcke und Inventar gesperrt, wer runterfällt, steht wieder am Spawn. Der Chat ist aus (`room.chat`), mit `mrwaitroom.chat` darf man trotzdem schreiben. Andere Spieler lassen sich ausblenden (`room.hide-players`).
