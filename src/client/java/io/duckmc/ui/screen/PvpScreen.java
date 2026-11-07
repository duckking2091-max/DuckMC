package io.duckmc.ui.screen;

import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.ui.model.ColorPresets;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.ColorPresets.Preset;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.ColorSwatch;
import io.duckmc.ui.widget.KeyCapture;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.NeonToggle;
import io.duckmc.ui.widget.SectionButton;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class PvpScreen extends DuckUiScreen {
   private static final String[] NAMES = new String[]{
      "Remove Particles",
      "Mace Lines",
      "Hitbox Colors",
      "Optimize Crystals",
      "Shield Status",
      "Target Info",
      "Armor Status",
      "Totem Status",
      "Low Fire",
      "Low Shield",
      "Low Totems"
   };
   private static final String[] HELP = new String[]{
      "Hide all particles, including hit, potion, flame and totem particles.",
      "Hold a mace more than 1.5 blocks above a visible player or mob. Green up to 3 blocks, yellow up to 10, red up to 20. Does not extend attack reach.",
      "Depth-tested visible entity hitboxes, up to 32 blocks and 128 outlines.",
      "Cached hotbar lookup and local feedback on your obsidian/bedrock click. No automatic selection or placement.",
      "Shields in either hand: green usable, red disabled, grey unknown. Custom replacement models may not support tint.",
      "Crosshair target information observed by your client.",
      "Left-side armor and held-item durability, 50% larger.",
      "Stacked icon/count plus observed player pops; counts reset on death or world exit.",
      "Keep only the bottom 20% of the first-person fire overlay visible.",
      "First-person shields at 80% size and lower position. Third person stays normal.",
      "Held totems at 80% size and lower; pop animation 80% smaller (20% size)."
   };
   private final DuckMcRuntime runtime;
   private final List<PvpScreen.Positioned> moving = new ArrayList<>();
   private final List<PvpScreen.Label> labels = new ArrayList<>();
   private int scroll;
   private int contentHeight;
   private int x;
   private int w;
   private String expanded = "";
   private final KeyCapture capture;

   public PvpScreen(DuckMcRuntime runtime) {
      super(Component.literal("PvP"), runtime);
      this.runtime = runtime;
      this.capture = new KeyCapture(runtime, this::rebuild);
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   protected void init() {
      this.prepareLayout();
      this.moving.clear();
      this.labels.clear();
      this.x = Math.max(14, (this.width - 580) / 2);
      this.w = Math.min(580, this.width - 28);
      int y = 0;

      for (int i = 0; i < PvpPolicy.MODULES.size(); i++) {
         if (i == 0 || i == 4 || i == 8) {
            this.labels.add(new PvpScreen.Label(i == 0 ? "Visuals" : (i == 4 ? "Status" : "Feedback"), y + 8));
            y += 22;
         }

         String id = (String)PvpPolicy.MODULES.get(i);
         String name = NAMES[i];
         String help = HELP[i];
         String unavailable = this.runtime.pvp().unavailableReason(id);
         NeonToggle toggle = new NeonToggle(
            "pvp." + id,
            this.text(name),
            this.text(name + ": " + (this.runtime.pvp().enabled(id) ? "On" : "Off")),
            "",
            this.runtime.pvp().enabled(id),
            -50340,
            this.x,
            0,
            this.w - 150,
            this.w,
            b -> {
               this.runtime.pvp().setEnabled(id, !this.runtime.pvp().enabled(id));
               this.rebuild();
            }
         );
         toggle.active = !this.runtime.settings().readOnly() && unavailable.isEmpty();
         toggle.setTooltip(Tooltip.create(Component.literal(unavailable.isEmpty() ? help : unavailable)));
         this.addMoving(toggle, y);
         this.addMoving(this.sectionButton(this.text(this.expanded.equals(id) ? "Less" : "Config"), b -> {
            this.expanded = this.expanded.equals(id) ? "" : id;
            this.rebuild();
         }).dimensions(this.x + this.w - 142, 2, 58, 16).build(), y);
         this.keyButton(id, y);
         y += 22;
         if (this.expanded.equals(id)) {
            this.labels.add(new PvpScreen.Label(help, y + 2));
            y += Math.max(24, this.font.split(this.text(help), this.w - 12).size() * 9 + 8);
            if (id.equals("remove_particles")) {
               this.labels.add(new PvpScreen.Label("All particle types are hidden while enabled.", y + 2));
               y += 24;
            } else if (id.equals("mace_lines")) {
               this.labels.add(new PvpScreen.Label("Automatic distance colors: green / yellow / red.", y + 2));
               y += 24;
            } else if (id.equals("hitbox_colors")) {
               y = this.colors("pvp.playerOutlineColor", "Player Color", y);
               y = this.colors("pvp.entityOutlineColor", "Entity Color", y);
            } else if (PvpPolicy.CARDS.contains(id)) {
               SectionButton edit = this.sectionButton(
                     this.text("Edit HUD element"), b -> this.minecraft.setScreen(new PvpHudEditorScreen(this, this.runtime, id))
                  )
                  .dimensions(this.x + 6, 0, this.w - 12, 20)
                  .build();
               edit.active = this.runtime.pvp().enabled(id);
               this.addMoving(edit, y);
               y += 24;
               if (id.equals("low_health_alert")) {
                  y = this.field("pvp.lowHealthThreshold", "Health threshold", y);
               }
            }
         }

         y += 2;
      }

      this.contentHeight = y;
      this.addRenderableWidget(this.sectionButton(this.text("Back"), b -> this.onClose()).dimensions(this.x, this.height - 28, 80, 20).build());
      ((SectionButton)this.addRenderableWidget(
            this.sectionButton(this.text("Save"), b -> this.runtime.settings().flush()).dimensions(this.x + this.w - 100, this.height - 28, 100, 20).build()
         ))
         .active = !this.runtime.settings().readOnly();
      this.layout();
      this.addSectionSidebar();
   }

   private SectionButton.StyleBuilder sectionButton(Component message, OnPress action) {
      return SectionButton.create(message, action, SectionPalette.RED);
   }

   private Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   private int colors(String key, String label, int y) {
      this.labels.add(new PvpScreen.Label(label, y + 5));
      long current = this.runtime.config().activeSettings().get(key).getAsLong();
      int perRow = Math.max(3, Math.min(12, (this.w - 118) / 19));
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

   private int field(String key, String label, int y) {
      this.labels.add(new PvpScreen.Label(label, y + 5));
      NeonField field = new NeonField(this.font, this.x + this.w / 2, 0, this.w / 2, 20, Component.literal(label), -50340);
      field.setMaxLength(6);
      field.setValue(this.runtime.config().activeSettings().get(key).getAsString());
      field.setEditable(!this.runtime.settings().readOnly());
      field.setResponder(value -> {
         try {
            this.runtime.settings().preview(key, new JsonPrimitive(Double.parseDouble(value)));
            field.setTextColor(-1);
         } catch (IllegalArgumentException invalid) {
            field.setTextColor(-36744);
         }
      });
      this.addMoving(field, y);
      return y + 26;
   }

   private void addMoving(AbstractWidget widget, int y) {
      this.addRenderableWidget(widget);
      this.moving.add(new PvpScreen.Positioned(widget, y));
   }

   private void layout() {
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.contentHeight - (this.height - 76))));

      for (PvpScreen.Positioned p : this.moving) {
         int offset = p.widget().getHeight() == 16 ? 2 : 0;
         int y = 32 + p.y() - this.scroll;
         p.widget().setY(y + offset);
         p.widget().visible = y >= 32 && y + 20 <= this.height - 34;
      }
   }

   @Override
   protected boolean scrollContents(double mx, double my, double horizontal, double vertical) {
      this.scroll -= (int)(vertical * 36.0);
      this.setFocused(null);
      this.layout();
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.capture.key(input)) {
         return true;
      }

      if (!(this.getFocused() instanceof EditBox)) {
         int step = Math.max(36, this.height - 120);
         switch (input.key()) {
            case 266:
               this.scroll -= step;
               break;
            case 267:
               this.scroll += step;
               break;
            case 268:
               this.scroll = 0;
               break;
            case 269:
               this.scroll = this.contentHeight;
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
      if (!this.capture.notice().isEmpty()) {
         draw.drawString(this.font, this.font.plainSubstrByWidth(this.capture.notice(), this.w - 8), this.x + 4, 23, -5720, false);
      }

      draw.drawString(this.font, this.text("PvP — local visuals and feedback"), this.x + 4, 14, -50340);
      draw.enableScissor(this.x, 32, this.x + this.w, this.height - 34);

      for (PvpScreen.Label label : this.labels) {
         int y = 32 + label.y() - this.scroll;
         if (y >= 32 && y < this.height - 34) {
            draw.drawWordWrap(this.font, this.text(label.text()), this.x + 6, y, this.w - 12, -4208428, false);
         }
      }

      draw.disableScissor();
      if (!this.runtime.settings().error().isBlank() || this.runtime.settings().dirty()) {
         this.footerNote(draw, "Unsaved", this.x + 80, this.x + this.w - 100, -50340);
      }

      super.renderContents(draw, mx, my, delta);
      this.capture.renderPulse(draw, -50340);
   }

   @Override
   protected void beforeLeave() {
      if (!this.runtime.settings().readOnly()) {
         this.runtime.settings().discard();
      }
   }

   @Override
   protected boolean clickContents(MouseButtonEvent click, boolean doubled) {
      this.capture.cancel();
      return super.clickContents(click, doubled);
   }

   private void keyButton(String id, int y) {
      SectionButton key = this.sectionButton(this.text(this.capture.label(id)), b -> this.capture.begin(id))
         .dimensions(this.x + this.w - 80, 2, 76, 16)
         .build();
      key.setTooltip(Tooltip.create(Component.literal("Click, then press a key. Backspace or Delete clears it.")));
      key.active = !this.runtime.settings().readOnly();
      this.addMoving(this.capture.track(id, key), y);
   }

   public void onClose() {
      this.beforeLeave();
      this.runtime.router().open(ScreenRoute.MAIN);
   }

   @Environment(EnvType.CLIENT)
   private record Label(String text, int y) {
   }

   @Environment(EnvType.CLIENT)
   private record Positioned(AbstractWidget widget, int y) {
   }
}
