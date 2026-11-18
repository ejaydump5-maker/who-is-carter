package com.carter.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();
    public static int version = 0;

    public boolean enabled = true;
    public float sky = 0.8f;
    public float puddles = 0.8f;
    public float ripples = 0.8f;
    public float reflection = 0.8f;
    public float streaks = 0.6f;
    public int quality = 1; // 0 low, 1 medium, 2 high

    public static Config get() { return instance; }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("carter-visuals.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file()), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception ignored) {}
        instance.quality = Math.max(0, Math.min(2, instance.quality));
        save();
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(instance)); } catch (IOException ignored) {}
    }

    public static void changed() { version++; save(); }

    public int radius()      { return new int[]{12, 20, 30}[quality]; }
    public int maxPuddles()  { return new int[]{250, 550, 1000}[quality]; }
    public int maxRings()    { return new int[]{30, 80, 160}[quality]; }
    public float ringRange() { return new float[]{8f, 13f, 18f}[quality]; }
    public int ringSegs()    { return new int[]{6, 8, 12}[quality]; }
    public int streakCount() { return new int[]{120, 260, 450}[quality]; }
}
