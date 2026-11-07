package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.ScreenRoute;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class ScreenRouter {
   private final Minecraft client;
   private final DuckMcRuntime runtime;
   private volatile ScreenRoute route = ScreenRoute.MAIN;
   private Screen previous;

   public ScreenRouter(Minecraft client, DuckMcRuntime runtime) {
      this.client = Objects.requireNonNull(client, "client");
      this.runtime = Objects.requireNonNull(runtime, "runtime");
   }

   public void open(ScreenRoute destination) {
      Objects.requireNonNull(destination, "destination");
      Runnable change = () -> {
         if (!(this.client.screen instanceof DuckScreen)
            && !(this.client.screen instanceof PerformanceScreen)
            && !(this.client.screen instanceof PvpScreen)
            && !(this.client.screen instanceof AdvantagesScreen)
            && !(this.client.screen instanceof ContentPage)) {
            this.previous = this.client.screen;
         }

         this.route = destination;
         this.client.setScreen(this.screenFor(destination));
      };
      if (this.client.isSameThread()) {
         change.run();
      } else {
         this.client.execute(change);
      }
   }

   public Screen screenFor(ScreenRoute destination) {
      if (destination == ScreenRoute.PERFORMANCE) {
         return new PerformanceScreen(this.runtime);
      } else if (destination == ScreenRoute.PVP) {
         return new PvpScreen(this.runtime);
      } else if (destination == ScreenRoute.ADVANTAGES) {
         return new AdvantagesScreen(this.runtime);
      } else {
         return destination == ScreenRoute.CONTENT
            ? new ContentScreen(this.runtime)
            : new DuckScreen(this, this.runtime, Objects.requireNonNull(destination, "destination"));
      }
   }

   public ScreenRoute route() {
      return this.route;
   }

   public void close() {
      Runnable change = () -> {
         Screen target = this.previous;
         this.previous = null;
         this.client.setScreen(target);
      };
      if (this.client.isSameThread()) {
         change.run();
      } else {
         this.client.execute(change);
      }
   }
}
