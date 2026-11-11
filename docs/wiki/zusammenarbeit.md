# Zusammenarbeit mit MRadmin

MRwaitroom läuft **allein**, die beiden Plugins kennen sich nicht: MRadmin liefert über eine kleine Schnittstelle (`waiting-room.link`) Infos im JSON-Format, MRwaitroom liest sie. Wie die Plugins allgemein zusammenarbeiten: [Zusammenarbeit](https://mrtechnology.marcelr.at/wiki/allgemein/zusammenarbeit/).

| Funktion | Ohne MRadmin | Mit MRadmin auf dem Hauptserver |
|---|---|---|
| Spieler vor dem Neustart in den Warteraum | – (sie fliegen; bei Abstürzen hilft Velocity) | ✅ kurz vor dem Herunterfahren |
| Bei Wartung in den Warteraum statt Kick | – | ✅ |
| Grund des Neustarts | – | ✅ |
| Restzeit | aus den gemessenen Ausfällen | ✅ aus den gemessenen Neustarts |
| Wartung mit Ende anzeigen, Bypass beachten | – | ✅ |
| Warnungen aus „Neuigkeiten“ | – | ✅ |
| „Bereit“ erst, wenn der Server fertig gestartet ist | sobald der Port antwortet | ✅ |

Einrichten auf der MRadmin-Seite: [Warteraum (Proxy)](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
