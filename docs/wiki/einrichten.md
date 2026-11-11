# Einrichten (Velocity & Server)

So sieht der Aufbau aus:

```text
Spieler ──► Velocity (Proxy) ──► Hauptserver  (MRadmin, deine Plugins)
                          └────► Warteraum    (MRwaitroom)
```

Spieler verbinden sich immer mit dem Proxy. Läuft der Hauptserver nicht, landen sie im Warteraum; MRadmin schickt sie vor einem Neustart selbst hinüber, MRwaitroom schickt sie danach zurück.

## 1. Velocity
In der `velocity.toml`:

```toml
player-info-forwarding-mode = "modern"
bungee-plugin-message-channel = true
# Bricht die Verbindung zum Hauptserver unerwartet ab (Absturz), geht es in den Warteraum.
failover-on-unexpected-server-disconnect = true

[servers]
main = "10.0.0.5:25565"
waitroom = "10.0.0.6:25565"
# Erst der Hauptserver, dann der Warteraum.
try = ["main", "waitroom"]
```

- `bungee-plugin-message-channel = true` ist **Pflicht**: Darüber schicken MRadmin und MRwaitroom die Spieler zwischen den Servern hin und her.
- Die Namen (`main`, `waitroom`) müssen zu `waiting-room.server` in MRadmin und `main.server` in MRwaitroom passen.

## 2. Beide Paper-Server hinter den Proxy
Auf **beiden** Servern:
- `server.properties`: `online-mode=false` (Velocity prüft die Accounts).
- `config/paper-global.yml`: `proxies.velocity.enabled: true`, `online-mode: true` und dasselbe `secret` wie in der `forwarding.secret` von Velocity.
- Die Server nur im internen Netz erreichbar machen - von außen ist nur Velocity offen.

## 3. Der Warteraum-Server
- **Arbeitsspeicher:** 2 GB reichen gut.
- `server.properties`: kleine `view-distance` und `simulation-distance` (z.B. 4 und 2), `spawn-protection=0` und **`white-list=false`** - Minecraft 26 legt neue Server teils mit eingeschalteter Whitelist an, dann käme niemand in den Warteraum.
- MRwaitroom legt die Welt `waitroom` (leer, mit Plattform) selbst an und schickt jeden Spieler dorthin.
- Damit der Warteraum nach einem Absturz oder Update von selbst wieder hochkommt, als Dienst starten (z.B. systemd mit `Restart=always`).

## 4. Verbindung zu MRadmin
Auf dem Hauptserver in MRadmins `config.yml` den Abschnitt `waiting-room` einschalten ([Anleitung](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/)). MRadmin erzeugt einen Schlüssel. Im Warteraum dann:

```yaml
main:
  server: "main"
  address: "10.0.0.5:25565"
  link-url: "http://10.0.0.5:8126"
  link-key: "<waiting-room.link.key aus MRadmin>"
```

`/mrwaitroom status` zeigt, ob alles passt: Hauptserver „bereit“, Port „erreichbar“, MRadmin „verbunden“.

> [!TIP]
> Ohne MRadmin geht es auch: `link-url` leer lassen. Der Warteraum sieht dann nur, ob der Hauptserver läuft, und zeigt „Server startet neu · seit …“ ohne Grund und ohne Wartungszeiten.

## 5. Rechte und Sperren auf beiden Servern
Damit sich der Warteraum wie der Hauptserver verhält, sollten **LuckPerms** und **LibertyBans** auf beiden Servern dieselbe Datenbank nutzen (z.B. eine MariaDB auf dem Proxy-Rechner):
- **LuckPerms:** `storage-method: mariadb` (bzw. `mysql`) mit denselben Zugangsdaten und `messaging-service: sql`, damit Änderungen sofort auf beiden Servern gelten. Bestehende Daten: vorher `/lp export`, danach `/lp import`.
- **LibertyBans:** auf beiden Servern mit derselben Datenbank. Dann kommen Gebannte gar nicht erst in den Warteraum.
- Der Warteraum prüft vor dem Zurückschicken während einer Wartung den Wartungs-Bypass (`mradmin.maintenance.bypass`) - mit gemeinsamem LuckPerms gilt dort dasselbe wie auf dem Hauptserver.

> [!NOTE]
> Eine eigene Whitelist braucht der Warteraum nicht: Wer vom Hauptserver mehrmals abgewiesen wird, wird getrennt (`return.max-attempts`).

## 6. Xaero's Minimap / World Map
Velocity zeigt beide Server unter derselben Adresse. MRwaitroom schickt Xaero deshalb eine eigene Karten-ID (`xaero.world-id`, beim ersten Start erzeugt) - so landet die leere Warteraum-Welt nicht auf der Karte des Hauptservers.
