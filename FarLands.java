package dev.farlands;

// =============================================================================
//  FarLands.java  —  v2.0.0
//  Paper 1.21.2 | Geyser/Bedrock compatible
//
//  Everything from the Wemmbus Unstable SMP Far Lands arc in one file:
//
//  ZONES (outward from 0,0):
//    0         → 175k  Normal terrain
//    175k      → 185k  Gravel Lands   – surface turns to gravel/broken ground
//    185k      → 200k  Spikelands     – spikes, gradual and escalating
//    200k      → 215k  Edge/Corner/Inner Far Lands  – the Swiss-cheese wall
//    215k      → 220k  Repeating Dungeon Zone       – lore: infinite gapples
//    220k      → 235k  Biome Scramble               – every biome crammed in
//    235k      → 250k  Absolute End                 – terrain breaks down
//    250k+            Void                          – nothing
//
//  STRUCTURES:
//    Orbital Cannon platforms every 512 blocks along the wall face
//    (decorative; based on the Unstable SMP cannon installation)
//
//  NO WORLD RESET:
//    generateSurface() is only called for chunks that have NEVER been
//    generated before. All existing region files are untouched.
//
//  SETUP:
//    1. Drop FarLands-2.0.0.jar in plugins/
//    2. Add to bukkit.yml:
//         worlds:
//           world:
//             generator: FarLands
//    3. Restart server.
//
//  COMMANDS:  /fl info | /fl tp <zone> | /fl reload
// =============================================================================

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FarLands extends JavaPlugin {

    // =========================================================================
    //  ZONE ENUM
    // =========================================================================

    enum Zone {
        NORMAL, GRAVEL_LANDS, SPIKELANDS, INNER, EDGE, CORNER,
        REPEATING_DUNGEON, BIOME_SCRAMBLE, ABSOLUTE_END, VOID
    }

    // =========================================================================
    //  PLUGIN LIFECYCLE
    // =========================================================================

    private Cfg cfg;
    private Gen generator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = new Cfg(this);
        generator = new Gen(this);

        getServer().getPluginManager().registerEvents(new Events(this), this);

        var cmd = new Cmd(this);
        Objects.requireNonNull(getCommand("farlands")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("farlands")).setTabCompleter(cmd);

        getLogger().info("FarLands v2 enabled.");
        getLogger().info("Wall starts at: ±" + cfg.wallStart + " blocks");
        getLogger().info("Void starts at: ±" + cfg.voidStart + " blocks");
        getLogger().info("IMPORTANT: Add 'generator: FarLands' to bukkit.yml for world '" + cfg.worldName + "'");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return generator;
    }

    // shorthand
    Cfg cfg() { return cfg; }

    // =========================================================================
    //  CONFIG
    // =========================================================================

    static class Cfg {
        final FarLands plugin;

        String worldName;
        boolean netherFL;
        int gravelStart, spikesStart, wallStart, dungeonStart, scrambleStart, absoluteStart, voidStart;
        int wallThickChunks;
        double wallHeightFraction, holeDensity;
        int spikeMin, spikeMax;
        double spikeDensity;
        Material spikeMat;
        int innerDepth, cornerLayers, cornerSpacing;
        boolean cannonsEnabled;
        int cannonSpacing, cannonWidth, cannonLength;
        boolean entryTitle, particles;

        // Zone messages
        String msgGravel, msgSpikes, msgWall, msgDungeon, msgScramble, msgEnd;
        String subGravel, subSpikes, subWall, subDungeon, subScramble, subEnd;

        Cfg(FarLands plugin) { this.plugin = plugin; load(); }

        void reload() { plugin.reloadConfig(); load(); }

        void load() {
            FileConfiguration c = plugin.getConfig();
            worldName          = c.getString("world-name", "world");
            netherFL           = c.getBoolean("enable-nether-farlands", true);
            gravelStart        = c.getInt("gravel-lands-start",       60000);
            spikesStart        = c.getInt("spikelands-start",          70000);
            wallStart          = c.getInt("farlands-wall-start",       80000);
            dungeonStart       = c.getInt("repeating-dungeon-start",  110000);
            scrambleStart      = c.getInt("biome-scramble-start",     120000);
            absoluteStart      = c.getInt("absolute-end-start",       135000);
            voidStart          = c.getInt("void-start",               150000);
            wallThickChunks    = c.getInt("wall-thickness-chunks", 60);
            wallHeightFraction = c.getDouble("wall-height-fraction", 0.92);
            holeDensity        = c.getDouble("hole-density", 0.38);
            spikeMin           = c.getInt("spike-min-height", 20);
            spikeMax           = c.getInt("spike-max-height", 220);
            spikeDensity       = c.getDouble("spike-density", 0.13);
            innerDepth         = c.getInt("inner-depth", 100);
            cornerLayers       = c.getInt("corner-layer-count", 4);
            cornerSpacing      = c.getInt("corner-layer-spacing", 18);
            cannonsEnabled     = c.getBoolean("orbital-cannons-enabled", true);
            cannonSpacing      = c.getInt("cannon-spacing-blocks", 512);
            cannonWidth        = c.getInt("cannon-platform-width", 40);
            cannonLength       = c.getInt("cannon-platform-length", 80);
            entryTitle         = c.getBoolean("entry-title", true);
            particles          = c.getBoolean("particle-effects", true);

            String mat = c.getString("spike-material", "STONE");
            try { spikeMat = Material.valueOf(mat.toUpperCase()); }
            catch (Exception e) { spikeMat = Material.STONE; }

            msgGravel   = c.getString("msg-gravel",   "§8§lThe terrain is changing...");
            msgSpikes   = c.getString("msg-spikes",   "§6§lSomething is wrong...");
            msgWall     = c.getString("msg-wall",     "§4§lThe Far Lands");
            msgDungeon  = c.getString("msg-dungeon",  "§d§lRepeating Structures Detected");
            msgScramble = c.getString("msg-scramble", "§b§lThe World is Breaking");
            msgEnd      = c.getString("msg-end",      "§0§lThe Absolute End");
            subGravel   = c.getString("msg-sub-gravel",   "§7The ground feels unstable.");
            subSpikes   = c.getString("msg-sub-spikes",   "§eSpikes rise from the earth.");
            subWall     = c.getString("msg-sub-wall",     "§cYou should not have come here.");
            subDungeon  = c.getString("msg-sub-dungeon",  "§5The world keeps repeating...");
            subScramble = c.getString("msg-sub-scramble", "§3Every biome at once. Impossible.");
            subEnd      = c.getString("msg-sub-end",      "§8There is nothing left.");
        }

        int wallEnd() { return wallStart + wallThickChunks * 16; }
    }

    // =========================================================================
    //  NOISE
    //  Simulates the "integer overflow" noise discontinuity that created the
    //  original Far Lands in Beta Minecraft. The overflowWave() function is
    //  the key piece — it produces the alternating solid/hole bands.
    // =========================================================================

    static class Noise {
        private final int[] perm;
        private final int[] perm2;

        Noise(long seed) {
            perm  = buildPerm(seed);
            perm2 = buildPerm(seed ^ 0xDEADBEEFL);
        }

        // --- PUBLIC ---

        /** Edge Far Lands overflow noise. face=along wall, y=vertical, perp=overflow axis */
        double edge(int face, int y, int perp) {
            double ov = overflowWave(perp);
            double fn = octave(face * 0.008, y * 0.012, 0, 4, 0.5);
            double yb = yFalloff(y);
            return clamp(ov * 0.55 + fn * 0.35 + yb * 0.10);
        }

        /** Corner Far Lands — both X and Z are overflow axes */
        double corner(int x, int y, int z) {
            double ox = overflowWave(x), oz = overflowWave(z);
            double yn = octave(0, y * 0.015, 0, 3, 0.5);
            return clamp((ox + oz) * 0.30 + yn * 0.30 + yFalloff(y) * 0.10);
        }

        /** Inner Far Lands — cavernous overhung ceiling */
        double inner(int x, int y, int z, double progress) {
            double n = octave(x * 0.022, y * 0.016, z * 0.022, 5, 0.55);
            return clamp(n + (1.0 - progress) * 0.28);
        }

        /** Spike placement — Worley-style cell noise */
        double spike(int x, int z) { return worley(x, z, 22); }

        /** Base terrain height offset (−1 to +1) */
        double terrain(int x, int z) {
            return octave(x * 0.005, 0, z * 0.005, 4, 0.5) * 2.0 - 1.0;
        }

        /** Scramble noise — maps (x,z) → a biome index offset */
        double scramble(int x, int z) {
            return octave(x * 0.03, 0, z * 0.03, 2, 0.5);
        }

        /** Absolute-end erosion — how much terrain disappears */
        double erosion(int x, int y, int z) {
            return octave(x * 0.04, y * 0.04, z * 0.04, 3, 0.5);
        }

        // --- PRIVATE ---

        private double overflowWave(int c) {
            double d = c * 0.003141592;
            double v = Math.sin(d)
                     + 0.50 * Math.sin(d * 2.03)
                     + 0.25 * Math.sin(d * 4.07)
                     + 0.12 * Math.sin(d * 8.13)
                     + 0.06 * Math.sin(d * 16.27);
            return (v + 1.93) / 3.86;
        }

        private double yFalloff(int y) {
            // Denser at bottom and top of wall
            return -(4.0 * (y / 320.0) * (1.0 - y / 320.0)) + 0.5;
        }

        private double octave(double x, double y, double z, int oct, double p) {
            double t = 0, a = 1, f = 1, m = 0;
            for (int i = 0; i < oct; i++) {
                t += value3d(x*f, y*f, z*f) * a;
                m += a; a *= p; f *= 2.01;
            }
            return t / m;
        }

        private double value3d(double x, double y, double z) {
            int xi = (int)Math.floor(x), yi = (int)Math.floor(y), zi = (int)Math.floor(z);
            double xf = x-xi, yf = y-yi, zf = z-zi;
            double u = fade(xf), v = fade(yf), w = fade(zf);
            int aaa=perm[(perm[(perm[xi&255]+(yi&255))&255]+(zi&255))&255];
            int aba=perm[(perm[(perm[xi&255]+((yi+1)&255))&255]+(zi&255))&255];
            int aab=perm[(perm[(perm[xi&255]+(yi&255))&255]+((zi+1)&255))&255];
            int abb=perm[(perm[(perm[xi&255]+((yi+1)&255))&255]+((zi+1)&255))&255];
            int baa=perm[(perm[(perm[(xi+1)&255]+(yi&255))&255]+(zi&255))&255];
            int bba=perm[(perm[(perm[(xi+1)&255]+((yi+1)&255))&255]+(zi&255))&255];
            int bab=perm[(perm[(perm[(xi+1)&255]+(yi&255))&255]+((zi+1)&255))&255];
            int bbb=perm[(perm[(perm[(xi+1)&255]+((yi+1)&255))&255]+((zi+1)&255))&255];
            double x1=lerp(u,aaa/255.0,baa/255.0), x2=lerp(u,aba/255.0,bba/255.0);
            double y1=lerp(v,x1,x2);
            double x3=lerp(u,aab/255.0,bab/255.0), x4=lerp(u,abb/255.0,bbb/255.0);
            return lerp(w,y1,lerp(v,x3,x4));
        }

        private double worley(int x, int z, int cell) {
            int cx=Math.floorDiv(x,cell), cz=Math.floorDiv(z,cell);
            double min=Double.MAX_VALUE;
            for (int dx=-1;dx<=1;dx++) for (int dz2=-1;dz2<=1;dz2++) {
                int h=hash2(cx+dx,cz+dz2);
                double px=(cx+dx+(h&0xFF)/255.0)*cell;
                double pz=(cz+dz2+((h>>8)&0xFF)/255.0)*cell;
                double d=Math.sqrt((x-px)*(x-px)+(z-pz)*(z-pz));
                if(d<min)min=d;
            }
            return 1.0-Math.min(1.0,min/(cell*0.7));
        }

        private static double fade(double t){return t*t*t*(t*(t*6-15)+10);}
        private static double lerp(double t,double a,double b){return a+t*(b-a);}
        private static double clamp(double v){return Math.max(0,Math.min(1,v));}
        private int hash2(int x,int z){int h=x*374761393+z*668265263;h=(h^(h>>13))*1274126177;return h^(h>>16);}
        private int[] buildPerm(long seed){
            int[] p=new int[512]; Random r=new Random(seed);
            for(int i=0;i<256;i++)p[i]=i;
            for(int i=255;i>0;i--){int j=r.nextInt(i+1);int t=p[i];p[i]=p[j];p[j]=t;}
            System.arraycopy(p,0,p,256,256); return p;
        }
    }

    // =========================================================================
    //  CHUNK GENERATOR
    //  Classifies each chunk into a Zone, then generates the appropriate
    //  terrain. Delegates normal terrain to a simple octave-noise heightmap.
    //  All Far Lands zones use the Noise class above.
    //
    //  KEY GUARANTEE: This method is only called for chunks that have NEVER
    //  been generated. Existing explored chunks are NEVER touched.
    // =========================================================================

    static class Gen extends ChunkGenerator {

        private final FarLands plugin;
        private final Noise noise;

        Gen(FarLands plugin) {
            this.plugin = plugin;
            this.noise  = new Noise(12345678L);
        }

        @Override
        public void generateSurface(@NotNull WorldInfo wi, @NotNull Random r,
                                    int cx, int cz, @NotNull ChunkData cd) {
            Cfg c = plugin.cfg();
            int minY = wi.getMinHeight(), maxY = wi.getMaxHeight();
            int ox = cx * 16, oz = cz * 16;
            Zone zone = classify(ox, oz, c);

            switch (zone) {
                case NORMAL            -> genNormal      (r, ox, oz, cd, minY, maxY);
                case GRAVEL_LANDS      -> genGravelLands (r, ox, oz, cd, minY, maxY, c);
                case SPIKELANDS        -> genSpikelands  (r, ox, oz, cd, minY, maxY, c);
                case EDGE              -> genEdge        (r, ox, oz, cd, minY, maxY, c);
                case CORNER            -> genCorner      (r, ox, oz, cd, minY, maxY, c);
                case INNER             -> genInner       (r, ox, oz, cd, minY, maxY, c);
                case REPEATING_DUNGEON -> genRepeating   (r, ox, oz, cd, minY, maxY, c);
                case BIOME_SCRAMBLE    -> genScramble    (r, ox, oz, cd, minY, maxY, c);
                case ABSOLUTE_END      -> genAbsoluteEnd (r, ox, oz, cd, minY, maxY, c);
                case VOID              -> genVoid        (r, cd, minY, maxY);
            }
        }

        // ---- ZONE CLASSIFIER ----

        Zone classify(int ox, int oz, Cfg c) {
            int ax = Math.abs(ox), az = Math.abs(oz);
            int max = Math.max(ax, az);
            int wallEnd = c.wallEnd();

            // Walls are axis-aligned, so check each axis independently
            boolean xWall = ax >= c.wallStart && ax < wallEnd;
            boolean zWall = az >= c.wallStart && az < wallEnd;
            boolean pastWall = ax >= wallEnd || az >= wallEnd;

            if (max >= c.voidStart)          return Zone.VOID;
            if (max >= c.absoluteStart)      return Zone.ABSOLUTE_END;
            if (max >= c.scrambleStart)      return Zone.BIOME_SCRAMBLE;
            if (max >= c.dungeonStart)       return Zone.REPEATING_DUNGEON;
            if (pastWall)                    return Zone.REPEATING_DUNGEON; // inside the wall thickness but past wall edge
            if (xWall && zWall)              return Zone.CORNER;
            if (xWall || zWall) {
                boolean inner = (xWall && ax < c.wallStart + c.innerDepth)
                             || (zWall && az < c.wallStart + c.innerDepth);
                return inner ? Zone.INNER : Zone.EDGE;
            }
            if (max >= c.spikesStart)        return Zone.SPIKELANDS;
            if (max >= c.gravelStart)        return Zone.GRAVEL_LANDS;
            return Zone.NORMAL;
        }

        // ---- NORMAL TERRAIN ----
        // Realistic-looking terrain using two-octave continent + detail noise.

        void genNormal(Random r, int ox, int oz, ChunkData cd, int minY, int maxY) {
            int sea = 63 - minY;
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int bx = ox+lx, bz = oz+lz;
                double cont = octave2d(bx*0.001, bz*0.001, 3, 0.5) * 28;
                double det  = octave2d(bx*0.007, bz*0.007, 4, 0.5) * 10;
                int surf = clampY((int)(sea + cont + det - 18), 2, maxY-minY-10);
                bedrock(cd, lx, lz, r);
                fillStone(cd, lx, lz, 1, surf-3);
                fillDirt (cd, lx, lz, surf-3, surf);
                cd.setBlock(lx, surf, lz, Material.GRASS_BLOCK);
                if (surf < sea) for (int y=surf+1;y<=sea;y++) cd.setBlock(lx,y,lz,Material.WATER);
            }
        }

        // ---- GRAVEL LANDS ----
        // Surface turns to gravel with some patches of dirt/grass.
        // First sign of Far Lands — unsettling but still traversable.

        void genGravelLands(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int sea = 63 - minY;
            double progress = Math.min(1.0, (double)(Math.max(Math.abs(ox),Math.abs(oz)) - c.gravelStart)
                              / (c.spikesStart - c.gravelStart));

            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int bx=ox+lx, bz=oz+lz;
                double cont = octave2d(bx*0.001, bz*0.001, 3, 0.5)*28;
                double det  = octave2d(bx*0.007, bz*0.007, 4, 0.5)*10;
                int surf = clampY((int)(sea + cont + det - 18), 2, maxY-minY-10);

                bedrock(cd, lx, lz, r);
                fillStone(cd, lx, lz, 1, surf-2);

                // Transition from dirt/grass to gravel surface
                Material top = r.nextDouble() < progress ? Material.GRAVEL :
                               r.nextDouble() < 0.5 ? Material.DIRT : Material.GRASS_BLOCK;
                if (top == Material.GRASS_BLOCK) {
                    fillDirt(cd, lx, lz, surf-2, surf);
                    cd.setBlock(lx, surf, lz, Material.GRASS_BLOCK);
                } else {
                    for (int y=surf-2;y<=surf;y++) cd.setBlock(lx,y,lz,top);
                }
                if (surf < sea) for (int y=surf+1;y<=sea;y++) cd.setBlock(lx,y,lz,Material.WATER);

                // Occasional sinkhole pits in gravel lands
                if (r.nextInt(80) == 0) {
                    int depth = 3 + r.nextInt(8);
                    for (int y=surf;y>surf-depth&&y>1;y--)
                        cd.setBlock(lx, y, lz, Material.AIR);
                }
            }
        }

        // ---- SPIKELANDS ----
        // Normal terrain base + spike columns. Density and height scale with
        // proximity to the wall. Matches the Unstable SMP "spiky mountains" zone.

        void genSpikelands(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int sea = 63 - minY;
            int dist = Math.max(Math.abs(ox), Math.abs(oz));
            double progress = Math.pow(Math.min(1.0,
                (double)(dist - c.spikesStart) / (c.wallStart - c.spikesStart)), 1.4);

            // Base terrain (gravel-heavy like the end of gravel lands)
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int bx=ox+lx, bz=oz+lz;
                int surf = clampY((int)(sea + noise.terrain(bx,bz)*9), 2, maxY-minY-20);
                bedrock(cd, lx, lz, r);
                fillStone(cd, lx, lz, 1, surf-1);
                cd.setBlock(lx, surf, lz, r.nextDouble()<0.6?Material.GRAVEL:Material.DIRT);
                if (surf < sea) for (int y=surf+1;y<=sea;y++) cd.setBlock(lx,y,lz,Material.WATER);
            }

            // Spike columns
            double eff = c.spikeDensity * (0.1 + 0.9 * progress);
            int effMax = (int)(c.spikeMin + (c.spikeMax - c.spikeMin) * progress);

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                if (noise.spike(bx, bz) < eff) continue;
                int base = highestSolid(cd, lx, lz, minY, maxY);
                int height = c.spikeMin + (int)(r.nextDouble()*(effMax-c.spikeMin));
                int top = Math.min(base+height, maxY-minY-2);
                for (int y=base;y<=top;y++) cd.setBlock(lx,y,lz,c.spikeMat);
                // Random cap
                if (top+1<maxY-minY && r.nextInt(4)==0)
                    cd.setBlock(lx,top+1,lz,Material.COBBLESTONE);
                // Occasional deepslate spike variety
                if (r.nextInt(8)==0)
                    for (int y=base;y<base+Math.min(8,height/3);y++)
                        cd.setBlock(lx,y,lz,Material.DEEPSLATE);
            }
        }

        // ---- EDGE FAR LANDS ----
        // The classic Swiss-cheese wall. A massive vertical face of stone
        // riddled with infinite tunnels in one direction.
        // The overflowWave noise is what creates the hole pattern.

        void genEdge(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int wallH = (int)((maxY-minY)*c.wallHeightFraction);
            boolean xWall = Math.abs(ox) >= c.wallStart;

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                cd.setBlock(lx, 0, lz, Material.BEDROCK);

                for (int y=1;y<=wallH;y++) {
                    // xWall: face runs along Z, tunnels punch through in X (and vice versa)
                    double n = xWall ? noise.edge(bz, y+minY, bx) : noise.edge(bx, y+minY, bz);
                    if (n > c.holeDensity) cd.setBlock(lx, y, lz, wallMat(y, wallH, r));
                }
                cd.setBlock(lx, wallH+1, lz, Material.BEDROCK);
            }
        }

        // ---- CORNER FAR LANDS ----
        // Where two Edge walls meet. Layered strata — alternating dense/sparse bands.
        // More claustrophobic, more chaotic than Edge.

        void genCorner(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int wallH = (int)((maxY-minY)*c.wallHeightFraction);

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                cd.setBlock(lx, 0, lz, Material.BEDROCK);

                for (int y=1;y<=wallH;y++) {
                    double n = noise.corner(bx, y+minY, bz);
                    int layer = (y/c.cornerSpacing)%c.cornerLayers;
                    double bias = (layer%2==0) ? 0.12 : -0.08;
                    if (n+bias > c.holeDensity-0.05) cd.setBlock(lx, y, lz, wallMat(y, wallH, r));
                }
                cd.setBlock(lx, wallH+1, lz, Material.BEDROCK);
            }
        }

        // ---- INNER FAR LANDS ----
        // The eerie cavernous inside face of the Edge wall. Massive overhung
        // stone ceiling, crevassed floor, water pools at the bottom.
        // This is what players see first when approaching from the normal world.

        void genInner(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int sea = 63-minY;
            int ax=Math.abs(ox), az=Math.abs(oz);
            boolean xSide = ax >= c.wallStart;
            int distFromWall = xSide ? (ax-c.wallStart) : (az-c.wallStart);
            double prog = (double)distFromWall / c.innerDepth;

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                cd.setBlock(lx, 0, lz, Material.BEDROCK);

                int totalH = maxY-minY;
                for (int y=1;y<totalH-1;y++) {
                    double n = noise.inner(bx, y+minY, bz, prog);
                    boolean upper = y > totalH*0.5;
                    double thresh = upper ? (c.holeDensity+0.18-prog*0.12) : (c.holeDensity-0.22+prog*0.08);
                    if (n > thresh) cd.setBlock(lx, y, lz, wallMat(y, totalH, r));
                }
                for (int y=1;y<=sea;y++)
                    if (cd.getBlockData(lx,y,lz).getMaterial()==Material.AIR)
                        cd.setBlock(lx, y, lz, Material.WATER);
            }
        }

        // ---- REPEATING DUNGEON ZONE ----
        // Lore from the Unstable SMP: dungeons repeat endlessly here, filled
        // with enchanted golden apples. We generate repeating stone chambers
        // with chest markers (the actual loot is placed by the Populator).

        void genRepeating(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            // Base: stone wasteland
            int sea = 63-minY;
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                cd.setBlock(lx,0,lz,Material.BEDROCK);
                for (int y=1;y<=sea-5;y++) cd.setBlock(lx,y,lz,Material.STONE);
                // Flat, broken top layer
                for (int y=sea-5;y<=sea;y++) {
                    Material m = r.nextInt(4)==0 ? Material.GRAVEL :
                                 r.nextInt(3)==0 ? Material.AIR : Material.STONE;
                    cd.setBlock(lx,y,lz,m);
                }
            }

            // Dungeon rooms repeat on a 32-block grid
            int dungeonPeriod = 32;
            int localX = Math.floorMod(ox, dungeonPeriod);
            int localZ = Math.floorMod(oz, dungeonPeriod);

            // If this chunk contains the dungeon room corner
            if (localX < 16 && localZ < 16) {
                int roomY = sea - 8;
                // Hollow out a room
                for (int lx=1;lx<15;lx++) for (int lz=1;lz<15;lz++) {
                    for (int y=roomY;y<=roomY+7;y++) {
                        if (lx==0||lx==15||lz==0||lz==15||y==roomY||y==roomY+7)
                            cd.setBlock(lx,y,lz,Material.MOSSY_COBBLESTONE);
                        else
                            cd.setBlock(lx,y,lz,Material.AIR);
                    }
                }
                // Spawner marker — Populator sets mob type
                cd.setBlock(7, roomY+3, 7, Material.SPAWNER);
                // Chest markers — Populator fills with gapples
                cd.setBlock(2, roomY+1, 2, Material.CHEST);
                cd.setBlock(13, roomY+1, 13, Material.CHEST);
            }
        }

        // ---- BIOME SCRAMBLE ----
        // "A biome with chunks of every biome in the game" — terrain violently
        // switches between desert, snow, jungle, ocean etc. within metres.

        void genScramble(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int sea = 63-minY;
            // Divide the chunk into 4x4 cell squares, each with random biome-like surface
            Material[] surfaces = {
                Material.SAND, Material.SNOW_BLOCK, Material.MYCELIUM,
                Material.PODZOL, Material.GRASS_BLOCK, Material.RED_SAND,
                Material.GRAVEL, Material.SOUL_SAND, Material.DIRT
            };
            Material[] fills = {
                Material.SANDSTONE, Material.PACKED_ICE, Material.DIRT,
                Material.DIRT, Material.DIRT, Material.RED_SANDSTONE,
                Material.GRAVEL, Material.SOUL_SOIL, Material.DIRT
            };

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                // Which "biome cell" are we in?
                int cell = (int)(noise.scramble(bx, bz) * surfaces.length) % surfaces.length;
                int surf = clampY((int)(sea + noise.terrain(bx,bz)*6), 2, maxY-minY-8);

                cd.setBlock(lx,0,lz,Material.BEDROCK);
                for (int y=1;y<surf-2;y++) cd.setBlock(lx,y,lz,Material.STONE);
                for (int y=surf-2;y<surf;y++) cd.setBlock(lx,y,lz,fills[cell]);
                cd.setBlock(lx,surf,lz,surfaces[cell]);

                // Some cells are flooded (ocean fragments)
                if (cell==6||cell==3) {
                    if (surf < sea) for (int y=surf+1;y<=sea;y++) cd.setBlock(lx,y,lz,Material.WATER);
                }
                // Ice on snow cells
                if (cell==1 && surf>=sea) cd.setBlock(lx,surf+1,lz,Material.SNOW);
            }
        }

        // ---- ABSOLUTE END ----
        // Terrain progressively erodes and disappears. Grass is almost gone.
        // The further in, the more the terrain deletes itself.

        void genAbsoluteEnd(Random r, int ox, int oz, ChunkData cd, int minY, int maxY, Cfg c) {
            int dist = Math.max(Math.abs(ox), Math.abs(oz));
            double erosionProgress = Math.min(1.0,
                (double)(dist - c.absoluteStart) / (c.voidStart - c.absoluteStart));

            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=ox+lx, bz=oz+lz;
                cd.setBlock(lx,0,lz,Material.BEDROCK);

                int totalH = maxY-minY;
                for (int y=1;y<totalH;y++) {
                    // Erosion noise — above the threshold, the block simply doesn't exist
                    double en = noise.erosion(bx, y+minY, bz);
                    double erodeThresh = 0.2 + erosionProgress*0.7; // 0.2→0.9 as you go deeper
                    if (en > erodeThresh) {
                        Material m = y<5 ? Material.BEDROCK : (y<20 ? Material.DEEPSLATE : Material.STONE);
                        cd.setBlock(lx,y,lz,m);
                    }
                    // else: air — the terrain is literally disappearing
                }
            }
        }

        // ---- VOID ----
        // Sparse rubble, lone pillars. Mostly nothing.

        void genVoid(Random r, ChunkData cd, int minY, int maxY) {
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                cd.setBlock(lx,0,lz,Material.BEDROCK);
                if (r.nextDouble()<0.02) {
                    int h=3+r.nextInt(40);
                    for (int y=1;y<Math.min(h,maxY-minY);y++)
                        cd.setBlock(lx,y,lz,r.nextBoolean()?Material.STONE:Material.GRAVEL);
                }
            }
        }

        // ---- HELPERS ----

        Material wallMat(int y, int wallH, Random r) {
            if (y<=4)         return Material.BEDROCK;
            if (y<=12)        return r.nextBoolean()?Material.DEEPSLATE:Material.STONE;
            if (y>wallH-5)    return r.nextBoolean()?Material.COBBLESTONE:Material.STONE;
            int rng=r.nextInt(130);
            if (rng<1)  return Material.DIAMOND_ORE;
            if (rng<4)  return Material.IRON_ORE;
            if (rng<8)  return Material.COAL_ORE;
            if (rng<11) return Material.GRAVEL;
            if (rng<13) return Material.MOSSY_COBBLESTONE;
            return Material.STONE;
        }

        void bedrock(ChunkData cd, int lx, int lz, Random r) {
            cd.setBlock(lx,0,lz,Material.BEDROCK);
            for (int y=1;y<=4;y++) if (r.nextInt(5-y)==0) cd.setBlock(lx,y,lz,Material.BEDROCK);
        }

        void fillStone(ChunkData cd, int lx, int lz, int from, int to) {
            for (int y=Math.max(0,from);y<to;y++) cd.setBlock(lx,y,lz,Material.STONE);
        }

        void fillDirt(ChunkData cd, int lx, int lz, int from, int to) {
            for (int y=Math.max(0,from);y<to;y++) cd.setBlock(lx,y,lz,Material.DIRT);
        }

        int highestSolid(ChunkData cd, int lx, int lz, int minY, int maxY) {
            for (int y=maxY-minY-1;y>=0;y--) {
                Material m=cd.getBlockData(lx,y,lz).getMaterial();
                if (m!=Material.AIR&&m!=Material.WATER&&m!=Material.CAVE_AIR) return y;
            }
            return 0;
        }

        int clampY(int y, int min, int max) { return Math.max(min,Math.min(y,max)); }

        double octave2d(double x, double z, int oct, double p) {
            double t=0,a=1,f=1,m=0;
            for (int i=0;i<oct;i++) {
                t+=val2d(x*f,z*f)*a; m+=a; a*=p; f*=2.01;
            }
            return t/m;
        }

        private final int[] p2 = initP();
        private int[] initP() {
            int[] p=new int[512]; Random r=new Random(987654321L);
            for(int i=0;i<256;i++)p[i]=i;
            for(int i=255;i>0;i--){int j=r.nextInt(i+1);int t=p[i];p[i]=p[j];p[j]=t;}
            System.arraycopy(p,0,p,256,256); return p;
        }
        double val2d(double x, double z) {
            int xi=(int)Math.floor(x),zi=(int)Math.floor(z);
            double xf=x-xi,zf=z-zi,u=fade(xf),w=fade(zf);
            int aa=p2[(p2[xi&255]+zi)&255],ab=p2[(p2[xi&255]+zi+1)&255];
            int ba=p2[(p2[(xi+1)&255]+zi)&255],bb=p2[(p2[(xi+1)&255]+zi+1)&255];
            return lerp(w,lerp(u,aa/255.0,ba/255.0),lerp(u,ab/255.0,bb/255.0));
        }
        double fade(double t){return t*t*t*(t*(t*6-15)+10);}
        double lerp(double t,double a,double b){return a+t*(b-a);}

        @Override public boolean shouldGenerateNoise()       { return false; }
        @Override public boolean shouldGenerateSurface()     { return false; }
        @Override public boolean shouldGenerateBedrock()     { return false; }
        @Override public boolean shouldGenerateCaves()       { return true;  }
        @Override public boolean shouldGenerateDecorations() { return true;  }
        @Override public boolean shouldGenerateMobs()        { return true;  }
        @Override public boolean shouldGenerateStructures()  { return true;  }

        @Override
        public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
            return List.of(new Pop(plugin));
        }
    }

    // =========================================================================
    //  POPULATOR
    //  Runs after terrain generation to add:
    //    - Orbital Cannon structures along the wall face
    //    - Lava/spawners inside wall caverns
    //    - Gapple chests in repeating dungeons
    //    - Mossy cobblestone / deepslate details
    // =========================================================================

    static class Pop extends BlockPopulator {
        private final FarLands plugin;
        Pop(FarLands plugin) { this.plugin = plugin; }

        @Override
        public void populate(@NotNull World w, @NotNull Random r, @NotNull Chunk chunk) {
            Cfg c = plugin.cfg();
            int ox = chunk.getX()*16, oz = chunk.getZ()*16;
            Zone zone = plugin.generator.classify(ox, oz, c);

            switch (zone) {
                case EDGE, INNER  -> populateWall     (w, chunk, r, c);
                case CORNER       -> populateCorner   (w, chunk, r, c);
                case SPIKELANDS   -> populateSpikes   (w, chunk, r, c);
                case REPEATING_DUNGEON -> populateDungeon(w, chunk, r, c);
                default -> {}
            }

            // Orbital Cannons: spawn one along the +X wall face every cannonSpacing blocks
            if (c.cannonsEnabled) trySpawnCannon(w, chunk, r, c, ox, oz);
        }

        // ---- WALL DETAILS ----

        void populateWall(World w, Chunk chunk, Random r, Cfg c) {
            int minY = w.getMinHeight();
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=chunk.getX()*16+lx, bz=chunk.getZ()*16+lz;

                // Lava pools inside open caverns
                if (r.nextInt(18)==0) {
                    int ly = minY+5+r.nextInt(18);
                    Block b = w.getBlockAt(bx,ly,bz);
                    if (b.getType()==Material.AIR) b.setType(Material.LAVA);
                }
                // Mossy cobblestone veins
                if (r.nextInt(25)==0) {
                    for (int y=minY+8;y<minY+80;y++) {
                        Block b=w.getBlockAt(bx,y,bz);
                        if (b.getType()==Material.STONE&&r.nextInt(3)==0)
                            b.setType(Material.MOSSY_COBBLESTONE);
                    }
                }
                // Spawners (rare — ~0.4% per column)
                if (r.nextInt(250)==0) {
                    for (int y=w.getMaxHeight()-15;y>minY+8;y--) {
                        Block b=w.getBlockAt(bx,y,bz);
                        if (b.getType()==Material.AIR) { b.setType(Material.SPAWNER); break; }
                    }
                }
            }
        }

        void populateCorner(World w, Chunk chunk, Random r, Cfg c) {
            int minY = w.getMinHeight();
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=chunk.getX()*16+lx, bz=chunk.getZ()*16+lz;
                // Deepslate base
                for (int y=minY;y<minY+28;y++) {
                    Block b=w.getBlockAt(bx,y,bz);
                    if (b.getType()==Material.STONE) b.setType(Material.DEEPSLATE);
                }
                if (r.nextInt(120)==0) {
                    for (int y=w.getMaxHeight()-10;y>minY+5;y--) {
                        Block b=w.getBlockAt(bx,y,bz);
                        if (b.getType()==Material.AIR){b.setType(Material.SPAWNER);break;}
                    }
                }
            }
        }

        void populateSpikes(World w, Chunk chunk, Random r, Cfg c) {
            if (r.nextInt(6)!=0) return;
            int bx=chunk.getX()*16+2+r.nextInt(12), bz=chunk.getZ()*16+2+r.nextInt(12);
            int surf=w.getHighestBlockYAt(bx,bz);
            int depth=4+r.nextInt(10);
            for (int dy=0;dy<=depth;dy++) {
                int rad=Math.max(1,(depth-dy)/3);
                for (int rx=-rad;rx<=rad;rx++) for (int rz=-rad;rz<=rad;rz++) {
                    if (rx*rx+rz*rz<=rad*rad) {
                        Block b=w.getBlockAt(bx+rx,surf-dy,bz+rz);
                        if (b.getType()!=Material.BEDROCK) b.setType(Material.AIR);
                    }
                }
            }
        }

        void populateDungeon(World w, Chunk chunk, Random r, Cfg c) {
            // Fill chest markers with enchanted golden apples (the lore gapples)
            for (int lx=0;lx<16;lx++) for (int lz=0;lz<16;lz++) {
                int bx=chunk.getX()*16+lx, bz=chunk.getZ()*16+lz;
                for (int y=w.getMinHeight();y<w.getMaxHeight();y++) {
                    Block b=w.getBlockAt(bx,y,bz);
                    if (b.getType()==Material.CHEST) {
                        var state=(org.bukkit.block.Chest)b.getState();
                        var inv=state.getInventory();
                        // Fill with gapples — the repeating dungeon infinite gapple lore
                        var gapple=new org.bukkit.inventory.ItemStack(Material.ENCHANTED_GOLDEN_APPLE,
                            1+r.nextInt(4));
                        for (int slot=0;slot<inv.getSize();slot++) {
                            if (r.nextInt(3)==0) inv.setItem(slot,gapple.clone());
                        }
                        state.update();
                    }
                }
            }
        }

        // ---- ORBITAL CANNON STRUCTURE ----
        //
        // Decorative cannon installation along the Far Lands wall face.
        // Based on the Unstable SMP cannon structure: a long horizontal platform
        // of blackstone/polished basalt with purple beacon pillars spaced along it,
        // iron bars framing the sides, and chain/lantern details.
        //
        // Spawns one platform every cannonSpacing blocks along the ±X wall face
        // (and ±Z wall face), at sea level, flush with the wall.

        void trySpawnCannon(World w, Chunk chunk, Random r, Cfg c, int ox, int oz) {
            // Only place cannons along the EDGE wall faces (not corner or inside)
            int ax=Math.abs(ox), az=Math.abs(oz);
            boolean xWall = ax>=c.wallStart && ax<c.wallEnd() && az<c.wallStart;
            boolean zWall = az>=c.wallStart && az<c.wallEnd() && ax<c.wallStart;
            if (!xWall && !zWall) return;

            // Determine the "face coordinate" (the one that runs along the wall)
            int faceCoord = xWall ? oz : ox;

            // Does this chunk contain a cannon spawn point?
            // Cannons align on cannonSpacing-block boundaries
            int spacing = c.cannonSpacing;
            int chunkFaceMin = faceCoord;
            int chunkFaceMax = faceCoord + 16;
            int nearestCannon = (int)(Math.round((double)faceCoord / spacing) * spacing);

            if (nearestCannon < chunkFaceMin || nearestCannon >= chunkFaceMax) return;

            // Cannon origin: at the wall face, on the outside (inner face side)
            int wallFace = xWall ? (int)Math.copySign(c.wallStart, ox) : ox;
            int wallFaceZ = zWall ? (int)Math.copySign(c.wallStart, oz) : oz;

            int platformY = 64; // sea level — cannons rest on the water/ground at wall base

            if (xWall) {
                buildCannon(w, wallFace, platformY, nearestCannon, true, c);
            } else {
                buildCannon(w, nearestCannon, platformY, wallFaceZ, false, c);
            }
        }

        /**
         * Build one orbital cannon platform.
         *
         * The structure (based on the screenshot):
         *   - Main platform: blackstone slab floor, polished basalt frame
         *   - Purple beacon pillars at regular intervals (the glowing purple columns)
         *   - Iron bar railings along the sides
         *   - Chain + lantern details hanging below
         *   - Barrel / chest / crafting table scatter for the "lived-in" SMP look
         *   - A long "barrel" pointing outward made of polished basalt + iron blocks
         *
         * @param xAlign  true = platform runs along Z axis (placed on ±X wall)
         *                false = platform runs along X axis (placed on ±Z wall)
         */
        void buildCannon(World w, int baseX, int baseY, int baseZ,
                         boolean xAlign, Cfg c) {

            int len = c.cannonLength;   // length along wall face
            int wid = c.cannonWidth;    // depth away from wall

            // Sign: which direction away from wall
            int sign = (baseX < 0 || baseZ < 0) ? 1 : -1; // face inward

            for (int i = 0; i < len; i++) {
                for (int j = 0; j < wid; j++) {
                    int bx, bz;
                    if (xAlign) {
                        bx = baseX + sign * j;
                        bz = baseZ + i - len/2;
                    } else {
                        bx = baseX + i - len/2;
                        bz = baseZ + sign * j;
                    }

                    // ---- Floor ----
                    setBlock(w, bx, baseY,   bz, Material.POLISHED_BASALT);
                    setBlock(w, bx, baseY-1, bz, Material.BLACKSTONE);

                    // ---- Walls / Railings ----
                    if (j==0||j==wid-1) {
                        setBlock(w, bx, baseY+1, bz, Material.IRON_BARS);
                        setBlock(w, bx, baseY+2, bz, Material.IRON_BARS);
                    }

                    // ---- Frame border ----
                    if (i==0||i==len-1||j==0||j==wid-1) {
                        setBlock(w, bx, baseY, bz, Material.POLISHED_BLACKSTONE);
                    }

                    // ---- Support pillars below ----
                    for (int down=1;down<=6;down++) {
                        Block below = w.getBlockAt(bx,baseY-down,bz);
                        if (below.getType()==Material.AIR||below.getType()==Material.WATER)
                            setBlock(w,bx,baseY-down,bz,Material.CHAIN);
                    }
                }
            }

            // ---- Purple Beacon Pillars ----
            // Evenly spaced pillars of purple glass + beacon, matching the screenshot
            int pillarSpacing = 10;
            for (int i = pillarSpacing/2; i < len; i += pillarSpacing) {
                int bx, bz;
                if (xAlign) { bx = baseX + sign*(wid/2); bz = baseZ + i - len/2; }
                else        { bx = baseX + i - len/2;    bz = baseZ + sign*(wid/2); }

                // Base column
                setBlock(w, bx, baseY+1, bz, Material.PURPLE_STAINED_GLASS);
                setBlock(w, bx, baseY+2, bz, Material.BEACON);
                setBlock(w, bx, baseY+3, bz, Material.PURPLE_STAINED_GLASS);
                setBlock(w, bx, baseY+4, bz, Material.PURPLE_STAINED_GLASS);
                setBlock(w, bx, baseY+5, bz, Material.PURPLE_CONCRETE);

                // Halo ring around beacon
                int[][] ring = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : ring) {
                    setBlock(w, bx+d[0], baseY+2, bz+d[1], Material.PURPLE_STAINED_GLASS);
                }
            }

            // ---- Cannon Barrel (pointing outward from wall) ----
            // A long cylinder of polished basalt extending away from the platform
            int barrelLen = 12;
            int midI = len/2;
            for (int k=1;k<=barrelLen;k++) {
                int bx, bz;
                if (xAlign) { bx = baseX + sign*(wid+k); bz = baseZ + midI - len/2; }
                else        { bx = baseX + midI - len/2; bz = baseZ + sign*(wid+k); }

                setBlock(w, bx, baseY+1, bz, Material.POLISHED_BASALT);
                setBlock(w, bx, baseY+2, bz, Material.IRON_BLOCK);
                setBlock(w, bx, baseY+3, bz, Material.POLISHED_BASALT);
                // End cap
                if (k==barrelLen) {
                    setBlock(w, bx, baseY+2, bz, Material.OBSIDIAN);
                }
            }

            // ---- Detail scatter (barrels, lanterns, rails) ----
            // Spread some lived-in props around the platform surface
            Random prop = new Random((long)baseX*31337+baseZ);
            int[] propXOff = {3, 7, len-5, len-9, len/2, 5, len-4};
            int[] propZOff = {1, wid-2, 1, wid-2, wid/2, wid-3, 3};
            Material[] propMats = {
                Material.BARREL, Material.CRAFTING_TABLE, Material.CHEST,
                Material.LANTERN, Material.IRON_BARS, Material.LEVER, Material.TARGET
            };
            for (int p = 0; p < propXOff.length; p++) {
                if (propXOff[p]>=len||propZOff[p]>=wid) continue;
                int bx = xAlign ? baseX+sign*propZOff[p] : baseX+propXOff[p]-len/2;
                int bz = xAlign ? baseZ+propXOff[p]-len/2 : baseZ+sign*propZOff[p];
                setBlock(w, bx, baseY+1, bz, propMats[p%propMats.length]);
            }
        }

        void setBlock(World w, int x, int y, int z, Material m) {
            if (y < w.getMinHeight() || y >= w.getMaxHeight()) return;
            Block b = w.getBlockAt(x, y, z);
            b.setType(m, false);
        }
    }

    // =========================================================================
    //  EVENT LISTENER
    //  Tracks zone transitions, fires titles, ambient particles.
    //  All effects use vanilla packets — fully Geyser/Bedrock compatible.
    // =========================================================================

    static class Events implements Listener {
        private final FarLands plugin;
        private final Map<UUID, Zone> zones = new HashMap<>();

        Events(FarLands plugin) {
            this.plugin = plugin;
            startParticleTask();
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent e) {
            Location to = e.getTo();
            if (to==null) return;
            if (e.getFrom().getBlockX()==to.getBlockX()&&e.getFrom().getBlockZ()==to.getBlockZ()) return;

            Player p = e.getPlayer();
            Cfg c = plugin.cfg();
            if (!p.getWorld().getName().equals(c.worldName)) return;

            Zone nz = classifyLoc(to, c);
            Zone oz = zones.getOrDefault(p.getUniqueId(), Zone.NORMAL);
            if (nz!=oz) { zones.put(p.getUniqueId(),nz); onZoneChange(p,oz,nz,c); }
        }

        void onZoneChange(Player p, Zone from, Zone to, Cfg c) {
            if (!c.entryTitle) return;
            switch (to) {
                case GRAVEL_LANDS -> {
                    p.sendTitle(c.msgGravel, c.subGravel, 15, 50, 15);
                    p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.6f, 0.6f);
                }
                case SPIKELANDS -> {
                    p.sendTitle(c.msgSpikes, c.subSpikes, 20, 60, 20);
                    p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.4f);
                }
                case EDGE, INNER -> {
                    p.sendTitle(c.msgWall, c.subWall, 20, 80, 30);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 1.0f, 0.4f);
                    p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 0.3f);
                }
                case CORNER -> {
                    p.sendTitle("§4§lThe Corner", "§cWhere the walls meet...", 20, 80, 30);
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.7f, 0.3f);
                }
                case REPEATING_DUNGEON -> {
                    p.sendTitle(c.msgDungeon, c.subDungeon, 20, 70, 25);
                    p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.5f);
                }
                case BIOME_SCRAMBLE -> {
                    p.sendTitle(c.msgScramble, c.subScramble, 20, 80, 30);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.3f);
                }
                case ABSOLUTE_END -> {
                    p.sendTitle(c.msgEnd, c.subEnd, 20, 100, 40);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 0.2f);
                }
                case VOID -> {
                    p.sendTitle("§0§lThe Void", "§8There is nothing here.", 20, 120, 40);
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.2f);
                }
                case NORMAL -> {
                    if (from!=Zone.NORMAL) p.sendTitle("§a§lYou escaped","§7...for now.",10,40,20);
                }
            }
        }

        void startParticleTask() {
            new BukkitRunnable() {
                @Override public void run() {
                    Cfg c = plugin.cfg();
                    if (!c.particles) return;
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (!p.getWorld().getName().equals(c.worldName)) continue;
                        Zone z = zones.getOrDefault(p.getUniqueId(), Zone.NORMAL);
                        Location loc = p.getLocation();
                        switch (z) {
                            case GRAVEL_LANDS -> {
                                if (Math.random()<0.2)
                                    loc.getWorld().spawnParticle(Particle.SMOKE,
                                        loc.clone().add(rnd()*2,rnd(),rnd()*2),1,0,0,0,0.01);
                            }
                            case SPIKELANDS -> {
                                if (Math.random()<0.3)
                                    loc.getWorld().spawnParticle(Particle.SMOKE,
                                        loc.clone().add(rnd()*3,rnd()*2,rnd()*3),1,0,0,0,0.01);
                            }
                            case EDGE, INNER -> {
                                loc.getWorld().spawnParticle(Particle.PORTAL,
                                    loc.clone().add(rnd()*4,rnd()*3,rnd()*4),2,0,0,0,0.06);
                                if (Math.random()<0.15)
                                    loc.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                                        loc.clone().add(rnd()*2,0,rnd()*2),1,0,0,0,0);
                            }
                            case CORNER -> {
                                loc.getWorld().spawnParticle(Particle.PORTAL,
                                    loc.clone().add(rnd()*5,rnd()*4,rnd()*5),4,0,0,0,0.10);
                                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                                    loc.clone().add(rnd()*3,rnd()*2,rnd()*3),1,0,0,0,0.01);
                            }
                            case REPEATING_DUNGEON -> {
                                if (Math.random()<0.25)
                                    loc.getWorld().spawnParticle(Particle.ENCHANT,
                                        loc.clone().add(rnd()*3,rnd()*2,rnd()*3),3,0,0,0,0.1);
                            }
                            case BIOME_SCRAMBLE -> {
                                loc.getWorld().spawnParticle(Particle.PORTAL,
                                    loc.clone().add(rnd()*6,rnd()*5,rnd()*6),5,0,0,0,0.12);
                            }
                            case ABSOLUTE_END -> {
                                if (Math.random()<0.4)
                                    loc.getWorld().spawnParticle(Particle.CLOUD,
                                        loc.clone().add(rnd()*6,rnd()*6,rnd()*6),2,0,0,0,0.01);
                            }
                            default -> {}
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 5L);
        }

        Zone classifyLoc(Location loc, Cfg c) {
            return plugin.generator.classify(loc.getBlockX(), loc.getBlockZ(), c);
        }

        double rnd() { return (Math.random()-0.5)*2; }
    }

    // =========================================================================
    //  COMMAND  /farlands | /fl
    //  info, tp <zone>, reload
    // =========================================================================

    static class Cmd implements CommandExecutor, TabCompleter {
        private final FarLands plugin;
        Cmd(FarLands plugin) { this.plugin = plugin; }

        static final List<String> ZONES = List.of(
            "gravel","spikes","edge","corner","inner","dungeon","scramble","end","void");

        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd,
                                 @NotNull String lbl, @NotNull String[] args) {
            Cfg c = plugin.cfg();
            if (args.length==0) { help(s,lbl); return true; }

            switch (args[0].toLowerCase()) {

                case "info" -> {
                    if (!(s instanceof Player p)) { s.sendMessage("§cPlayers only."); return true; }
                    int ax=Math.abs(p.getLocation().getBlockX());
                    int az=Math.abs(p.getLocation().getBlockZ());
                    int dist=Math.max(ax,az);
                    Zone z = plugin.generator.classify(p.getLocation().getBlockX(),
                                                       p.getLocation().getBlockZ(), c);
                    p.sendMessage("§6§l--- Far Lands Info ---");
                    p.sendMessage("§eZone: §f"+zoneLabel(z));
                    p.sendMessage("§eDistance from spawn: §f"+dist);
                    p.sendMessage("§eGravel Lands:  §f±"+c.gravelStart);
                    p.sendMessage("§eSpikelands:    §f±"+c.spikesStart);
                    p.sendMessage("§eEdge FL Wall:  §f±"+c.wallStart);
                    p.sendMessage("§eRepeating Dgn: §f±"+c.dungeonStart);
                    p.sendMessage("§eBiome Scramble:§f±"+c.scrambleStart);
                    p.sendMessage("§eAbsolute End:  §f±"+c.absoluteStart);
                    p.sendMessage("§eVoid:          §f±"+c.voidStart);
                }

                case "tp","teleport" -> {
                    if (!s.hasPermission("farlands.teleport")){s.sendMessage("§cNo permission.");return true;}
                    if (!(s instanceof Player p)){s.sendMessage("§cPlayers only.");return true;}
                    World w = plugin.getServer().getWorld(c.worldName);
                    if (w==null){s.sendMessage("§cWorld '"+c.worldName+"' not found.");return true;}

                    String target = args.length>1 ? args[1].toLowerCase() : "edge";
                    Location dest = switch (target) {
                        case "gravel"   -> new Location(w, c.gravelStart+100,    100, 0);
                        case "spikes"   -> new Location(w, c.spikesStart+500,    130, 0);
                        case "corner"   -> new Location(w, c.wallStart+8,        180, c.wallStart+8);
                        case "inner"    -> new Location(w, c.wallStart+c.innerDepth/2, 80, 0);
                        case "dungeon"  -> new Location(w, c.dungeonStart+200,    80, 0);
                        case "scramble" -> new Location(w, c.scrambleStart+200,   90, 0);
                        case "end"      -> new Location(w, c.absoluteStart+200,   80, 0);
                        case "void"     -> new Location(w, c.voidStart+100,       80, 0);
                        default         -> new Location(w, c.wallStart+8,        180, 0); // edge
                    };
                    dest.setY(Math.max(dest.getY(), w.getHighestBlockYAt(dest.getBlockX(), dest.getBlockZ())+2));
                    p.teleport(dest);
                    p.sendMessage("§aTeleported to §6"+cap(target)+"§a. ("+dest.getBlockX()+", "+dest.getBlockY()+", "+dest.getBlockZ()+")");
                }

                case "reload" -> {
                    if (!s.hasPermission("farlands.admin")){s.sendMessage("§cNo permission.");return true;}
                    c.reload();
                    s.sendMessage("§aFarLands config reloaded. Wall: ±"+c.wallStart+", Void: ±"+c.voidStart);
                }

                default -> help(s,lbl);
            }
            return true;
        }

        void help(CommandSender s, String lbl) {
            s.sendMessage("§6§l--- FarLands ---");
            s.sendMessage("§e/"+lbl+" info");
            s.sendMessage("§e/"+lbl+" tp <"+String.join("|",ZONES)+">");
            s.sendMessage("§e/"+lbl+" reload §7(op)");
        }

        String zoneLabel(Zone z) { return switch(z) {
            case NORMAL -> "§aNormal"; case GRAVEL_LANDS -> "§7Gravel Lands";
            case SPIKELANDS -> "§6Spikelands"; case EDGE -> "§4Edge Far Lands";
            case CORNER -> "§4Corner Far Lands"; case INNER -> "§cInner Far Lands";
            case REPEATING_DUNGEON -> "§dRepeating Dungeon Zone";
            case BIOME_SCRAMBLE -> "§bBiome Scramble"; case ABSOLUTE_END -> "§8Absolute End";
            case VOID -> "§0The Void";
        };}

        String cap(String s){return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender s,@NotNull Command c,
                                                    @NotNull String a,@NotNull String[] args) {
            if (args.length==1) return List.of("info","tp","reload").stream()
                .filter(x->x.startsWith(args[0].toLowerCase())).toList();
            if (args.length==2&&args[0].equalsIgnoreCase("tp"))
                return ZONES.stream().filter(x->x.startsWith(args[1].toLowerCase())).toList();
            return List.of();
        }
    }
}
