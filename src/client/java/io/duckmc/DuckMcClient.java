package io.duckmc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.duckmc.fabric.PvpWorldRenderer;
import io.duckmc.hud.PvpHudRenderer;
import io.duckmc.ui.screen.AdvantagesScreen;
import io.duckmc.ui.screen.KeybindScreen;
import io.duckmc.ui.screen.PvpHudEditorScreen;
import io.duckmc.ui.screen.PvpScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public final class DuckMcClient implements ClientModInitializer {
   private static DuckMcRuntime runtime;

   public void onInitializeClient() {
      Minecraft client = Minecraft.getInstance();
      runtime = new DuckMcRuntime(client, FabricLoader.getInstance().getConfigDir().resolve("duckmc"));
      runtime.input().register(runtime);
      runtime.pvp().register();
      runtime.advantages().register();
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, access) -> dispatcher.register(
               (LiteralArgumentBuilder)ClientCommandManager.literal("duckmc")
                  .then(
                     ClientCommandManager.literal("benchmark")
                        .then(
                           ClientCommandManager.argument("seconds", IntegerArgumentType.integer(1, 600))
                              .executes(
                                 context -> {
                                    int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                    boolean started = runtime.performance().startBenchmark(seconds);
                                    ((FabricClientCommandSource)context.getSource())
                                       .sendFeedback(
                                          Component.literal(
                                             started
                                                ? "[DuckMC] Benchmark running: " + runtime.performance().rendererName()
                                                : "[DuckMC] A benchmark is already running."
                                          )
                                       );
                                    return 1;
                                 }
                              )
                        )
                  )
            )
         );
      PvpWorldRenderer.register(runtime);
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)ignored -> {
               Screen screen = Minecraft.getInstance().screen;
               runtime.settings()
                  .holdAutosave(
                     screen instanceof PvpScreen
                        || screen instanceof AdvantagesScreen
                        || screen instanceof KeybindScreen
                        || screen instanceof PvpHudEditorScreen
                  );
               runtime.settings().tick();
               runtime.input().applySettings(runtime.config().activeSettings());
            }
         );
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)ignored -> runtime.settings().flush());
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)ignored -> runtime.closeContent());
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("duckmc", "owned_caches");
         }

         public void onResourceManagerReload(ResourceManager manager) {
            DuckMcClient.runtime.performance().clearCaches();
         }
      });
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("duckmc", "status"), runtime.hudRenderer());
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("duckmc", "pvp"), new PvpHudRenderer(runtime));
   }

   public static DuckMcRuntime runtime() {
      if (runtime == null) {
         throw new IllegalStateException("DuckMC has not finished client initialization");
      } else {
         return runtime;
      }
   }

   public static DuckMcRuntime runtimeOrNull() {
      return runtime;
   }
}
