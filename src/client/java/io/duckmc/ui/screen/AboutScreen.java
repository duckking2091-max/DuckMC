package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.SectionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class AboutScreen extends DuckUiScreen {
   private static final int TOP = 34;
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private final List<AboutScreen.Line> lines = new ArrayList<>();
   private int scroll;
   private int contentHeight;

   public AboutScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("About DuckMC"), runtime);
      this.parent = parent;
      this.runtime = runtime;
   }

   private static String version(String id) {
      return FabricLoader.getInstance().getModContainer(id).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("Unknown");
   }

   private static String requirement(ModContainer mod, String id) {
      return mod.getMetadata()
         .getDependencies()
         .stream()
         .filter(d -> d.getKind() == Kind.DEPENDS && d.getModId().equals(id))
         .flatMap(d -> d.getVersionRequirements().stream())
         .map(Object::toString)
         .collect(Collectors.joining(", "));
   }

   public List<String> lines() {
      return this.lines.stream().map(AboutScreen.Line::text).toList();
   }

   private void section(String heading, String... body) {
      this.lines.add(new AboutScreen.Line(heading, true));

      for (String entry : body) {
         this.lines.add(new AboutScreen.Line(entry, false));
      }
   }

   private void collect() {
      this.lines.clear();
      ModContainer mod = (ModContainer)FabricLoader.getInstance().getModContainer("duckmc").orElseThrow();
      ModMetadata meta = mod.getMetadata();
      this.section(
         "Version",
         "DuckMC " + meta.getVersion().getFriendlyString(),
         "Minecraft " + requirement(mod, "minecraft"),
         "Fabric Loader " + requirement(mod, "fabricloader") + " (running " + version("fabricloader") + ")",
         "Fabric API " + requirement(mod, "fabric-api")
      );
      this.section("About", meta.getDescription());
      this.section(
         "Features",
         "Performance: frame pacing, culling, entity and cache controls.",
         "PvP: visual feedback, HUD status elements and first-person tweaks.",
         "Worlds & Resource Packs: browse, install and manage content.",
         "Advantages: opt-in assists. Use them only where server rules allow."
      );
      String credits = meta.getAuthors().stream().<CharSequence>map(Person::getName).collect(Collectors.joining(", "));
      if (!credits.isBlank()) {
         this.section("Credits", credits);
      }

      ArrayList<String> links = new ArrayList<>();
      meta.getContact().asMap().forEach((kind, url) -> links.add(kind + ": " + url));
      if (!links.isEmpty()) {
         this.section("Links", links.toArray(String[]::new));
      }

      this.section("License", String.join(", ", meta.getLicense()));
   }

   protected void init() {
      this.prepareLayout();
      this.collect();
      int textWidth = this.width - 32;
      this.contentHeight = 0;

      for (AboutScreen.Line line : this.lines) {
         this.contentHeight = this.contentHeight + (line.heading() ? 22 : this.font.split(this.text(line.text()), textWidth).size() * 10 + 2);
      }

      this.addRenderableWidget(
         SectionButton.create(this.text("Back"), b -> this.onClose(), SectionPalette.WHITE_GOLD).dimensions(8, this.height - 28, 80, 20).build()
      );
      this.addSectionSidebar();
   }

   private Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   private int maxScroll() {
      return Math.max(0, this.contentHeight - (this.height - 34 - 34));
   }

   @Override
   protected boolean scrollContents(double mx, double my, double horizontal, double vertical) {
      this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)(vertical * 18.0)));
      return true;
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mx, int my, float delta) {
      int accent = (int)this.runtime.theme().accent();
      draw.drawString(this.font, this.text("About DuckMC"), 12, 14, accent);
      draw.fill(12, 27, this.width - 12, 28, 1627389951);
      draw.enableScissor(8, 34, this.width - 8, this.height - 34);
      int y = 34 - this.scroll;
      int textWidth = this.width - 32;

      for (AboutScreen.Line line : this.lines) {
         if (line.heading()) {
            y += 6;
            draw.drawString(this.font, this.text(line.text()), 14, y, accent);
            draw.fill(14, y + 11, this.width - 14, y + 12, 905969663);
            y += 16;
         } else {
            for (FormattedCharSequence part : this.font.split(this.text(line.text()), textWidth)) {
               draw.drawString(this.font, part, 16, y, -2826520, false);
               y += 10;
            }

            y += 2;
         }
      }

      draw.disableScissor();
      super.renderContents(draw, mx, my, delta);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   @Environment(EnvType.CLIENT)
   private record Line(String text, boolean heading) {
   }
}
