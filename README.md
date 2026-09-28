# mcdata

> A version-controlled archive of extracted Minecraft data for use with [Bookshelf](https://github.com/mcbookshelf/bookshelf).

## Overview

This repository contains data extracted from various versions of Minecraft.  
Each Git tag corresponds to a specific Minecraft version and includes only the generated data for that version.

## Usage

You can access the extracted files directly via GitHub’s raw URLs:

```
https://raw.githubusercontent.com/mcbookshelf/mcdata/v1/<version>/blocks/data.min.json
```

Replace `<version>` with the desired Minecraft version, e.g. `1.21.7`.

> [!NOTE]
> The `v1` branch and its data format have been available since Minecraft `1.21.6`.
> We strongly recommend using this version for the latest and most stable data.
> For older Minecraft versions, please refer to our legacy builds, which use tags without the `v1/` prefix.

## Block data

`blocks/data.min.json` maps each block to these fields, which are the same for all its states:

| Field | Vanilla source | Meaning |
| --- | --- | --- |
| `item` | `Block.asItem()` | The block's item, or `minecraft:air` if it has none. |
| `can_occlude` | `BlockState.canOcclude()` | The default state can hide faces of neighbouring blocks. |
| `has_shape_offset` | | The outline shape moves with the block's position (flowers, bamboo…). |
| `has_visual_offset` | `BlockState.hasOffsetFunction()` | The model moves with the block's position. It's also true for blocks whose outline shape stays in place (short grass, ferns…). |
| `ignited_by_lava` | `BlockState.ignitedByLava()` | Nearby lava can set it on fire. |
| `blast_resistance` | `Block.getExplosionResistance()` | Resistance to explosions. |
| `friction` | `Block.getFriction()` | Slipperiness (`0.6` for most blocks, `0.98` for ice). |
| `hardness` | `Block.defaultDestroyTime()` | Time factor to break it, `-1` if unbreakable. |
| `jump_factor` | `Block.getJumpFactor()` | Multiplier on jump velocity when standing on it. |
| `speed_factor` | `Block.getSpeedFactor()` | Multiplier on movement speed when standing on it. |
| `instrument` | `BlockState.instrument()` | Sound event played by a note block above it. |
| `sounds` | `BlockState.getSoundType()` | Sound events for `break`, `hit`, `fall`, `place` and `step`. |
| `default_properties` | `Block.defaultBlockState()` | Property values of the default state. |
| `possible_properties` | `Block.getStateDefinition()` | Every value each property can take. |
| `states` | | One entry per block state, described below. |

Each entry of `states` has:

| Field | Vanilla source | Meaning |
| --- | --- | --- |
| `properties` | | Property values of this state. |
| `fluid` | `BlockState.getFluidState()` | The fluid's `id` and its properties (`falling`, `level`), or `{}` if there is none. |
| `shape` | `BlockState.getShape()` | Outline shape, used for targeting. |
| `collision_shape` | `BlockState.getCollisionShape()` | Collision shape, used for movement. |
| `luminance` | `BlockState.getLightEmission()` | Light level it emits, from `0` to `15`. |
| `is_conductive` | `BlockState.isRedstoneConductor()` | It conducts redstone power. |
| `is_spawnable` | `BlockState.isValidSpawn()` | Mobs can spawn on top of it, ignoring rules for specific mobs. |

Shapes are lists of boxes `[minX, minY, minZ, maxX, maxY, maxZ]`, in blocks, relative to the block's corner. Boxes may overlap, and coordinates can go outside `0`–`1` (fence collision reaches `1.5`).
When `has_shape_offset` is true, shapes are given without the offset.
Shapes are evaluated with no entity context, except for the `light` block, whose shape is the one seen while holding a light.

## Entity data

`entities/data.min.json` maps each entity type to its `dimensions` and these behaviour flags:

| Field | Vanilla source | Meaning |
| --- | --- | --- |
| `living` | `instanceof LivingEntity` | The entity is a living entity. |
| `pickable` | `Entity.isPickable()` | A player can target it (crosshair, raycast). |
| `pushable` | `Entity.isPushable()` | It pushes and is pushed by other entities (soft collision). |
| `collidable` | `Entity.canBeCollidedWith(Entity)` | Other entities can't pass through it, or can stand on it (hard collision). |
| `attached` | `instanceof BlockAttachedEntity` | Its position is the centre of its box, not the bottom (item frames, paintings, leash knots…). |

Some of these methods depend on the entity's state. The flags describe the entity in its normal state:
alive, adult, in a loaded and entity-ticking chunk, not a spectator, not ridden, not on a climbable block, and with default NBT
(an armor stand isn't a marker, an arrow isn't stuck in the ground…).
An entity that can only sometimes be collided with, like an adult happy ghast, is recorded as `collidable`.

## Credits
This project has taken inspiration from [Aeldrion/IrisDataGen](https://github.com/Aeldrion/IrisDataGen) and [misode/mcmeta](https://github.com/misode/mcmeta).
