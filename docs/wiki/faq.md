# Häufige Fragen

**`/mrwaitroom status` zeigt „MRadmin: keine Antwort (wrong key)“.**
`main.link-key` passt nicht zu `waiting-room.link.key` in MRadmins `config.yml`. Wert kopieren, `/mrwaitroom reload`.

**„MRadmin: keine Antwort (HTTP 429)“?**
Zu viele falsche Schlüssel in kurzer Zeit - MRadmin bremst dann eine Minute lang. Schlüssel korrigieren und kurz warten.

**„MRadmin: keine Antwort (ConnectException)“, obwohl der Hauptserver läuft?**
`waiting-room.link.bind` in MRadmin muss eine Adresse sein, die der Warteraum erreicht (nicht `127.0.0.1`, wenn die Server auf verschiedenen Rechnern laufen). Firewall zwischen den Rechnern für den Port (Standard 8126) öffnen.

**Spieler landen bei einem Neustart nicht im Warteraum, sondern fliegen.**
In der `velocity.toml` muss `bungee-plugin-message-channel = true` stehen, und der Name in `waiting-room.server` (MRadmin) muss zum Eintrag unter `[servers]` passen. Mit `/stop` in der Konsole wirft Paper alle raus - nimm `/mradmin restart` bzw. `/mradmin stop`.

**Spieler kommen nicht zurück.**
`main.server` muss der Name des Hauptservers in der `velocity.toml` sein. `/mrwaitroom status` zeigt, ob der Hauptserver „bereit“ ist. Während einer Wartung gehen nur Spieler mit Wartungs-Bypass zurück.

**Der Countdown passt nicht zu meinem Server.**
Er zählt bei Neustarts von `return.estimate-seconds` herunter (Standard 2 Minuten). Startet dein Server meist länger, den Wert erhöhen - früher zurück geht es ohnehin, sobald er bereit ist.

**Braucht der Warteraum eigene Plugins wie LuckPerms?**
Empfohlen: LuckPerms und LibertyBans mit derselben Datenbank wie der Hauptserver - siehe [Einrichten](einrichten.md#5-rechte-und-sperren-auf-beiden-servern).
