# Ablauf & Anzeige

## Was der Warteraum über den Hauptserver weiß
Alle paar Sekunden (`main.poll-seconds`) prüft der Warteraum, ob der Minecraft-Port des Hauptservers antwortet, und fragt MRadmin dort, was los ist. Die letzte Antwort von MRadmin hebt er auf (`plugins/MRwaitroom/state.yml`), damit Grund, Countdown und Wartungszeiten auch dann noch da sind, wenn der Hauptserver aus ist - auch nach einem Neustart des Warteraums selbst.

| Zustand | Wann | Bossbar (oben, jede Sekunde neu) |
|---|---|---|
| **Neustart** | MRadmin-Countdown ist abgelaufen, Server weg | „Neustart | 1:42“ (Countdown ab 2:00), danach „Neustart | gleich wieder da“ |
| **Heruntergefahren** | `/mradmin stop` ist abgelaufen | „Server aus | Ende offen“ |
| **Nicht erreichbar** | Server weg ohne Ankündigung (Absturz, `/stop`) | „Server startet neu | 1:35“ (Countdown ab 2:00) |
| **Startet** | Port antwortet, MRadmin meldet noch nicht „bereit“ | „Server startet | gleich geht es los“ |
| **Wartung** | MRadmin meldet eine laufende Wartung - auch wenn der Server währenddessen neu startet oder aus ist | „Wartung | 1:20:00“ (Countdown bis zum Ende) bzw. „Wartung | Ende offen“ |
| **Bereit** | MRadmin meldet „bereit“ | „Server bereit | 0:08“ |

Die Bossbar (`display.bossbar`) ist immer gleich aufgebaut - **Zustand | Countdown** -, Grund und Einzelheiten stehen im Uhr-Menü. Sie füllt sich bis zur erwarteten Rückkehr bzw. bis zum Ende der Wartung. **Beim Betreten** kommt eine kurze Chatzeile mit Einzelheiten im Hover, dazu die Warnungen, die in MRadmin unter **Neuigkeiten** gerade aktiv sind.

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

## Countdown bei Neustarts
Bei einem Neustart oder Absturz zählt die Bossbar von `return.estimate-seconds` herunter (Standard **2:00**). Ist der Hauptserver früher zurück, geht es früher los; dauert es länger, steht dort „gleich wieder da“.

## Zurückschicken
- Sobald der Hauptserver **bereit** ist, läuft ein **Countdown** von `return.delay-seconds` (Standard 10 s): Bossbar „Server bereit | 0:08“, eine Chatzeile und ein Ticken in den letzten 5 Sekunden. Danach schickt der Warteraum alle **in Schüben** zurück: `return.batch-size` Spieler (5) alle `return.batch-seconds` (2 s), wer am längsten wartet zuerst.
- **Während einer Wartung** gehen nur Team-Mitglieder mit dem Recht „Bei Wartung auf den Server“ zurück (siehe [Rechte](#rechte-und-admin-modus)) - die anderen warten bis zum Ende.
- **Abgewiesen?** Kommt ein Spieler `return.max-attempts`-mal (3) nicht an (Bann, Whitelist), trennt ihn der Warteraum mit einem Hinweis. Auch wer kurz hintereinander zurückprallt, zählt weiter - niemand pendelt endlos. Während einer Wartung wird nicht getrennt: Der Spieler bleibt dann einfach bis zum Ende im Warteraum.
- Wer das Recht „Im Warteraum bleiben“ hat, wird nie automatisch zurückgeschickt.
- Von Hand zurück nur fürs Team: mit dem grünen Item **Zurück zum Server** (Hotbar rechts) oder dem Knopf im Uhr-Menü - wer „Im Warteraum bleiben“ oder „Bei Wartung auf den Server“ darf -, Admins mit `/main` (`mrwaitroom.main`), Staff mit `/mrwaitroom send <spieler|all>`. Spieler können das nicht selbst anstoßen.

## Rechte und Admin-Modus
Was das Team hier darf, kommt aus der Tabelle **Rechte pro Rolle** in MRadmin (`waiting-room.rights`, [Anleitung](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/#rechte-im-warteraum)) - pro Rolle Admin, Staff, Supporter je **Nein / Ja / Nur im Admin-Modus**. Der Warteraum merkt sich die Tabelle, sie gilt also auch, solange der Hauptserver aus ist. Die Rolle kommt aus `mradmin.groups.admin|staff|support` (mit LuckPerms auf einer gemeinsamen Datenbank dieselbe wie auf dem Hauptserver). Standard: Admins nur im Admin-Modus, Staff und Supporter nichts - ein Admin ohne Admin-Modus wartet wie alle.

**Die Hotbar** ist symmetrisch, Plätze ohne Recht bleiben leer:

| Platz | Item | Wer |
|---|---|---|
| links (1) | **Admin-Modus** an/aus (Rechtsklick) | Recht „Admin-Modus im Warteraum“ |
| Mitte (`display.slot`, 5) | **Uhr** mit Zustand und Menü | alle |
| rechts (9) | **Zurück zum Server** | Recht „Im Warteraum bleiben“ oder „Bei Wartung auf den Server“ |

**Im Admin-Modus** bist du im Kreativmodus, kannst fliegen (z.B. an der Lobby bauen) und bist für Wartende unsichtbar; du siehst alle. Der Admin-Modus gilt im **ganzen Netzwerk**: Hier eingeschaltet, kommst du auf dem Hauptserver im Admin-Modus an; dort ausgeschaltet, bekommst du dein Inventar zurück. Wer auf dem Hauptserver im Admin-Modus ist, ist es auch hier.

## Der Raum
Eine eigene Welt (`room.world`) mit der [Lobby](lobby.md) und der [Anzeigetafel](lobby.md#die-anzeigetafel). **Jeder landet beim Betreten am Spawn** - egal, wo er beim letzten Mal stand. Kein Schaden, kein Hunger, Abenteuermodus, Blöcke und Inventar gesperrt, wer runterfällt, steht wieder am Spawn. Der Chat ist aus (`room.chat`), mit `mrwaitroom.chat` darf man trotzdem schreiben. Andere Spieler lassen sich ausblenden (`room.hide-players`).

## Neustarts des Warteraums
Mit dem Starter von MRadmin (`mrserver setup --role waitroom`) startet der Warteraum neu:

- **von Hand:** in MRadmin auf **Server → Übersicht** (Kachel **Warteraum-Server**, **Neu starten**) oder mit `mrserver restart` in der Konsole des Rechners,
- **geplant:** jeden Tag zu den Zeiten in `server.restart-times`.

Aber nur, wenn er nicht gebraucht wird: Der Hauptserver ist bereit, **keine Wartung** läuft, **kein Neustart des Hauptservers** kommt in den nächsten 15 Minuten, und **niemand wartet**. Sonst lehnt er einen Neustart von Hand mit dem Grund ab (wartende Spieler ausgenommen - dann startet er, sobald sie zurück sind), und ein geplanter Neustart wartet, bis alles passt. `mrserver stop` hält ihn ohne diese Prüfung an, `mrserver cancel` bricht einen wartenden Neustart ab.
