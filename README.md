# FarLands v2 — One File Plugin
**Paper 1.21.2 | Geyser/Bedrock Compatible**

Everything from the Wemmbus Unstable SMP Far Lands arc. One Java file.

---

## Zone Map (from 0,0 outward)

| Distance | Zone | What it is |
|----------|------|------------|
| 0 → 60k | **Normal** | Regular terrain |
| 60k → 70k | **Gravel Lands** | Surface turns to gravel, sinkholes appear |
| 70k → 80k | **Spikelands** | Spiky mountains, gradual and escalating |
| 80k → 110k | **Edge / Corner / Inner Far Lands** | The Swiss-cheese wall |
| 110k → 120k | **Repeating Dungeon Zone** | Dungeons repeat every 32 blocks, filled with gapples |
| 120k → 135k | **Biome Scramble** | Every biome crammed together randomly |
| 135k → 150k | **Absolute End** | Terrain erodes and disappears |
| 150k+ | **Void** | Nothing. |

**Orbital Cannon structures** spawn every 512 blocks along the wall face (decorative, based on the screenshot from Unstable SMP).

---

## ⚠️ NO WORLD RESET

Paper only calls `generateSurface()` for chunks that have **never been generated**. All your existing terrain is completely safe.

---

## Installation

### 1. Build the JAR
```bash
mvn clean package
# → target/FarLands-2.0.0.jar
```

### 2. Drop the JAR in plugins/

### 3. Edit bukkit.yml (REQUIRED)
```yaml
worlds:
  world:
    generator: FarLands
```
Replace `world` with your world folder name. For Nether Far Lands, add `world_nether` too.

### 4. Restart the server.

---

## Commands

| Command | Description |
|---------|-------------|
| `/fl info` | Show your zone + all zone distances |
| `/fl tp <zone>` | Teleport (op). Zones: `gravel` `spikes` `edge` `corner` `inner` `dungeon` `scramble` `end` `void` |
| `/fl reload` | Reload config live (op) |

---

## config.yml highlights

```yaml
farlands-wall-start: 200000   # Where the main wall begins
void-start: 250000            # Where void begins (Far Lands "ends" here)
orbital-cannons-enabled: true
cannon-spacing-blocks: 512    # One cannon per 512 blocks along wall
```
