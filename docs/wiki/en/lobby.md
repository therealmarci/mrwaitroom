# Lobby & board

## The lobby

When MRwaitroom creates the waiting room world (`room.world`), it builds the **lobby** into it - with spawn, viewing direction and board. A ready-made lobby comes with the plugin; you can replace it with your own.

> [!NOTE]
> The lobby is only built into a **new** world. An existing waiting room world stays as it is. Without the lobby (`room.lobby: false`) you get an empty world with a small platform (`room.platform`).

### Your own lobby

1. Build your lobby in the waiting room world (e.g. from a schematic with Litematica or WorldEdit, in creative mode).
2. Stand at the spawn, look the way players should look and set it: `/setworldspawn ~ ~ ~ ~ 0`.
3. Optional: hang up the board (see below).
4. `/mrwaitroom lobby save` - MRwaitroom finds the build by itself (all blocks in the loaded chunks around you). If the lobby is larger than your view distance, give the area: `/mrwaitroom lobby save x1 y1 z1 x2 y2 z2`.

It is saved in `plugins/MRwaitroom/lobby/` (`lobby.nbt` = blocks and frames, `lobby.yml` = position, spawn, board). This lobby is then used instead of the bundled one - handy when you set up the waiting room server again. Delete the folder to get the bundled one back.

## The board

A wall of **4 × 2 item frames** shows the waiting players what is going on - everyone sees it in their own language:

- large on the left the state (restart, maintenance, server off, ready …) in its colour, below it the text and the reason or the end of the maintenance,
- on the right the countdown with a bar,
- at the bottom the next or running maintenance and the **name of your server** (from MRadmin, page Appearance).

### Hanging it up

1. Hang 8 item frames on a wall: 4 side by side, 2 on top of each other, all facing the same way (empty).
2. Look at one of the frames: `/mrwaitroom screen set`.

MRwaitroom puts the maps in by itself and locks the frames (no turning, no taking out, no breaking). Take it down: `/mrwaitroom screen remove`. If there is a board already, `screen set` moves it to the new wall.

> [!TIP]
> The texts are in the language files under `screen-…` - five lines per state (heading, text, detail, countdown, label). The board only draws when something changes, and only for players who see it.
