package io.duckmc.ui.screen;

import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcRuntime;
import io.duckmc.fabric.PerformanceAdapter;
import io.duckmc.performance.PerformanceDraft;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.ScrollRows;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.NeonSlider;
import io.duckmc.ui.widget.NeonToggle;
import io.duckmc.ui.widget.PerformanceButton;
import java.math.BigDecimal;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class PerformanceScreen extends DuckUiScreen {
   private static final String[] GROUPS = new String[]{"General", "Memory", "Entities", "Misc"};
   private static final List<List<PerformanceScreen.Row>> ROWS = List.of(
      List.of(
         new PerformanceScreen.Row(
            "module.fps_boost",
            "FPS Boost",
            "Requires Sodium for Minecraft 1.21.11. Reversible preset; results depend on your hardware and settings. Enables DuckMC performance modules; unavailable hooks remain off. Manual edits survive disabling and restart."
         ),
         new PerformanceScreen.Row(
            "module.frame_pacing", "Frame pacing", "Adds a foreground FPS ceiling; never raises a lower vanilla limit or changes vanilla options."
         ),
         new PerformanceScreen.Row("performance.foregroundFps", "Foreground FPS", "30–360 FPS or Unlimited. VSync and vanilla limits may cap lower."),
         new PerformanceScreen.Row("module.background_throttling", "Background throttling", "Independent lower cap while the window is unfocused."),
         new PerformanceScreen.Row("performance.backgroundFps", "Background FPS", "5–60 FPS. Disabling immediately restores vanilla decisions."),
         new PerformanceScreen.Row(
            "module.loading_ui",
            "Loading / UI work",
            "Reduces DuckMC diagnostic formatting and statistics refresh to twice per second. Does not alter vanilla loading."
         )
      ),
      List.of(
         new PerformanceScreen.Row(
            "module.cache_management", "Owned cache budget", "Only DuckMC-owned CPU caches. No forced garbage collection and no launcher heap changes."
         ),
         new PerformanceScreen.Row(
            "performance.cacheMiB",
            "Cache budget (MiB)",
            "1–128 MiB of estimated retained cache weight, not a JVM memory guarantee. Disabled uses a conservative 2 MiB budget."
         ),
         new PerformanceScreen.Row("clear", "Clear DuckMC caches", "Drops only DuckMC-owned text and passive-render snapshots.")
      ),
      List.of(
         new PerformanceScreen.Row(
            "module.entity_culling",
            "Off-screen culling",
            "Conservative extra frustum check for passive animals, items and XP. Preserves players, named, glowing, targeted and special entities. Not occlusion culling."
         ),
         new PerformanceScreen.Row(
            "module.distant_animation",
            "Distant passive animation",
            "Beyond 32 blocks, reuses passive animal render snapshots for 2–4 ticks. Position still updates every frame. No simulation is skipped."
         ),
         new PerformanceScreen.Row(
            "module.entity_distance",
            "Passive render distance",
            "Limits only unprotected passive animals, items and XP. Hostile mobs and players remain vanilla."
         ),
         new PerformanceScreen.Row("performance.entityDistance", "Distance (blocks)", "16–256 blocks."),
         new PerformanceScreen.Row(
            "module.item_limits",
            "Item / XP render limits",
            "Per-frame render budgets only. Items remain in the world; targeted, named and glowing entities are protected."
         ),
         new PerformanceScreen.Row("performance.itemLimit", "Items per frame", "16–1024 render candidates."),
         new PerformanceScreen.Row("performance.orbLimit", "XP orbs per frame", "8–512 render candidates.")
      ),
      List.of(
         new PerformanceScreen.Row(
            "module.gui_cache", "DuckMC text cache", "Caches wrapped text in DuckMC cards only; cleared on resource reload, resize or settings changes."
         ),
         new PerformanceScreen.Row(
            "module.diagnostics", "Diagnostic overlay", "Measured rolling frame intervals, percentile timings and JVM heap usage. No FPS improvement promises."
         ),
         new PerformanceScreen.Row(
            "compatibility",
            "Compatibility status",
            "Known overlaps are disabled conservatively; this is not a claim that every mod combination has been tested."
         ),
         new PerformanceScreen.Row(
            "defaults", "Restore performance defaults", "Confirmation required. Resets only performance modules, values and preset ownership."
         )
      )
   );
   private final DuckMcRuntime runtime;
   private final PerformanceDraft draft;
   private boolean clearCachesOnSave;
   private boolean rebuilding;
   private int group;
   private int firstRow;
   private int pageSize;
   private String notice = "Changes apply only after Save. Back or Escape discards unsaved edits.";
   private boolean confirming;

   public PerformanceScreen(DuckMcRuntime runtime) {
      super(Component.literal("Performance"), runtime);
      this.runtime = runtime;
      this.draft = new PerformanceDraft(runtime.settings());
   }

   private void rebuild() {
      this.clearWidgets();
      this.rebuilding = true;

      try {
         this.init();
      } finally {
         this.rebuilding = false;
      }
   }

   protected void init() {
      if (!this.rebuilding) {
         this.prepareLayout();
      }

      int x = Math.max(14, (this.width - 560) / 2);
      int w = Math.min(560, this.width - 28);
      int tabWidth = (w - 12) / 4;

      for (int i = 0; i < GROUPS.length; i++) {
         int selected = i;
         ((PerformanceButton)this.addRenderableWidget(PerformanceButton.create(Component.literal(GROUPS[i]), b -> {
            this.group = selected;
            this.firstRow = 0;
            this.confirming = false;
            this.rebuild();
         }).tab(i).dimensions(x + i * (tabWidth + 4), 28, tabWidth, 20).build())).active = i != this.group;
      }

      this.pageSize = Math.max(1, (this.height - 118) / 22);
      this.firstRow = ScrollRows.move(this.firstRow, 0, ROWS.get(this.group).size(), this.pageSize);

      for (int index = this.firstRow; index < Math.min(ROWS.get(this.group).size(), this.firstRow + this.pageSize); index++) {
         PerformanceScreen.Row row = ROWS.get(this.group).get(index);
         int y = 54 + (index - this.firstRow) * 22;
         if (row.key().startsWith("module.")) {
            String name = row.key().substring(7);
            boolean preset = name.equals("fps_boost");
            String reason = this.runtime.performance().unavailableReason(name);
            boolean requested = this.draft.settings().get(row.key()).getAsBoolean();
            String state = !reason.isEmpty() ? (preset ? "Requires Sodium" : "Unavailable") : (requested ? "On" : "Off");
            NeonToggle button = new NeonToggle(
               row.key(),
               Component.literal(row.label()),
               Component.literal(row.label() + ": " + state),
               reason.isEmpty() ? "" : state,
               requested,
               -13703542,
               x,
               y,
               w,
               w,
               b -> {
                  this.draft.setEnabled(name, !requested);
                  this.notice = "Pending — press Save to apply. " + row.help();
                  this.rebuild();
               }
            );
            button.active = !this.runtime.settings().readOnly() && (reason.isEmpty() || !preset && requested);
            button.setTooltip(Tooltip.create(Component.literal(reason.isEmpty() ? row.help() : reason)));
            this.addRenderableWidget(button);
         } else if (row.key().equals("performance.foregroundFps")) {
            NeonSlider slider = new NeonSlider(
               Component.literal(row.label()),
               this.draft.settings().get(row.key()).getAsInt(),
               30.0,
               361.0,
               -13703542,
               x + w - 174,
               y + 3,
               170,
               value -> value >= 360.5 ? "Unlimited" : Math.round(value) + " FPS",
               value -> {
                  this.draft.preview(row.key(), new JsonPrimitive(Math.round(value)));
                  this.notice = "Pending — press Save to apply. " + row.help();
               }
            );
            slider.active = !this.runtime.settings().readOnly();
            slider.setTooltip(Tooltip.create(Component.literal(row.help())));
            this.addRenderableWidget(slider);
         } else if (row.key().startsWith("performance.")) {
            NeonField field = new NeonField(this.font, x + w - 74, y + 2, 70, 16, Component.literal(row.label()), -13703542);
            field.setMaxLength(8);
            field.setValue(this.draft.settings().get(row.key()).getAsString());
            field.setEditable(!this.runtime.settings().readOnly());
            field.setTooltip(Tooltip.create(Component.literal(row.help())));
            field.setResponder(value -> {
               try {
                  this.draft.preview(row.key(), new JsonPrimitive(new BigDecimal(value)));
                  this.notice = "Pending — press Save to apply. " + row.help();
               } catch (IllegalArgumentException | IllegalStateException invalid) {
                  this.notice = "Invalid " + row.label() + "; previous value retained.";
               }
            });
            this.addRenderableWidget(field);
         } else {
            PerformanceButton button = PerformanceButton.create(
                  Component.literal(row.key().equals("defaults") && this.confirming ? "Confirm defaults" : row.label()), b -> this.special(row.key())
               )
               .dimensions(x, y, w, 20)
               .build();
            button.setTooltip(Tooltip.create(Component.literal(row.help())));
            button.active = !row.key().equals("defaults") || !this.runtime.settings().readOnly();
            this.addRenderableWidget(button);
         }
      }

      this.addRenderableWidget(
         PerformanceButton.create(Component.translatable("gui.back"), b -> this.onClose()).gold().dimensions(x, this.height - 28, 80, 20).build()
      );
      ((PerformanceButton)this.addRenderableWidget(PerformanceButton.create(Component.literal("Save"), b -> {
         if (this.draft.save()) {
            this.runtime.performance().sync();
            if (this.clearCachesOnSave) {
               this.runtime.performance().clearCaches();
            }

            this.clearCachesOnSave = false;
            this.notice = "";
         }
      }).gold().dimensions(x + w - 100, this.height - 28, 100, 20).build())).active = !this.runtime.settings().readOnly();
      this.addSectionSidebar();
   }

   private boolean scrollRows(int amount) {
      int previous = this.firstRow;
      this.firstRow = ScrollRows.move(this.firstRow, amount, ROWS.get(this.group).size(), this.pageSize);
      if (this.firstRow != previous) {
         this.rebuild();
      }

      return this.firstRow != previous;
   }

   @Override
   protected boolean scrollContents(double mx, double my, double horizontal, double vertical) {
      int x = Math.max(14, (this.width - 560) / 2);
      int w = Math.min(560, this.width - 28);
      if (vertical != 0.0 && mx >= x && mx <= x + w && my >= 54.0 && my < this.height - 64) {
         this.scrollRows(vertical < 0.0 ? 1 : -1);
         return true;
      } else {
         return super.scrollContents(mx, my, horizontal, vertical);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 267) {
         this.scrollRows(this.pageSize);
         return true;
      } else if (input.key() == 266) {
         this.scrollRows(-this.pageSize);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private void special(String key) {
      switch (key) {
         case "clear":
            this.clearCachesOnSave = true;
            this.notice = "DuckMC caches will be cleared when you press Save.";
            break;
         case "compatibility":
            List<String> unavailable = PerformanceAdapter.HOOKS
               .keySet()
               .stream()
               .filter(name -> !this.runtime.performance().unavailableReason(name).isEmpty())
               .toList();
            this.notice = unavailable.isEmpty()
               ? "No recognized overlap detected. Unknown combinations are not certified."
               : "Unavailable: " + String.join(", ", unavailable) + ". Hover each control for its reason.";
            break;
         case "defaults":
            if (!this.confirming) {
               this.confirming = true;
               this.notice = "Restore performance settings? Confirm defaults; Back cancels.";
            } else {
               this.draft.defaults();
               this.confirming = false;
               this.notice = "Performance defaults pending — press Save to apply.";
            }

            this.rebuild();
            break;
         default:
            throw new IllegalArgumentException("Unknown action");
      }
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      int x = Math.max(14, (this.width - 560) / 2);
      int w = Math.min(560, this.width - 28);
      draw.drawString(this.font, "Performance / " + GROUPS[this.group], x + 4, 12, -13703542);

      for (int index = this.firstRow; index < Math.min(ROWS.get(this.group).size(), this.firstRow + this.pageSize); index++) {
         PerformanceScreen.Row row = ROWS.get(this.group).get(index);
         int y = 54 + (index - this.firstRow) * 22;
         if (row.key().startsWith("performance.")) {
            NeonUi.row(draw, x, y, w, 20, -13703542, 0.0F);
            draw.drawString(this.font, this.font.plainSubstrByWidth(row.label(), w - 90), x + 8, y + 6, -854278);
         }
      }

      if (ROWS.get(this.group).size() > this.pageSize) {
         String hint = "Scroll / PgUp / PgDn";
         draw.drawString(this.font, hint, x + w - this.font.width(hint) - 4, 12, -6574399);
      }

      NeonUi.inset(draw, x, this.height - 60, w, 27, -13703542);
      String status = !this.runtime.settings().error().isBlank() ? this.runtime.settings().error() : this.notice;
      draw.drawString(this.font, this.font.plainSubstrByWidth(status, w - 12), x + 6, this.height - 57, -4208428, false);
      String readout = this.group == 1
         ? "Owned cache estimate: " + this.runtime.performance().cacheWeight() / 1024L + " KiB. Heap size is set in your launcher."
         : this.runtime.performance().readout();
      draw.drawString(this.font, this.font.plainSubstrByWidth(readout, w - 12), x + 6, this.height - 46, -7350355, false);
      super.renderContents(draw, mouseX, mouseY, delta);
   }

   public void onClose() {
      this.runtime.router().open(ScreenRoute.MAIN);
   }

   @Environment(EnvType.CLIENT)
   private record Row(String key, String label, String help) {
   }
}
