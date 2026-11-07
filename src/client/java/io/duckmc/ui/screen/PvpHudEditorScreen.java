package io.duckmc.ui.screen;

import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.config.SettingsSchema;
import io.duckmc.config.SettingsSchema.Definition;
import io.duckmc.hud.PvpHudRenderer;
import io.duckmc.pvp.PvpHudLayout;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.pvp.PvpHudLayout.Rect;
import io.duckmc.ui.model.ColorPresets;
import io.duckmc.ui.model.HudPlacement;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.ColorPresets.Preset;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.widget.ColorSwatch;
import io.duckmc.ui.widget.NeonSlider;
import io.duckmc.ui.widget.NeonToggle;
import io.duckmc.ui.widget.SectionButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class PvpHudEditorScreen extends Screen {
   private static final int PANEL_WIDTH = 150;
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private final String id;
   private final String prefix;
   private final PvpHudRenderer renderer;
   private boolean dragging;
   private double offsetX;
   private double offsetY;
   private boolean panelLeft;

   public PvpHudEditorScreen(Screen parent, DuckMcRuntime runtime, String id) {
      super(Component.literal("HUD Element"));
      if (!PvpPolicy.CARDS.contains(id)) {
         throw new IllegalArgumentException("Unknown card");
      }

      this.parent = parent;
      this.runtime = runtime;
      this.id = id;
      this.prefix = "hud." + id + ".";
      this.renderer = new PvpHudRenderer(runtime);
   }

   private int panelX() {
      return this.panelLeft ? 6 : this.width - 150 - 6;
   }

   private boolean colored() {
      return !this.id.equals("armor_status") && !this.id.equals("totem_status");
   }

   private boolean scaledText() {
      return this.colored();
   }

   private boolean backgrounded() {
      return true;
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   private <T extends AbstractWidget> T editable(T widget) {
      widget.active = !this.runtime.settings().readOnly();
      return (T)this.addRenderableWidget(widget);
   }

   private SectionButton button(String label, int x, int y, int w, Runnable action) {
      return SectionButton.create(Component.literal(label), b -> action.run(), SectionPalette.WHITE_GOLD).dimensions(x, y, w, 18).build();
   }

   private int slider(String key, String label, double min, double max, int x, int y) {
      this.addRenderableWidget(new PvpHudEditorScreen.LabelWidget(x, y, 134, label));
      this.editable(
         new NeonSlider(
            Component.literal(label),
            this.setting(key),
            min,
            max,
            -15058,
            x,
            y + 10,
            134,
            value -> Math.round(value * 100.0) + "%",
            value -> this.runtime.settings().preview(this.prefix + key, new JsonPrimitive(value))
         )
      );
      return y + 27;
   }

   private double setting(String key) {
      return this.runtime.config().activeSettings().get(this.prefix + key).getAsDouble();
   }

   private boolean flag(String key) {
      return this.runtime.config().activeSettings().get(this.prefix + key).getAsBoolean();
   }

   private int toggle(String key, String label, int x, int y) {
      this.editable(
         new NeonToggle(
            this.prefix + key,
            Component.literal(label),
            Component.literal(label + ": " + (this.flag(key) ? "On" : "Off")),
            "",
            this.flag(key),
            -15058,
            x,
            y,
            134,
            134,
            b -> {
               this.runtime.settings().preview(this.prefix + key, new JsonPrimitive(!this.flag(key)));
               this.rebuild();
            }
         )
      );
      return y + 22;
   }

   protected void init() {
      Rect at = this.renderer.bounds(this.id, this.width, this.height);
      this.panelLeft = at.x() + at.width() / 2 > this.width / 2;
      int x = this.panelX() + 8;
      int y = 22;
      y = this.slider("scale", "Size", 0.5, this.id.equals("totem_status") ? 1.0 : 3.0, x, y);
      if (this.scaledText()) {
         y = this.slider("textScale", "Text size", 0.5, 3.0, x, y);
      }

      y = this.slider("opacity", "Opacity", 0.1, 1.0, x, y);
      if (this.colored()) {
         long current = this.runtime.config().activeSettings().get(this.prefix + "color").getAsLong();
         int index = 0;

         for (Preset preset : ColorPresets.ALL) {
            this.editable(new ColorSwatch(preset, ColorPresets.matches(current, preset), x + index % 6 * 22, y + index / 6 * 18, 18, 15, () -> {
               this.runtime.settings().preview(this.prefix + "color", new JsonPrimitive(ColorPresets.apply(current, preset)));
               this.rebuild();
            }));
            index++;
         }

         y += 38;
      }

      if (this.backgrounded()) {
         y = this.toggle("background", "Background", x, y);
      }

      if (this.id.equals("armor_status")) {
         this.addRenderableWidget(new PvpHudEditorScreen.LabelWidget(x, y, 134, "Spacing"));
         this.editable(
            new NeonSlider(
               Component.literal("Spacing"),
               this.setting("spacing"),
               0.0,
               8.0,
               -15058,
               x,
               y + 10,
               134,
               value -> Math.round(value) + "px",
               value -> this.runtime.settings().preview(this.prefix + "spacing", new JsonPrimitive(Math.round(value)))
            )
         );
         y += 27;
         y = this.toggle("durability", "Durability bars", x, y);
         y = this.toggle("offhand", "Offhand item", x, y);
         boolean on = this.runtime.pvp().enabled(this.id);
         this.editable(
            new NeonToggle(
               this.prefix + "enabled",
               Component.literal("Enabled"),
               Component.literal("Enabled: " + (on ? "On" : "Off")),
               "",
               on,
               -15058,
               x,
               y,
               134,
               134,
               b -> {
                  this.runtime.pvp().setEnabled(this.id, !this.runtime.pvp().enabled(this.id));
                  this.rebuild();
               }
            )
         );
         y += 22;
      }

      if (this.id.equals("shield_status")) {
         y = this.toggle("icon", "Icon", x, y);
      }

      this.editable(this.button("Reset position", x, y, 134, this::resetPosition));
      y += 21;
      this.addRenderableWidget(this.button("Back", x, y, 64, this::onClose));
      this.editable(this.button("Save", x + 70, y, 64, () -> this.runtime.settings().flush()));
      this.setFocused(null);
   }

   private void resetPosition() {
      for (String key : new String[]{"x", "y", "free"}) {
         this.runtime.settings().preview(this.prefix + key, ((Definition)SettingsSchema.definitions().get(this.prefix + key)).initial());
      }
   }

   private void move(double x, double y) {
      Rect size = this.renderer.bounds(this.id, this.width, this.height);
      HudPlacement p = new HudPlacement(x, y, 1.0).clamp(this.width, this.height, size.width(), size.height());
      this.runtime.settings().preview(this.prefix + "x", new JsonPrimitive(p.x()));
      this.runtime.settings().preview(this.prefix + "y", new JsonPrimitive(p.y()));
      if (PvpHudLayout.anchored(this.id)) {
         this.runtime.settings().preview(this.prefix + "free", new JsonPrimitive(true));
      }
   }

   private void adjust(String key, double delta, double min, double max) {
      this.runtime.settings().preview(this.prefix + key, new JsonPrimitive(Math.max(min, Math.min(max, this.setting(key) + delta))));
   }

   public void renderBackground(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      if (this.minecraft.level == null) {
         this.renderPanorama(draw, delta);
      }
   }

   public void render(GuiGraphics draw, int mx, int my, float delta) {
      this.renderer.renderCard(draw, this.id, true);
      Rect r = this.renderer.bounds(this.id, this.width, this.height);
      NeonUi.outline(draw, r.x() - 2, r.y() - 2, r.width() + 4, r.height() + 4, -13703542);
      NeonUi.glow(draw, r.x() - 2, r.y() - 2, r.width() + 4, r.height() + 4, -13703542, 1.0F);
      NeonUi.panel(draw, this.panelX(), 4, 150, this.height - 8, -15058);
      draw.drawString(this.font, this.font.plainSubstrByWidth("Drag to move", 134), this.panelX() + 8, 10, -5720);
      super.render(draw, mx, my, delta);
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (super.mouseClicked(click, doubled)) {
         return true;
      } else {
         Rect r = this.renderer.bounds(this.id, this.width, this.height);
         if (!this.runtime.settings().readOnly()
            && click.button() == 0
            && click.x() >= r.x()
            && click.y() >= r.y()
            && click.x() < r.x() + r.width()
            && click.y() < r.y() + r.height()) {
            this.setFocused(null);
            this.dragging = true;
            this.offsetX = click.x() - r.x();
            this.offsetY = click.y() - r.y();
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (!this.dragging) {
         return super.mouseDragged(click, dx, dy);
      }

      this.move((click.x() - this.offsetX) / this.width, (click.y() - this.offsetY) / this.height);
      return true;
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      if (!this.dragging) {
         return super.mouseReleased(click);
      }

      this.dragging = false;
      Rect r = this.renderer.bounds(this.id, this.width, this.height);
      boolean left = r.x() + r.width() / 2 > this.width / 2;
      if (left != this.panelLeft) {
         this.panelLeft = left;
         this.rebuild();
      }

      return true;
   }

   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      if (this.runtime.settings().readOnly()) {
         return false;
      }

      this.adjust("scale", vertical * 0.1, 0.5, this.id.equals("totem_status") ? 1.0 : 3.0);
      this.rebuild();
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.runtime.settings().readOnly()) {
         return super.keyPressed(input);
      } else {
         int key = input.key();
         double step = (input.modifiers() & 1) != 0 ? 10.0 : 1.0;
         double dx = key == 262 ? step : (key == 263 ? -step : 0.0);
         double dy = key == 264 ? step : (key == 265 ? -step : 0.0);
         if ((dx != 0.0 || dy != 0.0) && !(this.getFocused() instanceof NeonSlider)) {
            Rect r = this.renderer.bounds(this.id, this.width, this.height);
            this.move((r.x() + dx) / this.width, (r.y() + dy) / this.height);
            return true;
         } else {
            return key != 61 && key != 45 ? super.keyPressed(input) : this.mouseScrolled(0.0, 0.0, 0.0, key == 61 ? 1.0 : -1.0);
         }
      }
   }

   public void onClose() {
      this.runtime.settings().settle();
      this.minecraft.setScreen(this.parent);
   }

   @Environment(EnvType.CLIENT)
   private static final class LabelWidget extends AbstractWidget {
      private final String label;

      LabelWidget(int x, int y, int width, String label) {
         super(x, y, width, 9, Component.literal(label));
         this.label = label;
         this.active = false;
      }

      protected void renderWidget(GuiGraphics draw, int mouseX, int mouseY, float delta) {
         draw.drawString(Minecraft.getInstance().font, this.label, this.getX(), this.getY(), -1775121);
      }

      protected void updateWidgetNarration(NarrationElementOutput builder) {
      }
   }
}
