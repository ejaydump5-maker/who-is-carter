package com.carter.visuals;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

public class CarterVisuals implements ClientModInitializer {
    public static float wetness = 0f;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.carter.visuals.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.carter.visuals"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                Config.get().enabled = !Config.get().enabled;
                Config.changed();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Carter Visuals: "
                            + (Config.get().enabled ? "ON" : "OFF")), true);
                }
            }
            if (client.world == null) { wetness = 0f; return; }
            float rain = client.world.getRainGradient(1.0f);
            if (rain > wetness) wetness = Math.min(rain, wetness + 0.01f);
            else wetness = Math.max(rain, wetness - 0.0008f); // dries slowly
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldFx::render);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("carter");
            floatCmd(root, "sky", (c, v) -> c.sky = v);
            floatCmd(root, "puddles", (c, v) -> c.puddles = v);
            floatCmd(root, "ripples", (c, v) -> c.ripples = v);
            floatCmd(root, "reflection", (c, v) -> c.reflection = v);
            floatCmd(root, "streaks", (c, v) -> c.streaks = v);
            root.then(ClientCommandManager.literal("toggle").executes(ctx -> {
                Config.get().enabled = !Config.get().enabled;
                Config.changed();
                ctx.getSource().sendFeedback(Text.literal("enabled = " + Config.get().enabled));
                return 1;
            }));
            root.then(ClientCommandManager.literal("quality").then(
                    ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 2)).executes(ctx -> {
                        Config.get().quality = IntegerArgumentType.getInteger(ctx, "level");
                        Config.changed();
                        ctx.getSource().sendFeedback(Text.literal("quality = " + Config.get().quality + " (0 low, 1 med, 2 high)"));
                        return 1;
                    })));
            dispatcher.register(root);
        });
    }

    private static void floatCmd(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
                                 BiConsumer<Config, Float> setter) {
        root.then(ClientCommandManager.literal(name).then(
                ClientCommandManager.argument("value", FloatArgumentType.floatArg(0f, 1f)).executes(ctx -> {
                    float v = FloatArgumentType.getFloat(ctx, "value");
                    setter.accept(Config.get(), v);
                    Config.changed();
                    ctx.getSource().sendFeedback(Text.literal(name + " = " + v));
                    return 1;
                })));
    }
}
