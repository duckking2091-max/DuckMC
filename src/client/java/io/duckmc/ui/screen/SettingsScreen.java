package io.duckmc.ui.screen;

import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.ColorPresets;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.ColorPresets.Preset;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.ColorSwatch;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.NeonSlider;
import io.duckmc.ui.widget.NeonToggle;
import io.duckmc.ui.widget.SectionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class SettingsScreen extends DuckUiScreen {
   private static final int SWATCH = 16;
   private static final int STEP = 19;
   private static final DoubleFunction<String> PERCENT = value -> Math.round(value * 100.0) + "%";
   private static final DoubleFunction<String> PLAIN = value -> String.format(Locale.ROOT, "%.2f", value);
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private final List<SettingsScreen.Positioned> moving = new ArrayList<>();
   private final List<SettingsScreen.Label> labels = new ArrayList<>();
   private int x;
   private int w;
   private int scroll;
   private int contentHeight;

   public SettingsScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("Appearance"), runtime);
      this.parent = parent;
      this.runtime = runtime;
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   protected void init() {
      this.prepareLayout();
      this.moving.clear();
      this.labels.clear();
      this.x = 8;
      this.w = this.width - 16;
      boolean editable = !this.runtime.settings().readOnly();
      int y = 0;
      this.labels.add(new SettingsScreen.Label("Name", y + 6));
      NeonField name = new NeonField(this.font, this.x + 112, 0, this.w - 118, 20, Component.literal("Name"), -15058);
      name.setMaxLength(40);
      name.setValue(this.runtime.config().activeSettings().get("ui.name").getAsString());
      name.setEditable(editable);
      name.setResponder(value -> this.runtime.settings().preview("ui.name", new JsonPrimitive(value)));
      this.addMoving(name, y);
      y += 24;
      y = this.toggle("ui.branding", "Show branding", y);
      y = this.colors("ui.accent", "Accent color", y);
      y = this.colors("ui.background", "Background color", y);
      y = this.toggle("ui.gradient", "Gradient", y);
      y = this.colors("ui.gradientEnd", "Gradient end color", y);
      y = this.slider("ui.opacity", "Panel opacity", 0.0, 1.0, PERCENT, y);
      y = this.colors("category.performance", "Performance color", y);
      y = this.colors("category.pvp", "PvP color", y);
      y = this.colors("category.content", "Worlds color", y);
      y = this.colors("category.advantages", "Advantages color", y);
      y = this.slider("ui.radius", "Corner radius", 0.0, 24.0, value -> Math.round(value) + "", y);
      y = this.slider("ui.animationSpeed", "Animation speed", 0.0, 3.0, PLAIN, y);
      y = this.slider("ui.scale", "Menu scale", 0.5, 2.0, PERCENT, y);
      y = this.slider("ui.fontScale", "Text scale", 0.75, 2.0, PERCENT, y);
      y = this.toggle("ui.shadows", "Shadows", y);
      y = this.toggle("ui.reducedMotion", "Reduced motion", y);
      List<String> fonts = UiText.availableFonts(this.minecraft);
      SectionButton font = this.gold(this.text("Font: " + this.runtime.theme().font()), () -> {
         int next = (fonts.indexOf(this.runtime.theme().font()) + 1) % fonts.size();
         this.runtime.settings().preview("ui.font", new JsonPrimitive(fonts.get(next)));
         this.rebuild();
      }, this.x, this.w);
      font.active = editable;
      this.addMoving(font, y);
      this.contentHeight = y + 24;
      SectionButton back = this.gold(this.text("Back"), this::onClose, this.x, 80);
      back.setY(this.height - 28);
      this.addRenderableWidget(back);
      SectionButton reset = this.gold(this.text(this.runtime.settings().hasPendingReset() ? "Confirm reset" : "Reset"), () -> {
         if (this.runtime.settings().hasPendingReset()) {
            this.runtime.settings().confirmReset();
         } else {
            this.runtime.settings().resetSection("ui");
         }

         this.rebuild();
      }, this.x + (this.w - 90) / 2, 90);
      reset.setY(this.height - 28);
      reset.active = editable;
      this.addRenderableWidget(reset);
      SectionButton save = this.gold(this.text("Save"), () -> this.runtime.settings().flush(), this.x + this.w - 100, 100);
      save.setY(this.height - 28);
      save.active = editable;
      this.addRenderableWidget(save);
      this.layout();
      this.addSectionSidebar();
   }

   private Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   private SectionButton gold(Component message, Runnable action, int bx, int bw) {
      return SectionButton.create(message, ignored -> action.run(), SectionPalette.WHITE_GOLD).dimensions(bx, 0, bw, 20).build();
   }

   private int toggle(String key, String label, int y) {
      boolean on = this.runtime.config().activeSettings().get(key).getAsBoolean();
      NeonToggle toggle = new NeonToggle(
         key, this.text(label), this.text(label + ": " + (on ? "On" : "Off")), "", on, -15058, this.x, 0, this.w, this.w, b -> {
            this.runtime.settings().preview(key, new JsonPrimitive(!this.runtime.config().activeSettings().get(key).getAsBoolean()));
            this.rebuild();
         }
      );
      toggle.active = !this.runtime.settings().readOnly();
      this.addMoving(toggle, y);
      return y + 24;
   }

   private int colors(String key, String label, int y) {
      this.labels.add(new SettingsScreen.Label(label, y + 5));
      long current = this.runtime.config().activeSettings().get(key).getAsLong();
      int perRow = Math.max(3, Math.min(12, (this.w - 112) / 19));
      int index = 0;

      for (Preset preset : ColorPresets.ALL) {
         ColorSwatch swatch = new ColorSwatch(preset, ColorPresets.matches(current, preset), this.x + 112 + index % perRow * 19, 0, 16, 16, () -> {
            this.runtime.settings().preview(key, new JsonPrimitive(ColorPresets.apply(current, preset)));
            this.rebuild();
         });
         swatch.active = !this.runtime.settings().readOnly();
         this.addMoving(swatch, y + index / perRow * 19);
         index++;
      }

      return y + (11 / perRow + 1) * 19 + 5;
   }

   private int slider(String key, String label, double min, double max, DoubleFunction<String> format, int y) {
      this.labels.add(new SettingsScreen.Label(label, y + 3));
      NeonSlider slider = new NeonSlider(
         Component.literal(label),
         this.runtime.config().activeSettings().get(key).getAsDouble(),
         min,
         max,
         -15058,
         this.x + 112,
         0,
         this.w - 118,
         format,
         value -> this.runtime.settings().preview(key, new JsonPrimitive(value))
      );
      slider.active = !this.runtime.settings().readOnly();
      this.addMoving(slider, y);
      return y + 20;
   }

   private void addMoving(AbstractWidget widget, int y) {
      this.addRenderableWidget(widget);
      this.moving.add(new SettingsScreen.Positioned(widget, y));
   }

   private void layout() {
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.contentHeight - (this.height - 66))));

      for (SettingsScreen.Positioned p : this.moving) {
         int y = 32 + p.y() - this.scroll;
         p.widget().setY(y);
         p.widget().visible = y >= 32 && y + p.widget().getHeight() <= this.height - 34;
      }
   }

   @Override
   protected boolean scrollContents(double mx, double my, double horizontal, double vertical) {
      this.scroll -= (int)(vertical * 24.0);
      this.setFocused(null);
      this.layout();
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (!(this.getFocused() instanceof EditBox)) {
         int step = Math.max(36, this.height - 120);
         switch (input.key()) {
            case 266:
               this.scroll -= step;
               break;
            case 267:
               this.scroll += step;
               break;
            default:
               return super.keyPressed(input);
         }

         this.setFocused(null);
         this.layout();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mx, int my, float delta) {
      draw.drawString(this.font, this.text("Appearance"), this.x + 4, 12, (int)this.runtime.theme().accent());
      draw.enableScissor(this.x, 32, this.x + this.w, this.height - 34);

      for (SettingsScreen.Label label : this.labels) {
         int y = 32 + label.y() - this.scroll;
         draw.drawString(this.font, this.text(label.text()), this.x + 4, y, -1775121);
      }

      draw.disableScissor();
      String status = this.runtime.settings().readOnly() ? "Read-only: newer configuration" : this.runtime.settings().error();
      if (!status.isBlank()) {
         draw.drawString(this.font, this.font.plainSubstrByWidth(status, this.w - 8), this.x + 4, 22, -36744, false);
      }

      super.renderContents(draw, mx, my, delta);
   }

   public void onClose() {
      this.runtime.settings().cancelReset();
      this.runtime.settings().flush();
      this.minecraft.setScreen(this.parent);
   }

   @Environment(EnvType.CLIENT)
   private record Label(String text, int y) {
   }

   @Environment(EnvType.CLIENT)
   private record Positioned(AbstractWidget widget, int y) {
   }
}
