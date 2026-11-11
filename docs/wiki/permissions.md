# Permissions

Wie die Gruppen in allen Plugins aufgebaut sind: [Rechte mit LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Gruppen
Jede Gruppe enthält die darunter, die Team-Gruppen auch `mrwaitroom.player`. Anders als bei den anderen Plugins hat **jeder** `mrwaitroom.player` schon von Haus aus - wer im Warteraum landet, ist oft neu und hat noch keine Gruppe.

<!-- auto:permission-groups -->

```text
/lp group supporter permission set mrwaitroom.groups.support true
/lp group staff permission set mrwaitroom.groups.staff true
/lp group admin permission set mrwaitroom.groups.admin true
```

## Einzeln
„Enthalten in“ nennt die kleinste Gruppe, die die Permission mitbringt; „–“ heißt: in keiner Gruppe, nur einzeln vergeben.

<!-- auto:permissions -->
