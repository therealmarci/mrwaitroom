# Permissions

How the groups are built in all plugins: [Permissions with LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Groups
Every group contains the one below, the team groups also `mrwaitroom.player`. Unlike the other plugins **everyone** has `mrwaitroom.player` by default - whoever lands in the waiting room is often new and has no group yet.

<!-- auto:permission-groups -->

```text
/lp group supporter permission set mrwaitroom.groups.support true
/lp group staff permission set mrwaitroom.groups.staff true
/lp group admin permission set mrwaitroom.groups.admin true
```

## Single ones
"Contained in" names the smallest group that brings the permission; "–" means: in no group, only given on its own.

<!-- auto:permissions -->
