package com.carter.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Wet-world effects drawn as cheap translucent geometry (no extra render passes):
 * sky-tinted puddles with grazing-angle reflection, ripple rings, and streak rain.
 * Puddle positions are cached and only rebuilt every ~2 seconds.
 */
public final class WorldFx {
    private WorldFx() {}

    private static final int CAP = 1024;
    private static final int[] px = new int[CAP], py = new int[CAP], pz = new int[CAP];
    private static final float[] psize = new float[CAP], pseed = new float[CAP];
    private static int count = 0, lastBuild = -9999, cacheX, cacheZ, cacheVersion = -1;

    private static final int SCAP = 512;
    private static final float[] sHx = new float[SCAP], sHz = new float[SCAP], sPh = new float[SCAP], sLen = new float[SCAP];
    private static final int[] sCx = new int[SCAP], sCz = new int[SCAP], sTop = new int[SCAP];

    static {
        Random r = new Random(1337);
        for (int i = 0; i < SCAP; i++) {
            sHx[i] = r.nextFloat(); sHz[i] = r.nextFloat(); sPh[i] = r.nextFloat();
            sLen[i] = 0.8f + r.nextFloat() * 0.7f;
            sCx[i] = Integer.MIN_VALUE;
        }
    }

    // ---------- noise ----------
    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    private static float noise(float x, float z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), d = hash(xi + 1, zi + 1);
        return MathHelper.lerp(fz, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
    }

    // ---------- puddle cache ----------
    private static void rebuild(ClientWorld w, Vec3d cam, Config c) {
        count = 0;
        BlockPos center = BlockPos.ofFloored(cam);
        cacheX = center.getX();
        cacheZ = center.getZ();
        cacheVersion = Config.version;
        lastBuild = (int) w.getTime();

        if (!w.getBiome(center).value().hasPrecipitation()) return;

        int R = c.radius(), cap = Math.min(CAP, c.maxPuddles());
        float thr = 0.78f - 0.22f * c.puddles;
        BlockPos.Mutable below = new BlockPos.Mutable();
        BlockPos.Mutable above = new BlockPos.Mutable();

        for (int x = cacheX - R; x <= cacheX + R && count < cap; x++) {
            for (int z = cacheZ - R; z <= cacheZ + R && count < cap; z++) {
                int dx = x - cacheX, dz = z - cacheZ;
                if (dx * dx + dz * dz > R * R) continue;

                float n = noise(x * 0.11f, z * 0.11f) * 0.65f + noise(x * 0.31f + 17f, z * 0.31f - 9f) * 0.35f;
                if (n < thr) continue;
                if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                below.set(x, y - 1, z);
                above.set(x, y, z);
                BlockState bs = w.getBlockState(below);
                if (!bs.isSideSolidFullSquare(w, below, Direction.UP)) continue;
                if (bs.isIn(BlockTags.LEAVES) || bs.isOf(Blocks.SNOW_BLOCK)) continue;
                BlockState up = w.getBlockState(above);
                if (up.isOf(Blocks.SNOW) || !up.getFluidState().isEmpty()) continue;
                if (!up.getCollisionShape(w, above).isEmpty()) continue;

                float t = MathHelper.clamp((n - thr) / 0.12f, 0f, 1f);
                t = t * t * (3 - 2 * t);
                px[count] = x; py[count] = y; pz[count] = z;
                psize[count] = t;
                pseed[count] = hash(x * 7 + 3, z * 11 + 5);
                count++;
            }
        }
    }

    // ---------- drawing helpers ----------
    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float y,
                             float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                             float r, float g, float b, float a) {
        v(vc, m, x1, y, z1, r, g, b, a);
        v(vc, m, x2, y, z2, r, g, b, a);
        v(vc, m, x3, y, z3, r, g, b, a);
        v(vc, m, x4, y, z4, r, g, b, a);
    }

    // ---------- main render ----------
    public static void render(WorldRenderContext ctx) {
        Config c = Config.get();
        ClientWorld w = ctx.world();
        if (!c.enabled || w == null || w.getRegistryKey() != World.OVERWORLD) return;
        if (ctx.consumers() == null || ctx.matrixStack() == null) return;

        float delta = ctx.tickCounter().getTickDelta(false);
        float rain = w.getRainGradient(delta);
        float wet = CarterVisuals.wetness;
        if (wet < 0.03f && rain < 0.05f) return;

        Vec3d cam = ctx.camera().getPos();
        Vec3d sky = w.getSkyColor(cam, delta);
        Vec3d hor = SkyFx.horizon(w, sky, delta);
        float time = (w.getTime() + delta) / 20f;

        int tick = (int) w.getTime();
        if (cacheVersion != Config.version || tick - lastBuild >= 40
                || Math.abs(cam.x - cacheX) > 6 || Math.abs(cam.z - cacheZ) > 6) {
            rebuild(w, cam, c);
        }

        VertexConsumerProvider cp = ctx.consumers();
        VertexConsumer vc = cp.getBuffer(RenderLayer.getDebugQuads());
        Matrix4f m = ctx.matrixStack().peek().getPositionMatrix();

        float R = c.radius();
        float wetA = MathHelper.clamp(wet, 0f, 1f);

        // ----- puddles with fake sky reflection -----
        if (c.puddles > 0f && wetA > 0.03f) {
            for (int i = 0; i < count; i++) {
                float cx = (float) (px[i] + 0.5 - cam.x);
                float cz = (float) (pz[i] + 0.5 - cam.z);
                float cy = (float) (py[i] + 0.015 - cam.y);
                float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (dist > R) continue;
                float fade = MathHelper.clamp((R - dist) / (R * 0.35f), 0f, 1f);

                float cosT = MathHelper.clamp(-cy / dist, 0f, 1f);
                float f = (float) Math.pow(1f - cosT, 3);
                float r = (float) (sky.x * 0.45 * (1 - f) + hor.x * f);
                float g = (float) (sky.y * 0.45 * (1 - f) + hor.y * f);
                float b = (float) (sky.z * 0.45 * (1 - f) + hor.z * f);
                float a = (0.25f + 0.55f * f) * c.reflection * wetA * fade * (0.5f + 0.5f * psize[i]);
                a = Math.min(a, 0.88f);
                if (a < 0.01f) continue;

                float size = psize[i];
                float half = 0.5f * (0.5f + 0.5f * size);
                float ch = half * (1f - size) * 0.8f;
                float[] ox = {-half + ch, half - ch, half, half, half - ch, -half + ch, -half, -half};
                float[] oz = {-half, -half, -half + ch, half - ch, half, half, half - ch, -half + ch};
                for (int q = 0; q < 4; q++) {
                    int i0 = q * 2, i1 = q * 2 + 1, i2 = (q * 2 + 2) % 8;
                    quad(vc, m, cy, cx, cz,
                            cx + ox[i0], cz + oz[i0], cx + ox[i1], cz + oz[i1], cx + ox[i2], cz + oz[i2],
                            r, g, b, a);
                }
            }
        }

        // ----- ripple rings -----
        if (c.ripples > 0f && wetA > 0.03f) {
            int rings = 0, maxRings = c.maxRings(), segs = c.ringSegs();
            float range = c.ringRange();
            float density = (0.2f + 0.75f * c.ripples) * wetA;
            float rr = (float) (sky.x * 0.3 + 0.7), rg = (float) (sky.y * 0.3 + 0.7), rb = (float) (sky.z * 0.3 + 0.7);
            for (int i = 0; i < count && rings < maxRings; i++) {
                if (psize[i] < 0.6f) continue;
                float cx = (float) (px[i] + 0.5 - cam.x);
                float cz = (float) (pz[i] + 0.5 - cam.z);
                float cy = (float) (py[i] + 0.03 - cam.y);
                float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (dist > range) continue;

                float period = 0.9f + pseed[i] * 0.8f;
                float t = time / period + pseed[i] * 7f;
                int cyc = (int) Math.floor(t);
                float phase = t - cyc;
                if (hash(px[i] * 31 + cyc, pz[i] * 17 + (int) (pseed[i] * 1000)) > density) continue;
                rings++;

                float ox = (hash(cyc * 13 + px[i], pz[i]) - 0.5f) * 0.5f;
                float oz = (hash(px[i], cyc * 29 + pz[i]) - 0.5f) * 0.5f;
                float outer = 0.06f + phase * 0.40f;
                float inner = Math.max(0f, outer - 0.035f);
                float a = (float) Math.pow(1f - phase, 1.4) * 0.6f * c.ripples
                        * MathHelper.clamp((range - dist) / (range * 0.4f), 0f, 1f);
                if (a < 0.02f) continue;
                for (int s = 0; s < segs; s++) {
                    double a0 = 2 * Math.PI * s / segs, a1 = 2 * Math.PI * (s + 1) / segs;
                    float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                    float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                    quad(vc, m, cy,
                            cx + ox + c0 * inner, cz + oz + s0 * inner,
                            cx + ox + c0 * outer, cz + oz + s0 * outer,
                            cx + ox + c1 * outer, cz + oz + s1 * outer,
                            cx + ox + c1 * inner, cz + oz + s1 * inner,
                            rr, rg, rb, a);
                }
            }
        }

        // ----- streak rain -----
        if (c.streaks > 0f && rain > 0.05f) {
            int n = Math.min(SCAP, c.streakCount());
            final float S = 14f, D = S * 2f, H = 20f;
            float sr = (float) (sky.x * 0.25 + 0.75), sg = (float) (sky.y * 0.25 + 0.75), sb = (float) (sky.z * 0.25 + 0.75);
            for (int i = 0; i < n; i++) {
                double relX = (((sHx[i] * D - cam.x) % D) + D) % D - S;
                double relZ = (((sHz[i] * D - cam.z) % D) + D) % D - S;
                float d = (float) Math.sqrt(relX * relX + relZ * relZ);
                if (d < 1.5f || d > S) continue;

                int cellX = (int) Math.floor(cam.x + relX), cellZ = (int) Math.floor(cam.z + relZ);
                if (cellX != sCx[i] || cellZ != sCz[i]) {
                    sCx[i] = cellX; sCz[i] = cellZ;
                    sTop[i] = w.getChunkManager().isChunkLoaded(cellX >> 4, cellZ >> 4)
                            ? w.getTopY(Heightmap.Type.MOTION_BLOCKING, cellX, cellZ) : Integer.MIN_VALUE;
                }
                if (sTop[i] == Integer.MIN_VALUE) continue;

                float phase = (time * 0.7f + sPh[i]) % 1f;
                float y = sTop[i] + H * (1f - phase);
                float by = (float) (y - cam.y);
                if (by < -20f || by > 16f) continue;

                float fade = MathHelper.clamp((S - d) / (S * 0.4f), 0f, 1f);
                float a = 0.30f * c.streaks * rain * fade;
                float len = sLen[i];
                float bx = (float) relX, bz = (float) relZ;
                float hx = -bz / d * 0.014f, hz = bx / d * 0.014f;
                float tx = bx + 0.10f * len, tz = bz + 0.04f * len, ty = by + len;
                v(vc, m, bx - hx, by, bz - hz, sr, sg, sb, a);
                v(vc, m, bx + hx, by, bz + hz, sr, sg, sb, a);
                v(vc, m, tx + hx, ty, tz + hz, sr, sg, sb, a * 0.15f);
                v(vc, m, tx - hx, ty, tz - hz, sr, sg, sb, a * 0.15f);
            }
        }

        if (cp instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getDebugQuads());
        }
    }
}
