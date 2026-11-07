package io.duckmc.ui.screen;

import com.google.gson.JsonPrimitive;
import io.duckmc.DuckMcClient;
import io.duckmc.DuckMcRuntime;
import io.duckmc.advantages.AdvantagePolicy;
import io.duckmc.config.SettingsSchema;
import io.duckmc.config.SettingsSchema.Definition;
import io.duckmc.config.SettingsSchema.Kind;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.KeyCapture;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.NeonToggle;
import io.duckmc.ui.widget.SectionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public final class AdvantagesScreen extends DuckUiScreen {
   private static final List<String> NAMES = List.of("Stun Slam", "Auto Crystal", "Auto Breach", "Informer", "Auto Totem", "Auto Shield Break", "Auto Lunge");
   private static final List<String> HELP = List.of(
      "One click with your axe: the normal axe hit is followed in the same tick by a hotbar mace hit. The mace stays selected. Works with or without a shield.",
      "Place crystals normally: attack a newly matched crystal from your placement only while it remains under your crosshair, visible and in normal reach. Existing or visibly ambiguous crystals are skipped. Simultaneous placements at the same spot cannot prove ownership.",
      "Your sword click equips a detected Breach mace, waits, attacks once normally, then returns to that sword. No additional sword hit or guaranteed damage.",
      "Distinct local sounds for newly observed nearby wind-charge and pearl throws. Existing projectiles are primed; hidden or unknown throwers are not announced.",
      "After your totem pops, detect a replacement anywhere in your inventory and equip it into offhand after the configured delay. Optional inventory mode opens your inventory visibly; it does not guarantee anti-cheat safety.",
      "Your click on a blocking target equips a detected hotbar axe, waits, attacks once normally, then returns. Highest priority; shield disabling follows normal server mechanics.",
      "Hold a wind charge and fresh right-click: detect and visibly equip a Lunge spear and jab in the same input tick. Only the return is delayed (one tick by default). Wind charge is not thrown. Normal hunger, water, riding and Elytra restrictions apply."
   );
   private static final Map<String, String> LABELS = Map.ofEntries(
      Map.entry("openInventory", "Open inventory (safe mode)"),
      Map.entry("axeSlot", "Axe hotbar slot (1–9)"),
      Map.entry("maceSlot", "Mace hotbar slot (1–9)"),
      Map.entry("crystalSlot", "Crystal hotbar slot (1–9)"),
      Map.entry("range", "Range (blocks)"),
      Map.entry("volume", "Alert volume (0–1)"),
      Map.entry("windPitch", "Wind-charge alert pitch"),
      Map.entry("pearlPitch", "Pearl alert pitch"),
      Map.entry("health", "Danger threshold (health points)"),
      Map.entry("includeAbsorption", "Include absorption"),
      Map.entry("inventorySlot", "Totem inventory slot (0 = first)"),
      Map.entry("anchorSlot", "Anchor hotbar slot (1–9)"),
      Map.entry("glowstoneSlot", "Glowstone hotbar slot (1–9)"),
      Map.entry("stepTicks", "Step delay (ticks, 1–4)"),
      Map.entry("spearSlot", "Spear hotbar slot (1–9)"),
      Map.entry("windSlot", "Wind-charge hotbar slot (1–9)")
   );
   private final DuckMcRuntime runtime;
   private final List<AdvantagesScreen.Positioned> moving = new ArrayList<>();
   private final List<AdvantagesScreen.Label> labels = new ArrayList<>();
   private int x;
   private int w;
   private int scroll;
   private int contentHeight;
   private String expanded = "";
   private String profile = "";
   private boolean acknowledged;
   private final KeyCapture capture = new KeyCapture(DuckMcClient.runtime(), this::rebuild);

   public AdvantagesScreen(DuckMcRuntime runtime) {
      super(Component.literal("Advantages"), runtime);
      this.runtime = runtime;
   }

   private SectionButton.StyleBuilder sectionButton(Component message, OnPress action) {
      return SectionButton.create(message, action, SectionPalette.GOLD);
   }

   private Component text(String value) {
      return UiText.styled(Component.literal(value), this.runtime.theme());
   }

   private void rebuild() {
      this.rebuildWidgets();
   }

   protected void init() {
      this.prepareLayout();
      this.moving.clear();
      this.labels.clear();
      this.w = Math.min(620, this.width - 32);
      this.x = (this.width - this.w) / 2;
      this.acknowledged = this.runtime.config().advantagesAcknowledged();
      this.profile = this.runtime.config().activeProfile();
      if (!this.acknowledged) {
         this.scroll = 0;
         this.labels
            .add(
               new AdvantagesScreen.Label(
                  "These features may violate server rules and can cause temporary or permanent bans. DuckMC has no anti-cheat bypass or evasion, packet spoofing, reach changes, or concealment features. Use only where permitted. Every feature starts disabled and requires its own toggle. Select I Understand only if you accept these risks.",
                  6
               )
            );
         ((SectionButton)this.addRenderableWidget(this.sectionButton(this.text("I Understand"), b -> {
            this.runtime.settings().acknowledgeAdvantages();
            this.rebuild();
         }).dimensions(this.x + this.w - 180, this.height - 52, 180, 20).build())).active = !this.runtime.settings().readOnly();
      } else {
         int y = 0;
         String rules = "Attack priority: Shield Break > Breach > Stun Slam. One owned slot sequence at a time. Normal mechanics and reach; no aiming, bypass or cooldown removal.";
         this.labels.add(new AdvantagesScreen.Label(rules, y));
         y += this.font.split(this.text(rules), this.w - 12).size() * 9 + 12;

         for (int i = 0; i < AdvantagePolicy.MODULES.size(); i++) {
            String id = (String)AdvantagePolicy.MODULES.get(i);
            String name = NAMES.get(i);
            String help = HELP.get(i);
            boolean on = this.runtime.config().activeSettings().get("module." + id).getAsBoolean();
            String reason = this.runtime.advantages().unavailableReason(id);
            NeonToggle toggle = new NeonToggle(
               "advantages." + id, this.text(name), this.text(name + ": " + (on ? "ON" : "OFF")), "", on, -15058, this.x, 0, this.w - 150, this.w, b -> {
                  this.runtime.advantages().setEnabled(id, !this.runtime.config().activeSettings().get("module." + id).getAsBoolean());
                  this.rebuild();
               }
            );
            toggle.active = !this.runtime.settings().readOnly() && (on || reason.isEmpty());
            toggle.setTooltip(Tooltip.create(this.text(help + (reason.isEmpty() ? "" : " " + reason))));
            this.addMoving(toggle, y);
            this.addMoving(this.sectionButton(this.text("Config"), b -> {
               this.expanded = this.expanded.equals(id) ? "" : id;
               this.rebuild();
            }).dimensions(this.x + this.w - 142, 2, 58, 16).build(), y);
            this.keyButton(id, y);
            y += 22;
            if (this.expanded.equals(id)) {
               this.labels.add(new AdvantagesScreen.Label(help, y));
               y += this.font.split(this.text(help), this.w - 12).size() * 9 + 8;
               String status = this.runtime.advantages().status(id);
               this.labels.add(new AdvantagesScreen.Label(status, y));
               y += this.font.split(this.text(status), this.w - 12).size() * 9 + 8;
               String prefix = "advantages." + id + ".";

               for (Definition definition : SettingsSchema.definitions().values()) {
                  if (definition.key().startsWith(prefix)
                     && (!id.equals("auto_crystal") || definition.key().equals(prefix + "range"))
                     && (!id.equals("auto_totem") || definition.key().equals(prefix + "stepTicks") || definition.key().equals(prefix + "openInventory"))
                     && !id.equals("stun_slam")
                     && (!AdvantagePolicy.TIMED_MODULES.contains(id) || definition.key().equals(prefix + "stepTicks"))) {
                     y = this.field(
                        definition,
                        id.equals("auto_lunge") && definition.key().endsWith(".stepTicks")
                           ? "Step delay after jab (ticks, 1–4)"
                           : LABELS.getOrDefault(definition.key().substring(prefix.length()), definition.key()),
                        y
                     );
                  }
               }
            }

            y += 2;
         }

         this.contentHeight = y;
         ((SectionButton)this.addRenderableWidget(
               this.sectionButton(this.text("Save"), b -> this.runtime.settings().flush()).dimensions(this.x + this.w - 100, this.height - 28, 100, 20).build()
            ))
            .active = !this.runtime.settings().readOnly();
         this.layout();
      }

      this.addRenderableWidget(this.sectionButton(this.text("Back"), b -> this.onClose()).dimensions(this.x, this.height - 28, 80, 20).build());
      this.addSectionSidebar();
   }

   private int field(Definition definition, String label, int y) {
      String key = definition.key();
      if (definition.kind() == Kind.BOOLEAN) {
         boolean on = this.runtime.config().activeSettings().get(key).getAsBoolean();
         SectionButton button = this.sectionButton(
               this.text(label + ": " + (key.endsWith(".openInventory") ? (on ? "Yes" : "No") : (on ? "ON" : "OFF"))), b -> {
                  this.runtime.settings().preview(key, new JsonPrimitive(!this.runtime.config().activeSettings().get(key).getAsBoolean()));
                  this.rebuild();
               }
            )
            .dimensions(this.x + 6, 0, this.w - 12, 20)
            .build();
         button.active = !this.runtime.settings().readOnly();
         this.addMoving(button, y);
         return y + 26;
      } else {
         this.labels.add(new AdvantagesScreen.Label(label, y + 5));
         NeonField input = new NeonField(this.font, this.x + this.w * 2 / 3, 0, this.w / 3 - 6, 20, Component.literal(label), -15058);
         input.setMaxLength(10);
         input.setValue(this.runtime.config().activeSettings().get(key).getAsString());
         input.setEditable(!this.runtime.settings().readOnly());
         input.setResponder(value -> {
            try {
               this.runtime.settings().preview(key, new JsonPrimitive(Double.parseDouble(value)));
               input.setTextColor(-1);
            } catch (IllegalArgumentException invalid) {
               input.setTextColor(-36744);
            }
         });
         this.addMoving(input, y);
         return y + 26;
      }
   }

   private void addMoving(AbstractWidget widget, int y) {
      this.addRenderableWidget(widget);
      this.moving.add(new AdvantagesScreen.Positioned(widget, y));
   }

   private void layout() {
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.contentHeight - (this.height - 76))));

      for (AdvantagesScreen.Positioned p : this.moving) {
         int offset = p.widget().getHeight() == 16 ? 2 : 0;
         int y = 32 + p.y() - this.scroll;
         p.widget().setY(y + offset);
         p.widget().visible = y >= 32 && y + 20 <= this.height - 34;
      }
   }

   public void tick() {
      if (this.acknowledged != this.runtime.config().advantagesAcknowledged() || !this.profile.equals(this.runtime.config().activeProfile())) {
         this.rebuild();
      }
   }

   @Override
   protected boolean scrollContents(double mx, double my, double h, double v) {
      if (!this.acknowledged) {
         return false;
      }

      this.scroll -= (int)(v * 36.0);
      this.setFocused(null);
      this.layout();
      return true;
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.capture.key(input)) {
         return true;
      }

      if (this.acknowledged && !(this.getFocused() instanceof EditBox)) {
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

      draw.drawString(this.font, this.text(this.acknowledged ? "Advantages — opt-in assists" : "Advantages — read before continuing"), this.x + 4, 14, -15058);
      draw.enableScissor(this.x, 32, this.x + this.w, this.acknowledged ? this.height - 34 : this.height - 56);

      for (AdvantagesScreen.Label label : this.labels) {
         int y = 32 + label.y() - this.scroll;
         if (y >= 32 && y < this.height - 34) {
            draw.drawWordWrap(this.font, this.text(label.text()), this.x + 6, y, this.w - 12, -4208428, false);
         }
      }

      draw.disableScissor();
      String error = this.runtime.settings().error();
      if (!this.acknowledged && !error.isBlank()) {
         draw.drawWordWrap(this.font, this.text(error), this.x, this.height - 66, this.w, -36744, false);
      } else if (!error.isBlank()) {
         this.footerNote(draw, error, this.x + 80, this.x + this.w - 100, -36744);
      } else if (this.runtime.settings().dirty()) {
         this.footerNote(draw, "Unsaved", this.x + 80, this.x + this.w - 100, -11193);
      }

      super.renderContents(draw, mx, my, delta);
      this.capture.renderPulse(draw, -15058);
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
