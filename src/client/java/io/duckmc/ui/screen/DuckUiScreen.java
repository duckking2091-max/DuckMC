package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.FrameLayout;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.SectionLayout;
import io.duckmc.ui.model.Theme;
import io.duckmc.ui.model.UiLayout;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.widget.SidebarButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public abstract class DuckUiScreen extends Screen {
   private final DuckMcRuntime uiRuntime;
   private double canvasScale = 1.0;
   private int contentOffset;
   private double originX;
   private double originY;
   private long openedAt;

   private boolean framed() {
      return this instanceof PerformanceScreen
         || this instanceof SettingsScreen
         || this instanceof HudElementsScreen
         || this instanceof AboutScreen
         || this instanceof PvpScreen
         || this instanceof AdvantagesScreen
         || this instanceof ContentPage
         || this instanceof ProfileScreen
         || this instanceof DuckScreen screen && (screen.route() == ScreenRoute.MAIN || screen.route() == ScreenRoute.SETTINGS);
   }

   private boolean settingsFamily() {
      return this instanceof SettingsScreen || this instanceof HudElementsScreen || this instanceof AboutScreen || this instanceof DuckScreen;
   }

   private int accent() {
      if (this instanceof PerformanceScreen) {
         return -13703542;
      } else if (this instanceof PvpScreen) {
         return -50340;
      } else if (this instanceof ContentPage) {
         return -13653761;
      } else {
         return this.settingsFamily() ? (int)this.uiRuntime.theme().accent() : -15058;
      }
   }

   private void themedPanel(GuiGraphics draw, int x, int y, int w, int h) {
      Theme theme = this.uiRuntime.theme();
      NeonUi.panel(
         draw, x, y, w, h, this.accent(), (int)theme.background(), (int)(theme.gradient() ? theme.gradientEnd() : theme.background()), theme.opacity()
      );
   }

   protected void footerNote(GuiGraphics draw, String note, int left, int right, int color) {
      draw.drawCenteredString(this.font, this.font.plainSubstrByWidth(note, Math.max(8, right - left - 8)), (left + right) / 2, this.height - 22, color);
   }

   private boolean hasSectionSidebar() {
      return this instanceof PerformanceScreen
         || this instanceof PvpScreen
         || this instanceof AdvantagesScreen
         || this instanceof ContentScreen
         || this instanceof SettingsScreen
         || this instanceof HudElementsScreen
         || this instanceof AboutScreen
         || this instanceof ManagedPacksScreen
         || this instanceof DuckScreen screen && screen.route() == ScreenRoute.SETTINGS;
   }

   protected void addSectionSidebar() {
      if (this.hasSectionSidebar()) {
         ScreenRoute[] routes = new ScreenRoute[]{ScreenRoute.PERFORMANCE, ScreenRoute.PVP, ScreenRoute.CONTENT, ScreenRoute.ADVANTAGES, ScreenRoute.SETTINGS};
         String[] labels = new String[]{"Performance", "PvP", "Worlds & Packs", "Advantages", "Settings"};
         String[] icons = new String[]{"performance", "pvp", "content", "advantages", "settings"};
         int[] colors = new int[]{-13703542, -50340, -13653761, -15058, -2827035};

         for (int i = 0; i < routes.length; i++) {
            ScreenRoute route = routes[i];
            boolean current = i == 0 && this instanceof PerformanceScreen
               || i == 1 && this instanceof PvpScreen
               || i == 2 && (this instanceof ContentScreen || this instanceof ManagedPacksScreen)
               || i == 3 && this instanceof AdvantagesScreen
               || i == 4 && this.settingsFamily();
            SidebarButton button = new SidebarButton(
               -106, 40 + i * 28 + (i == 4 ? 12 : 0), Component.literal(labels[i]), icons[i], colors[i], current, ignored -> {
                  this.beforeLeave();
                  this.uiRuntime.router().open(route);
               }
            );
            if (i == 2) {
               button.setTooltip(Tooltip.create(Component.literal("Worlds & Resource Packs")));
            }

            this.addRenderableWidget(button);
         }
      }
   }

   protected void beforeLeave() {
   }

   protected void renderSectionSidebar(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      for (GuiEventListener child : this.children()) {
         if (child instanceof SidebarButton button) {
            button.render(draw, mouseX, mouseY, delta);
         }
      }
   }

   protected DuckUiScreen(Component title, DuckMcRuntime runtime) {
      super(title);
      this.uiRuntime = runtime;
   }

   protected void prepareLayout() {
      int physicalWidth = this.minecraft.getWindow().getGuiScaledWidth();
      int physicalHeight = this.minecraft.getWindow().getGuiScaledHeight();
      Theme theme = this.uiRuntime.theme();
      this.canvasScale = UiLayout.scale(physicalWidth, physicalHeight, theme.scale() * theme.fontScale());
      this.width = (int)Math.floor(physicalWidth / this.canvasScale);
      this.height = (int)Math.floor(physicalHeight / this.canvasScale);
      this.contentOffset = 0;
      this.originX = 0.0;
      this.originY = 0.0;
      if (this.framed()) {
         boolean sidebar = this.hasSectionSidebar();
         FrameLayout frame = FrameLayout.fit(physicalWidth, physicalHeight, theme.scale() * theme.fontScale(), sidebar ? 444 : 320, 240);
         this.canvasScale = frame.scale();
         this.originX = frame.x();
         this.originY = frame.y();
         this.contentOffset = sidebar ? 112 : 0;
         this.width = frame.width() - this.contentOffset;
         this.height = frame.height();
         if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
         }
      } else if (this.hasSectionSidebar()) {
         SectionLayout layout = SectionLayout.fit(physicalWidth, physicalHeight, theme.scale() * theme.fontScale());
         this.canvasScale = layout.scale();
         this.width = layout.contentWidth();
         this.height = layout.height();
         this.contentOffset = 112;
      }

      this.uiRuntime.performance().clearCaches();
   }

   public final void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      draw.pose().pushMatrix();

      try {
         boolean framed = this.framed();
         if (framed) {
            draw.pose().translate((float)(this.originX + this.slide()), (float)this.originY);
         }

         draw.pose().scale((float)this.canvasScale);
         draw.pose().translate(this.contentOffset, 0.0F);
         if (framed) {
            if (this.contentOffset > 0) {
               this.themedPanel(draw, -this.contentOffset, 0, this.contentOffset - 4, this.height);
               draw.fill(-104, 155, -14, 156, 1358954495);
            }

            this.themedPanel(draw, 0, 0, this.width, this.height);
         } else if (this.contentOffset > 0) {
            draw.fill(-this.contentOffset, 0, 0, this.height, -15722464);
            draw.fill(-3, 12, -2, this.height - 12, -13286831);
            draw.fill(-104, 155, -8, 156, -13286831);
         }

         this.renderContents(draw, (int)this.canvasX(mouseX), (int)this.canvasY(mouseY), delta);
      } finally {
         draw.pose().popMatrix();
      }
   }

   public void renderBackground(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      if (this.minecraft.level == null) {
         this.renderPanorama(draw, delta);
      }

      if (!this.framed() && this.uiRuntime.theme().blur()) {
         this.renderBlurredBackground(draw);
      }
   }

   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      super.render(draw, mouseX, mouseY, delta);
   }

   public final boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      return this.clickContents(new MouseButtonEvent(this.canvasX(click.x()), this.canvasY(click.y()), click.buttonInfo()), doubled);
   }

   protected boolean clickContents(MouseButtonEvent click, boolean doubled) {
      return super.mouseClicked(click, doubled);
   }

   private MouseButtonEvent canvas(MouseButtonEvent click) {
      return new MouseButtonEvent(this.canvasX(click.x()), this.canvasY(click.y()), click.buttonInfo());
   }

   public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
      return super.mouseDragged(this.canvas(click), deltaX, deltaY);
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      return super.mouseReleased(this.canvas(click));
   }

   public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      return this.scrollContents(this.canvasX(x), this.canvasY(y), horizontal, vertical);
   }

   protected boolean scrollContents(double x, double y, double horizontal, double vertical) {
      return super.mouseScrolled(x, y, horizontal, vertical);
   }

   public void mouseMoved(double x, double y) {
      super.mouseMoved(this.canvasX(x), this.canvasY(y));
   }

   private double canvasX(double x) {
      return (x - this.originX) / this.canvasScale - this.contentOffset;
   }

   private double canvasY(double y) {
      return (y - this.originY) / this.canvasScale;
   }

   private double slide() {
      if (this.uiRuntime.theme().reducedMotion()) {
         return 0.0;
      }

      double t = Math.min(1.0, (System.nanoTime() - this.openedAt) / 1.6E8);
      return (1.0 - t) * (1.0 - t) * 8.0;
   }

   public double windowX(double x) {
      return (x + this.contentOffset) * this.canvasScale + this.originX;
   }

   public double windowY(double y) {
      return y * this.canvasScale + this.originY;
   }

   public double canvasScale() {
      return this.canvasScale;
   }
}
