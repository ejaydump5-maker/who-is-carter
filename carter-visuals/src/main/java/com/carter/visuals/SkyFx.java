package com.carter.visuals;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

public final class SkyFx {
    private SkyFx() {}

    /** 0..1, strongest at sunrise and sunset (wide golden hour). */
    public static float glow(ClientWorld w, float delta) {
        double sunH = Math.cos(w.getSkyAngle(delta) * 2.0 * Math.PI);
        return (float) Math.exp(-(sunH * sunH) / (2.0 * 0.28 * 0.28));
    }

    public static Vec3d apply(ClientWorld w, Vec3d vanilla, float delta, float strength) {
        float rain = w.getRainGradient(delta);
        float k = strength * glow(w, delta) * (1.0f - 0.5f * rain);
        if (k <= 0.001f) return vanilla;
        double bright = Math.max(vanilla.x, Math.max(vanilla.y, vanilla.z));
        double s = Math.min(1.0, bright * 1.5);
        Vec3d warm = new Vec3d(0.93 * s, 0.43 * s, 0.44 * s);
        return vanilla.lerp(warm, k);
    }

    /** Colour of the sky near the horizon, used for grazing-angle reflections. */
    public static Vec3d horizon(ClientWorld w, Vec3d sky, float delta) {
        float g = glow(w, delta) * Math.max(0.3f, Config.get().sky);
        double b = Math.min(1.0, Math.max(sky.x, Math.max(sky.y, sky.z)) * 1.6);
        Vec3d pale = sky.multiply(0.6).add(0.4 * b, 0.4 * b, 0.4 * b);
        Vec3d warm = new Vec3d(1.0 * b, 0.62 * b, 0.40 * b);
        return pale.lerp(warm, g);
    }
}
