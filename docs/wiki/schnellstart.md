# Schnellstart

Du brauchst: einen **Velocity**-Proxy, deinen **Hauptserver** und einen **zweiten Paper-Server** für den Warteraum (2 GB Arbeitsspeicher reichen).

1. **[MRwaitroom herunterladen](https://mrtechnology.marcelr.at/download/mrwaitroom/)** und die JAR auf dem **Warteraum-Server** nach `plugins/` legen. Server einmal starten.
2. **Velocity** (`velocity.toml`):
   ```toml
   bungee-plugin-message-channel = true

   [servers]
   main = "10.0.0.5:25565"
   waitroom = "10.0.0.6:25565"
   try = ["main", "waitroom"]
   ```
3. **Auf dem Hauptserver mit MRadmin** in der `config.yml` `waiting-room.enabled: true` und `waiting-room.link.bind` auf seine interne Adresse setzen, dann `/mradmin reload` - siehe [Warteraum in MRadmin](https://mrtechnology.marcelr.at/wiki/mradmin/warteraum/).
4. **Im Warteraum** in `plugins/MRwaitroom/config.yml`:
   ```yaml
   main:
     server: "main"                    # Name in der velocity.toml
     address: "10.0.0.5:25565"         # Hauptserver im internen Netz
     link-url: "http://10.0.0.5:8126"  # waiting-room.link von MRadmin
     link-key: "…"                     # waiting-room.link.key von MRadmin
   ```
   Dann `/mrwaitroom reload` und `/mrwaitroom status` - dort sollte „bereit“ und „MRadmin: verbunden“ stehen.

Fertig. Beim nächsten `/mradmin restart` wechseln alle in den Warteraum und kommen danach von selbst zurück.

> [!TIP]
> Velocity-Weiterleitung, LuckPerms und LibertyBans für beide Server: [Einrichten](einrichten.md).
