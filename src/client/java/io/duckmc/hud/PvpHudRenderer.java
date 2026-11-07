package io.duckmc.hud;

import com.google.gson.JsonObject;
import io.duckmc.DuckMcRuntime;
import io.duckmc.pvp.PvpCards;
import io.duckmc.pvp.PvpHudLayout;
import io.duckmc.pvp.PvpPolicy;
import io.duckmc.pvp.PvpSnapshot;
import io.duckmc.pvp.PvpHudLayout.Rect;
import io.duckmc.ui.model.HudPlacement;
import io.duckmc.ui.render.GlassRenderer;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.render.UiText;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public final class PvpHudRenderer implements HudElement {
   public static final int WIDTH = 176;
   public static final int HEIGHT = 64;
   private static final int ICON = 16;
   private static final int BAR = 2;
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final Identifier[] EMPTY = new Identifier[]{
      Identifier.withDefaultNamespace("container/slot/helmet"),
      Identifier.withDefaultNamespace("container/slot/chestplate"),
      Identifier.withDefaultNamespace("container/slot/leggings"),
      Identifier.withDefaultNamespace("container/slot/boots")
   };
   private static final ItemStack[] SAMPLE = new ItemStack[]{
      new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)
   };
   private final DuckMcRuntime runtime;

   public PvpHudRenderer(DuckMcRuntime runtime) {
      this.runtime = runtime;
   }

   public void render(GuiGraphics draw, DeltaTracker ticks) {
      Minecraft client = Minecraft.getInstance();
      if (!client.options.hideGui && client.player != null && !client.player.isSpectator()) {
         for (String id : PvpPolicy.VISIBLE_CARDS) {
            this.runtime.pvp().guard(id, () -> this.renderCard(draw, id, false));
         }

         this.runtime.pvp().guard("optimize_crystals", () -> {
            String feedback = this.runtime.pvp().crystalFeedback();
            if (!feedback.isEmpty()) {
               draw.drawCenteredString(Minecraft.getInstance().font, feedback, draw.guiWidth() / 2, draw.guiHeight() - 70, -11193);
            }
         });
      }
   }

   public HudPlacement placement(String id, int width, int height) {
      JsonObject s = this.runtime.config().activeSettings();
      String prefix = "hud." + id + ".";
      double scale = Math.max(0.5, Math.min(s.get(prefix + "scale").getAsDouble(), Math.min(width / 176.0, height / 64.0)));
      return new HudPlacement(s.get(prefix + "x").getAsDouble(), s.get(prefix + "y").getAsDouble(), scale).clamp(width, height, 176, 64);
   }

   public void renderCard(GuiGraphics draw, String id, boolean preview) {
      if (id.equals("shield_status")) {
         this.renderShield(draw, preview);
      } else if (PvpHudLayout.anchored(id)) {
         this.renderMovable(draw, id, preview);
      } else {
         List<String> lines = PvpCards.lines(id, this.runtime.pvp().snapshot());
         if (lines.isEmpty()) {
            if (!preview) {
               return;
            }

            lines = List.of(id.replace('_', ' '), "Layout preview", "No live status to display");
         }

         HudPlacement p = this.placement(id, draw.guiWidth(), draw.guiHeight());
         JsonObject s = this.runtime.config().activeSettings();
         String prefix = "hud." + id + ".";
         double opacity = s.get(prefix + "opacity").getAsDouble();
         int color = s.get(prefix + "color").getAsInt();
         color = (int)((color >>> 24 & 0xFF) * opacity) << 24 | color & 16777215;
         Matrix3x2fStack matrices = draw.pose();
         matrices.pushMatrix();

         try {
            matrices.translate((float)(p.x() * draw.guiWidth()), (float)(p.y() * draw.guiHeight()));
            matrices.scale((float)p.scale());
            if (s.get(prefix + "background").getAsBoolean()) {
               GlassRenderer.panel(draw, 0, 0, 176, 64, this.runtime.theme(), true, opacity);
            }

            Font renderer = Minecraft.getInstance().font;
            int y = 7;

            for (String line : lines) {
               Component styled = UiText.styled(Component.literal(line), this.runtime.theme());
               List<FormattedCharSequence> wrapped = renderer.split(styled, 162);
               if (!wrapped.isEmpty()) {
                  draw.drawString(renderer, wrapped.getFirst(), 7, y, color);
               }

               y += 10;
            }
         } finally {
            matrices.popMatrix();
         }
      }
   }

   private String shieldLabel(String state) {
      return switch (state) {
         case "READY" -> "Shield: Not broken";
         case "DISABLED" -> "Shield: Broken";
         default -> "Shield: Unknown";
      };
   }

   private int shieldWidth(String label, boolean icon) {
      return Minecraft.getInstance().font.width(label) + (icon ? 18 : 8);
   }

   private double number(String id, String key) {
      return this.runtime.config().activeSettings().get("hud." + id + "." + key).getAsDouble();
   }

   private boolean flag(String id, String key) {
      return this.runtime.config().activeSettings().get("hud." + id + "." + key).getAsBoolean();
   }

   private Rect home(String id, int width, int height) {
      Minecraft client = Minecraft.getInstance();
      float scale = (float)Math.min(2.0, this.number(id, "scale"));
      if (id.equals("target_info")) {
         return PvpHudLayout.target(width, height, (int)(9.0F * scale * this.number(id, "textScale") + 2.0));
      } else if (id.equals("armor_status")) {
         int w = (int)(this.armorWidth() * scale);
         int h = (int)(this.armorHeight() * scale);
         return new Rect(Math.max(0, width / 2 - 124 - w), Math.max(0, height - 4 - h), w, h);
      } else {
         return this.totemRect(width, height, scale);
      }
   }

   private int armorStep() {
      return 18 + (int)this.number("armor_status", "spacing");
   }

   private int armorWidth() {
      return 16 + (this.flag("armor_status", "offhand") ? 18 : 0);
   }

   private int armorHeight() {
      return this.armorStep() * ARMOR.length - (int)this.number("armor_status", "spacing");
   }

   private void drawPiece(GuiGraphics draw, ItemStack stack, int x, int y, int alpha, boolean bar) {
      if (!stack.isEmpty()) {
         draw.renderItem(stack, x, y);
         if (bar && stack.isBarVisible()) {
            draw.fill(x + 2, y + 16, x + 15, y + 18, alpha << 24);
            draw.fill(x + 2, y + 16, x + 2 + stack.getBarWidth(), y + 17, alpha << 24 | stack.getBarColor() & 16777215);
         }
      }
   }

   private void renderArmor(GuiGraphics draw, boolean preview, float scale, double opacity) {
      LocalPlayer player = Minecraft.getInstance().player;
      boolean sample = preview;
      if (player != null) {
         for (EquipmentSlot slot : ARMOR) {
            sample &= player.getItemBySlot(slot).isEmpty();
         }
      }

      boolean bars = this.flag("armor_status", "durability");
      int alpha = (int)(255.0 * opacity);
      int step = this.armorStep();
      Rect at = this.home("armor_status", draw.guiWidth(), draw.guiHeight());
      Matrix3x2fStack matrices = draw.pose();
      matrices.pushMatrix();

      try {
         matrices.translate(at.x(), at.y());
         matrices.scale(scale);

         for (int i = 0; i < ARMOR.length; i++) {
            if (!sample && player != null && player.getItemBySlot(ARMOR[i]).isEmpty()) {
               draw.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY[i], 0, i * step, 16, 16);
            }

            this.drawPiece(draw, !sample && player != null ? player.getItemBySlot(ARMOR[i]) : SAMPLE[i], 0, i * step, alpha, bars);
         }

         if (this.flag("armor_status", "offhand") && player != null) {
            this.drawPiece(draw, player.getOffhandItem(), 18, (this.armorHeight() - 16) / 2, alpha, bars);
         }
      } finally {
         matrices.popMatrix();
      }
   }

   private Rect totemRect(int width, int height, float scale) {
      Minecraft client = Minecraft.getInstance();
      int rows = client.player == null ? 1 : Math.max(1, (int)Math.ceil((client.player.getMaxHealth() + client.player.getAbsorptionAmount()) / 20.0F));
      return PvpHudLayout.totem(width, height, rows, height - 35, scale);
   }

   public Rect bounds(String id, int width, int height) {
      if (id.equals("shield_status")) {
         HudPlacement p = this.placement(id, width, height);
         double size = p.scale() * this.number(id, "textScale");
         String label = this.shieldLabel(this.runtime.pvp().shieldIndicator());
         return new Rect((int)(p.x() * width), (int)(p.y() * height), (int)(this.shieldWidth(label, this.flag(id, "icon")) * size), (int)(14.0 * size));
      } else if (PvpHudLayout.anchored(id)) {
         Rect home = this.home(id, width, height);
         return !this.flag(id, "free")
            ? home
            : new Rect((int)(this.number(id, "x") * width), (int)(this.number(id, "y") * height), home.width(), home.height());
      } else {
         HudPlacement p = this.placement(id, width, height);
         return new Rect((int)(p.x() * width), (int)(p.y() * height), (int)(176.0 * p.scale()), (int)(64.0 * p.scale()));
      }
   }

   private void renderMovable(GuiGraphics draw, String id, boolean preview) {
      PvpSnapshot snapshot = this.runtime.pvp().snapshot();
      if (preview || snapshot.connected()) {
         int width = draw.guiWidth();
         int height = draw.guiHeight();
         Rect home = this.home(id, width, height);
         Rect at = this.bounds(id, width, height);
         if (this.flag(id, "background")) {
            draw.fill(at.x() - 2, at.y() - 2, at.x() + at.width() + 2, at.y() + at.height() + 2, (int)(150.0 * this.number(id, "opacity")) << 24 | 660000);
         }

         Matrix3x2fStack matrices = draw.pose();
         matrices.pushMatrix();

         try {
            matrices.translate(at.x() - home.x(), at.y() - home.y());
            this.renderAnchored(draw, id, preview);
         } finally {
            matrices.popMatrix();
         }
      }
   }

   private void renderShield(GuiGraphics draw, boolean preview) {
      if (preview || this.runtime.pvp().snapshot().connected()) {
         String state = this.runtime.pvp().shieldIndicator();

         int color = switch (state) {
            case "READY" -> -11141291;
            case "DISABLED" -> -48060;
            default -> -5590340;
         };
         String id = "shield_status";
         String label = this.shieldLabel(state);
         int alpha = (int)(255.0 * this.number(id, "opacity"));
         color = alpha << 24 | color & 16777215;
         int custom = this.runtime.config().activeSettings().get("hud." + id + ".color").getAsInt();
         int text = (custom & 16777215) == 16777215 ? color : alpha << 24 | custom & 16777215;
         boolean icon = this.flag(id, "icon");
         int width = this.shieldWidth(label, icon);
         Rect at = this.bounds(id, draw.guiWidth(), draw.guiHeight());
         HudPlacement p = this.placement(id, draw.guiWidth(), draw.guiHeight());
         Matrix3x2fStack matrices = draw.pose();
         matrices.pushMatrix();

         try {
            matrices.translate(at.x(), at.y());
            matrices.scale((float)(p.scale() * this.number(id, "textScale")));
            if (this.flag(id, "background")) {
               draw.fill(0, 0, width, 14, alpha * 3 / 4 << 24 | 660000);
               NeonUi.outline(draw, 0, 0, width, 14, color);
            }

            if (icon) {
               matrices.pushMatrix();
               matrices.translate(2.0F, 1.0F);
               matrices.scale(0.75F);
               draw.renderItem(new ItemStack(Items.SHIELD), 0, 0);
               matrices.popMatrix();
            }

            draw.drawString(Minecraft.getInstance().font, label, icon ? 15 : 4, 3, text);
         } finally {
            matrices.popMatrix();
         }
      }
   }

   private void renderAnchored(GuiGraphics draw, String id, boolean preview) {
      Minecraft client = Minecraft.getInstance();
      PvpSnapshot snapshot = this.runtime.pvp().snapshot();
      if (preview || snapshot.connected()) {
         int width = draw.guiWidth();
         int height = draw.guiHeight();
         JsonObject settings = this.runtime.config().activeSettings();
         String prefix = "hud." + id + ".";
         double opacity = settings.get(prefix + "opacity").getAsDouble();
         float scale = (float)Math.min(2.0, settings.get(prefix + "scale").getAsDouble());
         int color = (int)(255.0 * opacity) << 24 | settings.get(prefix + "color").getAsInt() & 16777215;
         if (id.equals("target_info")) {
            List<String> lines = PvpCards.lines(id, snapshot);
            if (lines.isEmpty() && preview) {
               lines = List.of("2.5 blocks", "20.0 HP");
            }

            scale *= (float)settings.get(prefix + "textScale").getAsDouble();
            Rect rect = this.home(id, width, height);
            int y = rect.y();

            for (String line : lines) {
               Matrix3x2fStack matrices = draw.pose();
               matrices.pushMatrix();

               try {
                  matrices.translate(width / 2.0F, y);
                  matrices.scale(scale);
                  draw.drawCenteredString(client.font, line, 0, 0, color);
               } finally {
                  matrices.popMatrix();
               }

               y += (int)(9.0F * scale + 2.0F);
            }
         } else if (id.equals("armor_status")) {
            this.renderArmor(draw, preview, scale, opacity);
         } else {
            Rect r = this.totemRect(width, height, scale);
            Matrix3x2fStack matrices = draw.pose();
            matrices.pushMatrix();

            try {
               float iconScale = r.width() / 16.0F;
               matrices.translate(r.x(), r.y());
               matrices.scale(iconScale);
               draw.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0);
            } finally {
               matrices.popMatrix();
            }

            String count = Integer.toString(preview ? 3 : snapshot.totems());
            float countScale = Math.min(r.width() / 16.0F, (float)r.width() / Math.max(1, client.font.width(count)));
            matrices.pushMatrix();

            try {
               matrices.translate(r.x() + r.width() / 2.0F, r.y() + r.width() + 2);
               matrices.scale(countScale);
               draw.drawCenteredString(client.font, count, 0, 0, color);
            } finally {
               matrices.popMatrix();
            }
         }
      }
   }
}
