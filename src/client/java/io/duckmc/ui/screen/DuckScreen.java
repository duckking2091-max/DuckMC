package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.HomeLayout;
import io.duckmc.ui.model.ScreenRoute;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.model.SettingsHubLayout;
import io.duckmc.ui.model.Theme;
import io.duckmc.ui.model.UiLayout;
import io.duckmc.ui.model.HomeLayout.Bounds;
import io.duckmc.ui.render.GlassRenderer;
import io.duckmc.ui.render.MenuArtwork;
import io.duckmc.ui.render.UiText;
import io.duckmc.ui.widget.FeatureCard;
import io.duckmc.ui.widget.GoldSettingsButton;
import io.duckmc.ui.widget.Modal;
import io.duckmc.ui.widget.SectionButton;
import io.duckmc.ui.widget.SettingsCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class DuckScreen extends DuckUiScreen {
   private static final int OUTER_MARGIN = 12;
   private static final int MAX_CONTENT_WIDTH = 760;
   private static final int CARD_HEIGHT = 86;
   private static final int GAP = 10;
   private final ScreenRouter router;
   private final DuckMcRuntime runtime;
   private final ScreenRoute route;
   private Theme theme;
   private final List<FeatureCard> cards = new ArrayList<>();
   private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
   private Modal modal;
   private GuiEventListener focusBeforeModal;
   private double scrollY;
   private int contentX;
   private int contentWidth;
   private int listTop;
   private int listBottom;
   private int maxScroll;
   private long openedAt;
   private HomeLayout home;

   public DuckScreen(ScreenRouter router, DuckMcRuntime runtime, ScreenRoute route) {
      super(Component.translatable(titleKey(route)), runtime);
      this.router = Objects.requireNonNull(router, "router");
      this.runtime = Objects.requireNonNull(runtime, "runtime");
      this.route = Objects.requireNonNull(route, "route");
      this.theme = runtime.theme();
   }

   protected void init() {
      this.prepareLayout();
      this.openedAt = System.nanoTime();
      this.theme = this.runtime.theme();
      this.cards.clear();
      this.fixedWidgets.clear();
      this.modal = null;
      this.scrollY = 0.0;
      this.calculateFrame();
      if (this.route == ScreenRoute.MAIN) {
         this.initMain();
      } else if (this.route == ScreenRoute.SETTINGS) {
         this.initSettings();
      } else {
         this.initScheduledPage();
      }

      this.updateCardLayout();
      this.addSectionSidebar();
   }

   private void initMain() {
      this.addCategory("performance", -13703542L, "ui.duckmc.release_one", ScreenRoute.PERFORMANCE);
      this.addCategory("pvp", -50340L, "ui.duckmc.release_two", ScreenRoute.PVP);
      this.addCategory("content", -13653761L, "ui.duckmc.release_three", ScreenRoute.CONTENT);
      this.addCategory("advantages", -15058L, "ui.duckmc.release_four", ScreenRoute.ADVANTAGES);
      Button settings = new GoldSettingsButton(
         this.home.settings(), Component.translatable("ui.duckmc.settings"), button -> this.router.open(ScreenRoute.SETTINGS)
      );
      this.addFixed(settings);
      if (!this.cards.isEmpty()) {
         this.setInitialFocus((GuiEventListener)this.cards.getFirst());
      }
   }

   private void initSettings() {
      SettingsHubLayout layout = SettingsHubLayout.fit(this.contentX, this.contentWidth, this.height);
      Bounds back = (Bounds)layout.buttons().getFirst();
      this.addFixed(
         SectionButton.create(Component.translatable("gui.back"), button -> this.router.open(ScreenRoute.MAIN), SectionPalette.WHITE_GOLD)
            .dimensions(back.x(), back.y(), back.width(), back.height())
            .build()
      );
      this.addSettingsCard(
         layout,
         1,
         Component.translatable("ui.duckmc.edit_appearance"),
         "Menu colors, gradients, opacity and branding.",
         Items.PAINTING,
         () -> this.minecraft.setScreen(new SettingsScreen(this, this.runtime))
      );
      this.addSettingsCard(
         layout,
         2,
         Component.literal("Profiles"),
         "Manage saved DuckMC configuration profiles.",
         Items.CHEST,
         () -> this.minecraft.setScreen(new ProfileScreen(this, this.runtime))
      );
      this.addSettingsCard(
         layout,
         3,
         Component.literal("HUD"),
         "Position and style your PvP HUD elements.",
         Items.SPYGLASS,
         () -> this.minecraft.setScreen(new HudElementsScreen(this, this.runtime))
      );
      this.addSettingsCard(
         layout,
         4,
         Component.translatable("ui.duckmc.foundation_details"),
         "Version, features, credits and license.",
         Items.KNOWLEDGE_BOOK,
         () -> this.minecraft.setScreen(new AboutScreen(this, this.runtime))
      );
      this.setInitialFocus((GuiEventListener)this.fixedWidgets.get(1));
   }

   private void addSettingsCard(SettingsHubLayout layout, int index, Component title, String description, Item icon, Runnable action) {
      Bounds bounds = (Bounds)layout.buttons().get(index);
      this.addFixed(
         new SettingsCard(
            title,
            Component.literal(description),
            new ItemStack(icon),
            (int)this.theme.accent(),
            bounds.x(),
            bounds.y(),
            bounds.width(),
            bounds.height(),
            action
         )
      );
   }

   private void initScheduledPage() {
      Button back = Button.builder(Component.translatable("gui.back"), button -> this.router.open(ScreenRoute.MAIN))
         .bounds(this.contentX, this.height - 34, 92, 22)
         .build();
      this.addFixed(back);
      this.setInitialFocus(back);
   }

   private void addCategory(String key, long accent, String releaseKey, ScreenRoute destination) {
      FeatureCard card = FeatureCard.navigation(
            0,
            0,
            10,
            86,
            Component.translatable("ui.duckmc.category." + key),
            Component.translatable("ui.duckmc.home." + key + ".description"),
            Component.translatable(releaseKey),
            (int)accent,
            this.theme,
            () -> this.router.open(destination)
         )
         .illustrated(key);
      this.cards.add(card);
      this.addRenderableWidget(card);
   }

   private void addFixed(AbstractWidget widget) {
      widget.setMessage(UiText.styled(widget.getMessage(), this.theme));
      this.fixedWidgets.add(widget);
      this.addRenderableWidget(widget);
   }

   @Override
   protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.calculateFrame();
      this.updateCardLayout();
      if (this.route == ScreenRoute.MAIN) {
         this.renderHome(context, mouseX, mouseY, delta);
      } else if (this.route == ScreenRoute.SETTINGS) {
         this.drawHeader(context);

         for (AbstractWidget widget : this.fixedWidgets) {
            widget.render(context, mouseX, mouseY, delta);
         }

         this.renderSectionSidebar(context, mouseX, mouseY, delta);
      } else {
         context.fillGradient(0, 0, this.width, this.height, -1710617326, -1340662745);
         this.drawBackdropDetails(context);
         GlassRenderer.panel(context, this.contentX - 8, 16, this.contentWidth + 16, Math.max(80, this.height - 24), this.theme, true);
         this.drawHeader(context);
         if (this.route == ScreenRoute.MAIN) {
            GlassRenderer.withScissor(context, this.contentX, this.listTop, this.contentX + this.contentWidth, this.listBottom, () -> {
               for (FeatureCard card : this.cards) {
                  card.render(context, mouseX, mouseY, delta);
               }
            });
         } else {
            this.drawScheduledPage(context);
         }

         for (AbstractWidget widget : this.fixedWidgets) {
            widget.render(context, mouseX, mouseY, delta);
         }

         this.renderSectionSidebar(context, mouseX, mouseY, delta);
         this.drawNotice(context);
         if (this.modal != null && this.modal.isOpen()) {
            this.modal.render(context, this.width, this.height, mouseX, mouseY);
         }
      }
   }

   private void renderHome(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      Bounds banner = this.home.banner();
      draw.fillGradient(banner.x(), banner.y(), banner.x() + banner.width(), banner.y() + banner.height(), -15193280, -15984866);
      draw.enableScissor(banner.x() + 1, banner.y() + 1, banner.x() + banner.width() - 1, banner.y() + banner.height() - 1);
      MenuArtwork.image(draw, MenuArtwork.texture("duck_chase"), banner.x(), banner.y(), banner.width(), banner.height(), 3000, 1000, false);
      draw.disableScissor();
      MenuArtwork.border(draw, banner.x(), banner.y(), banner.width(), banner.height(), -1056979666);

      for (FeatureCard card : this.cards) {
         card.render(draw, mouseX, mouseY, delta);
      }

      for (AbstractWidget widget : this.fixedWidgets) {
         widget.render(draw, mouseX, mouseY, delta);
      }

      if (!this.runtime.notice().isBlank()) {
         draw.drawString(
            this.font,
            this.font.plainSubstrByWidth(this.runtime.notice(), Math.max(0, this.home.settings().x() - this.contentX - 12)),
            this.contentX + 4,
            this.home.footerY() + 7,
            -13192
         );
      }
   }

   private void drawBackdropDetails(GuiGraphics context) {
      if (this.route != ScreenRoute.SETTINGS) {
         int accent = (int)this.theme.accent();
         context.fill(0, 0, this.width, 2, accent);
         context.fill(this.width - Math.max(80, this.width / 5), 2, this.width, 3, 1728053247);
         double reveal = UiLayout.reveal(System.nanoTime() - this.openedAt, this.theme.animationSpeed(), this.theme.reducedMotion());
         context.fill(this.contentX, 58, this.contentX + (int)(this.contentWidth * reveal), 59, accent);
      }
   }

   private void drawHeader(GuiGraphics context) {
      Component brand = this.theme.branding() ? Component.literal(this.theme.name()) : Component.translatable("ui.duckmc.title");
      context.drawString(this.font, UiText.styled(brand, this.theme), this.contentX + 4, this.route == ScreenRoute.SETTINGS ? 12 : 29, (int)this.theme.accent());
      context.drawString(
         this.font,
         Component.translatable(this.route == ScreenRoute.MAIN ? "ui.duckmc.foundation_subtitle" : "ui.duckmc.route_subtitle"),
         this.contentX + 4,
         this.route == ScreenRoute.SETTINGS ? 25 : 44,
         -5721408,
         false
      );
   }

   private void drawScheduledPage(GuiGraphics context) {
      int panelY = this.listTop;
      int panelHeight = Math.max(72, this.listBottom - this.listTop);
      int accent = this.categoryAccent(this.route);
      GlassRenderer.panel(context, this.contentX, panelY, this.contentWidth, panelHeight, this.theme, false);
      context.fill(this.contentX, panelY, this.contentX + 4, panelY + panelHeight, accent);
      context.drawString(this.font, this.getTitle(), this.contentX + 18, panelY + 18, -657414);
      context.drawWordWrap(
         this.font, Component.translatable("ui.duckmc.scheduled_page"), this.contentX + 18, panelY + 43, this.contentWidth - 36, -4668979, false
      );
   }

   private void drawNotice(GuiGraphics context) {
      if (!this.runtime.notice().isBlank()) {
         int noticeWidth = Math.min(this.contentWidth, this.font.width(this.runtime.notice()) + 16);
         int noticeX = this.contentX + (this.contentWidth - noticeWidth) / 2;
         int noticeY = 65;
         GlassRenderer.pill(context, noticeX, noticeY, noticeWidth, 18, -866372842, -19641);
         String clipped = this.font.plainSubstrByWidth(this.runtime.notice(), noticeWidth - 12);
         context.drawString(this.font, clipped, noticeX + 6, noticeY + 5, -8014);
      }
   }

   @Override
   protected boolean scrollContents(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.hasOpenModal()) {
         return true;
      } else if (this.route == ScreenRoute.MAIN
         && mouseX >= this.contentX
         && mouseX < this.contentX + this.contentWidth
         && mouseY >= this.listTop
         && mouseY < this.listBottom
         && this.maxScroll > 0) {
         this.scrollY = clamp(this.scrollY - verticalAmount * 24.0, 0.0, this.maxScroll);
         this.updateCardLayout();
         return true;
      } else {
         return super.scrollContents(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   @Override
   protected boolean clickContents(MouseButtonEvent click, boolean doubled) {
      if (this.hasOpenModal()) {
         boolean handled = this.modal.mouseClicked(click, doubled);
         this.restoreModalFocus();
         return handled;
      }

      if (this.route != ScreenRoute.MAIN || !(click.y() < this.listTop) && !(click.y() >= this.listBottom)) {
         boolean handled = super.clickContents(click, doubled);
         if (this.hasOpenModal()) {
            this.setFocused(null);
         }

         return handled;
      } else {
         for (AbstractWidget widget : this.fixedWidgets) {
            if (widget.mouseClicked(click, doubled)) {
               this.setFocused(widget);
               return true;
            }
         }

         return false;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.modal != null && this.modal.isOpen()) {
         boolean handled = this.modal.keyPressed(input);
         this.restoreModalFocus();
         return handled;
      }

      if (input.key() == 256) {
         if (this.route == ScreenRoute.MAIN) {
            this.router.close();
         } else {
            this.router.open(ScreenRoute.MAIN);
         }

         return true;
      } else if (this.route == ScreenRoute.MAIN && input.key() == 258) {
         List<AbstractWidget> order = new ArrayList<>(this.cards);
         order.addAll(this.fixedWidgets);
         int direction = (input.modifiers() & 1) != 0 ? -1 : 1;
         int index = order.indexOf(this.getFocused());
         this.setFocused((GuiEventListener)order.get(Math.floorMod(index + direction, order.size())));
         this.ensureFocusedCardVisible();
         return true;
      } else {
         boolean handled = super.keyPressed(input);
         this.ensureFocusedCardVisible();
         return handled;
      }
   }

   public void onClose() {
      if (this.route == ScreenRoute.MAIN) {
         this.router.close();
      } else {
         this.router.open(ScreenRoute.MAIN);
      }
   }

   public boolean isPauseScreen() {
      return true;
   }

   private void restoreModalFocus() {
      if (this.modal != null && !this.modal.isOpen()) {
         this.modal = null;
         this.setFocused(this.focusBeforeModal);
         this.focusBeforeModal = null;
         this.triggerImmediateNarration(true);
      }
   }

   public Component getNarrationMessage() {
      return this.hasOpenModal() ? this.modal.narration() : super.getNarrationMessage();
   }

   protected void updateNarratedWidget(NarrationElementOutput builder) {
      if (this.hasOpenModal()) {
         builder.add(NarratedElementType.USAGE, Component.translatable("ui.duckmc.modal_usage"));
      } else {
         super.updateNarratedWidget(builder);
      }
   }

   public ScreenRoute route() {
      return this.route;
   }

   public List<FeatureCard> cards() {
      return List.copyOf(this.cards);
   }

   public boolean hasOpenModal() {
      return this.modal != null && this.modal.isOpen();
   }

   private void calculateFrame() {
      if (this.route == ScreenRoute.MAIN) {
         this.home = HomeLayout.fit(this.width, this.height);
         this.contentWidth = this.home.banner().width();
         this.contentX = this.home.banner().x();
         this.listTop = this.home.cardY();
         this.listBottom = this.listTop + this.home.cardHeight();
         this.maxScroll = 0;
      } else if (this.route == ScreenRoute.SETTINGS) {
         this.contentX = 8;
         this.contentWidth = this.width - 16;
      } else {
         this.contentWidth = Math.min(760, Math.max(160, this.width - 24 - 16));
         this.contentX = (this.width - this.contentWidth) / 2;
         this.listTop = this.runtime.notice().isBlank() ? 66 : 88;
         this.listBottom = Math.max(this.listTop + 1, this.height - 44);
      }
   }

   private void updateCardLayout() {
      if (!this.cards.isEmpty()) {
         if (this.route == ScreenRoute.MAIN) {
            for (int i = 0; i < this.cards.size(); i++) {
               Bounds bounds = this.home.card(i);
               this.cards.get(i).setRectangle(bounds.width(), bounds.height(), bounds.x(), bounds.y());
               this.cards.get(i).visible = true;
            }
         } else {
            int columns = this.contentWidth >= 340 ? 2 : 1;
            int cardWidth = (this.contentWidth - (columns - 1) * 10) / columns;
            int rows = (this.cards.size() + columns - 1) / columns;
            int cardHeight = columns == 2 ? Math.max(60, Math.min(86, (this.listBottom - this.listTop - 10) / 2)) : 86;
            int contentHeight = rows * cardHeight + Math.max(0, rows - 1) * 10;
            this.maxScroll = Math.max(0, contentHeight - Math.max(1, this.listBottom - this.listTop));
            this.scrollY = clamp(this.scrollY, 0.0, this.maxScroll);

            for (int i = 0; i < this.cards.size(); i++) {
               int column = i % columns;
               int row = i / columns;
               FeatureCard card = this.cards.get(i);
               card.setRectangle(
                  cardWidth, cardHeight, this.contentX + column * (cardWidth + 10), this.listTop + row * (cardHeight + 10) - (int)Math.round(this.scrollY)
               );
               card.visible = card.getBottom() > this.listTop && card.getY() < this.listBottom;
            }
         }
      }
   }

   private void ensureFocusedCardVisible() {
      if (this.getFocused() instanceof FeatureCard card && this.maxScroll > 0) {
         if (card.getY() < this.listTop) {
            this.scrollY = Math.max(0.0, this.scrollY - (this.listTop - card.getY()));
         } else if (card.getBottom() > this.listBottom) {
            this.scrollY = Math.min(this.maxScroll, this.scrollY + (card.getBottom() - this.listBottom));
         }

         this.updateCardLayout();
      }
   }

   private int categoryAccent(ScreenRoute destination) {
      return switch (destination) {
         case PERFORMANCE -> (int)this.theme.categories().performance();
         case PVP -> (int)this.theme.categories().pvp();
         case CONTENT -> (int)this.theme.categories().content();
         case ADVANTAGES -> (int)this.theme.categories().advantages();
         case MAIN, SETTINGS -> (int)this.theme.accent();
         default -> throw new MatchException(null, null);
      };
   }

   private static String titleKey(ScreenRoute route) {
      return "ui.duckmc.route." + route.name().toLowerCase(Locale.ROOT);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(value, max));
   }
}
