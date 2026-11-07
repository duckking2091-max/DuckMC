package io.duckmc;

import com.google.gson.JsonObject;
import io.duckmc.config.ConfigDocument;
import io.duckmc.config.ConfigStore;
import io.duckmc.config.SettingsSession;
import io.duckmc.config.ConfigStore.LoadResult;
import io.duckmc.fabric.AdvantagesAdapter;
import io.duckmc.fabric.ClientInputAdapter;
import io.duckmc.fabric.ContentAdapter;
import io.duckmc.fabric.PerformanceAdapter;
import io.duckmc.fabric.PvpAdapter;
import io.duckmc.hud.HudRenderer;
import io.duckmc.modules.ModuleRegistry;
import io.duckmc.ui.model.HudPlacement;
import io.duckmc.ui.model.Theme;
import io.duckmc.ui.screen.ScreenRouter;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class DuckMcRuntime {
   private final ConfigStore configStore;
   private final SettingsSession settings;
   private ConfigDocument themedDocument;
   private Theme theme;
   private final ModuleRegistry moduleRegistry;
   private final ScreenRouter router;
   private final ClientInputAdapter input;
   private final HudRenderer hudRenderer;
   private final String notice;
   private final PerformanceAdapter performance;
   private final PvpAdapter pvp;
   private final AdvantagesAdapter advantages;
   private final Path contentConfigRoot;
   private final Minecraft contentClient;
   private ContentAdapter content;

   public DuckMcRuntime(Minecraft client, Path configRoot) {
      Objects.requireNonNull(client, "client");
      this.contentClient = client;
      this.contentConfigRoot = configRoot;
      this.configStore = new ConfigStore(Objects.requireNonNull(configRoot, "configRoot"));
      ConfigDocument loaded = ConfigDocument.defaults();
      String loadNotice = "";
      boolean readOnly = false;

      try {
         LoadResult result = this.configStore.load();
         loaded = result.document();
         readOnly = result.readOnly();
         loadNotice = result.notice();
         if (result.readOnly() && loadNotice.isBlank()) {
            loadNotice = "A newer DuckMC configuration was found. Settings are read-only.";
         }
      } catch (RuntimeException exception) {
         loadNotice = "DuckMC could not load its configuration; safe defaults are active (" + concise(exception) + ").";
      }

      Theme loadedTheme;
      try {
         loadedTheme = Theme.from(loaded.activeSettings());
      } catch (RuntimeException exception) {
         loaded = ConfigDocument.defaults();
         loadedTheme = Theme.from(loaded.activeSettings());
         loadNotice = "DuckMC rejected invalid theme settings; safe defaults are active (" + concise(exception) + ").";
      }

      this.settings = new SettingsSession(loaded, this.configStore::save, System::nanoTime, readOnly);
      this.themedDocument = loaded;
      this.theme = loadedTheme;
      this.notice = loadNotice;
      this.moduleRegistry = new ModuleRegistry();
      this.router = new ScreenRouter(client, this);
      this.input = new ClientInputAdapter(this.router, loaded.activeSettings());
      this.hudRenderer = new HudRenderer(this);
      this.performance = new PerformanceAdapter(this, client);
      this.pvp = new PvpAdapter(this, client);
      this.advantages = new AdvantagesAdapter(this, client);
   }

   public ConfigStore configStore() {
      return this.configStore;
   }

   public ContentAdapter content() {
      if (this.content == null) {
         this.content = new ContentAdapter(this.contentClient, this.contentConfigRoot);
      }

      return this.content;
   }

   public void closeContent() {
      if (this.content != null) {
         this.content.close();
      }
   }

   public PerformanceAdapter performance() {
      return this.performance;
   }

   public PvpAdapter pvp() {
      return this.pvp;
   }

   public AdvantagesAdapter advantages() {
      return this.advantages;
   }

   public ConfigDocument config() {
      return this.settings.document();
   }

   public SettingsSession settings() {
      return this.settings;
   }

   public Theme theme() {
      if (this.themedDocument != this.settings.document()) {
         this.themedDocument = this.settings.document();
         this.theme = Theme.from(this.themedDocument.activeSettings());
      }

      return this.theme;
   }

   public ModuleRegistry moduleRegistry() {
      return this.moduleRegistry;
   }

   public ScreenRouter router() {
      return this.router;
   }

   public ClientInputAdapter input() {
      return this.input;
   }

   public HudRenderer hudRenderer() {
      return this.hudRenderer;
   }

   public String notice() {
      return this.notice;
   }

   public HudPlacement hudPlacement() {
      JsonObject settings = this.config().activeSettings();
      return new HudPlacement(settings.get("hud.x").getAsDouble(), settings.get("hud.y").getAsDouble(), settings.get("hud.scale").getAsDouble());
   }

   public boolean hudVisible() {
      return this.config().activeSettings().get("hud.visible").getAsBoolean();
   }

   private static String concise(RuntimeException exception) {
      String detail = exception.getMessage();
      if (detail == null || detail.isBlank()) {
         detail = exception.getClass().getSimpleName();
      }

      detail = detail.replaceAll("\\s+", " ").trim();
      return detail.length() <= 96 ? detail : detail.substring(0, 95) + "…";
   }
}
