# Ablauf & Anzeige

## Was der Warteraum über den Hauptserver weiß
Alle paar Sekunden (`main.poll-seconds`) prüft der Warteraum, ob der Minecraft-Port des Hauptservers antwortet, und fragt MRadmin dort, was los ist. Die letzte Antwort von MRadmin hebt er auf (`plugins/MRwaitroom/state.yml`), damit Grund, Countdown und Wartungszeiten auch dann noch da sind, wenn der Hauptserver aus ist - auch nach einem Neustart des Warteraums selbst.

| Zustand | Wann | Bossbar (oben, jede Sekunde neu) |
|---|---|---|
| **Neustart** | MRadmin-Countdown ist abgelaufen, Server weg | „Neustart | noch ca. 0:40“, danach „Neustart | gleich wieder da“ |
| **Heruntergefahren** | `/mradmin stop` ist abgelaufen | „Server aus | seit 2:13“ |
| **Nicht erreichbar** | Server weg ohne Ankündigung (Absturz, `/stop`) | „Server startet neu | seit 0:25“ |
| **Startet** | Port antwortet, MRadmin meldet noch nicht „bereit“ | „Server startet | gleich geht es los“ |
| **Wartung** | MRadmin meldet eine laufende Wartung - auch wenn der Server währenddessen neu startet oder aus ist | „Wartung | bis 18:00“ bzw. „Wartung | Ende offen“ |
| **Bereit** | MRadmin meldet „bereit“ | „Server bereit | zurück in 8 s“ |

Die Bossbar (`display.bossbar`) ist immer gleich aufgebaut - **Zustand | Zeit** -, Grund und Einzelheiten stehen im Uhr-Menü. Sie füllt sich bis zur erwarteten Rückkehr bzw. bis zum Ende der Wartung. **Beim Betreten** kommt eine kurze Chatzeile mit Einzelheiten im Hover, dazu die Warnungen, die in MRadmin unter **Neuigkeiten** gerade aktiv sind.

## Die Uhr und ihr Menü
Die **Uhr in der Hotbar** (`display.item`, bleibt ausgewählt auf `display.slot`) trägt den Zustand im Namen, z.B. „⏳ Neustart“ oder „🛠 Wartung bis 18:00“. **Rechtsklick** öffnet ein Menü mit allen Einzelheiten:

| Feld | Inhalt |
|---|---|
| Zustand (oben) | wie die Bossbar |
| Grund | Grund des Neustarts bzw. Stopps |
| Zeit | weg seit, noch ca., wie lange ein Neustart meist dauert |
| Wartung | Titel, Beginn und Ende - laufend oder geplant |
| Warnungen | die aktiven Warnungen aus MRadmin |
| Zurück zum Server | nur grün, wenn der Server bereit ist (sonst grau) |

Das Menü aktualisiert sich jede Sekunde, solange es offen ist.

> [!NOTE]
> Der Name der Uhr ändert sich nur, wenn sich der Zustand ändert - jede Änderung am Item in der Hand spielt im Spiel eine Animation ab. Der Countdown läuft deshalb in der Bossbar. Das Inventar ist gesperrt.

## Restzeit
Die Restzeit eines Neustarts schätzt der Warteraum aus der **typischen Dauer der letzten Neustarts** (Median der letzten 10, gemessen vom Herunterfahren bis „bereit“). Bis zwei Neustarts gemessen sind, gilt `return.estimate-seconds` (Standard 90 s). Stopps zählen nicht mit.

## Zurückschicken
- Sobald der Hauptserver **bereit** ist, läuft ein **Countdown** von `return.delay-seconds` (Standard 10 s): Bossbar „Server bereit | zurück in 8 s“, eine Chatzeile und ein Ticken in den letzten 5 Sekunden. Danach schickt der Warteraum alle **in Schüben** zurück: `return.batch-size` Spieler (5) alle `return.batch-seconds` (2 s), wer am längsten wartet zuerst.
- **Während einer Wartung** gehen nur Spieler mit dem Wartungs-Bypass von MRadmin zurück (`mradmin.maintenance.bypass`) - die anderen warten bis zum Ende.
- **Abgewiesen?** Kommt ein Spieler `return.max-attempts`-mal (3) nicht an (Bann, Whitelist), trennt ihn der Warteraum mit einem Hinweis. Auch wer kurz hintereinander zurückprallt, zählt weiter - niemand pendelt endlos. Während einer Wartung wird nicht getrennt: Der Spieler bleibt dann einfach bis zum Ende im Warteraum.
- Spieler mit `mrwaitroom.stay` werden nie automatisch zurückgeschickt.
- **Besuch:** Wer über **In den Warteraum** im Admin-Menü von MRadmin kommt (Permission `mrwaitroom.visit`), bleibt, bis er selbst geht - mit dem grünen Item **Zurück zum Server** in der Hotbar (Platz 9), auch während einer Wartung.
- Von Hand zurück nur fürs Team: Admins mit `/main`, dem grünen Item oder dem Knopf im Uhr-Menü (`mrwaitroom.main`, `mrwaitroom.visit`), Staff mit `/mrwaitroom send <spieler|all>`. Spieler können das nicht selbst anstoßen.

## Der Raum
Eine leere Welt (`room.world`) mit einer kleinen Plattform am Spawn. Kein Schaden, kein Hunger, Abenteuermodus, Blöcke und Inventar gesperrt, wer runterfällt, steht wieder am Spawn. Der Chat ist aus (`room.chat`), mit `mrwaitroom.chat` darf man trotzdem schreiben. Andere Spieler lassen sich ausblenden (`room.hide-players`).

## Neustarts des Warteraums
Mit dem Starter von MRadmin (`mrserver setup --role waitroom`) startet der Warteraum neu:

- **von Hand:** in MRadmin auf **Server → Übersicht** (Kachel **Warteraum-Server**, **Neu starten**) oder mit `mrserver restart` in der Konsole des Rechners,
- **geplant:** jeden Tag zu den Zeiten in `server.restart-times`.

Aber nur, wenn er nicht gebraucht wird: Der Hauptserver ist bereit, **keine Wartung** läuft, **kein Neustart des Hauptservers** kommt in den nächsten 15 Minuten, und **niemand wartet**. Sonst lehnt er einen Neustart von Hand mit dem Grund ab (wartende Spieler ausgenommen - dann startet er, sobald sie zurück sind), und ein geplanter Neustart wartet, bis alles passt. `mrserver stop` hält ihn ohne diese Prüfung an, `mrserver cancel` bricht einen wartenden Neustart ab.
