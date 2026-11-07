package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.content.CancellationToken;
import io.duckmc.content.ContentBrowserModel;
import io.duckmc.content.ContentCatalog;
import io.duckmc.content.ContentGridLayout;
import io.duckmc.content.ContentProvider;
import io.duckmc.content.ContentResult;
import io.duckmc.content.SearchQuery;
import io.duckmc.content.ContentBrowserModel.State;
import io.duckmc.content.ContentBrowserModel.Status;
import io.duckmc.content.ContentGridLayout.Rect;
import io.duckmc.content.ContentResult.Kind;
import io.duckmc.content.DownloadManager.Handle;
import io.duckmc.ui.widget.ContentResultCard;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.SectionButton;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;

@Environment(EnvType.CLIENT)
public final class ContentScreen extends ContentPage {
   private final ContentBrowserModel model;
   private State rendered;
   private String source = "Modrinth";
   private String search = "";
   private String version = "1.21.11";
   private String category = "";
   private String sort = "downloads";
   private String notice = "";
   private List<String> categories = List.of();
   private CancellationToken categoryToken = new CancellationToken();
   private int first;
   private boolean open;
   private int gridScroll;
   private boolean filtersOpen;
   private boolean categoriesOpen;
   private int categoryRow;
   private boolean previousBatchTail;
   private final ContentThumbnails thumbnails;
   private final Function<String, ContentProvider> listingProvider;

   private int gridTop() {
      return this.filtersOpen ? 156 : 124;
   }

   public ContentScreen(DuckMcRuntime runtime) {
      this(runtime, name -> runtime.content().provider(name));
   }

   public ContentScreen(DuckMcRuntime runtime, Function<String, ContentProvider> provider) {
      this(runtime, provider, runtime.content()::preview);
   }

   public ContentScreen(DuckMcRuntime runtime, Function<String, ContentProvider> provider, Function<URI, Handle> imageLoader) {
      super(runtime, "Worlds & Resource Packs", null);
      this.thumbnails = new ContentThumbnails(runtime, imageLoader);
      this.listingProvider = provider;
      this.model = new ContentBrowserModel(task -> Minecraft.getInstance().execute(task));
   }

   private static void tab(Button button, boolean selected) {
      ((SectionButton)button).markSelected(selected);
   }

   protected void init() {
      this.layoutPage();
      if (!this.open) {
         this.open = true;
         this.openListing();
      }

      this.rendered = this.model.state();
      tab(this.button("Worlds", this.x, 44, this.w / 3 - 2, () -> this.changeKind(Kind.WORLD)), this.model.query().kind() == Kind.WORLD);
      tab(
         this.button("Resource Packs", this.x + this.w / 3 + 2, 44, this.w / 3 - 2, () -> this.changeKind(Kind.RESOURCE_PACK)),
         this.model.query().kind() == Kind.RESOURCE_PACK
      );

      for (int i = 0; i < 2; i++) {
         String name = i == 0 ? "Modrinth" : "CurseForge";
         if (i != 0 || this.model.query().kind() != Kind.WORLD) {
            Button choice = this.button(name, this.x + i * (this.w / 4), 96, this.w / 4 - 2, () -> {
               if (!this.source.equals(name)) {
                  this.source = name;
                  this.reset();
                  this.openListing();
                  this.rebuild();
               }
            });
            choice.active = this.model.query().kind() != Kind.WORLD;
            tab(choice, this.source.equals(name));
         }
      }

      this.button("My Packs", this.x + 2 * this.w / 3 + 2, 44, this.w / 3 - 2, () -> this.minecraft.setScreen(new ManagedPacksScreen(this.runtime, this)));
      tab(
         this.button(this.category.isEmpty() ? "Categories" : pretty(this.category), this.x + this.w / 2, 96, this.w / 4 - 2, this::toggleCategories),
         this.categoriesOpen || !this.category.isEmpty()
      );
      this.button("Filters", this.x + 3 * this.w / 4, 96, this.w - 3 * this.w / 4, () -> {
         this.filtersOpen = !this.filtersOpen;
         this.gridScroll = 0;
         this.rebuild();
      });
      NeonField query = new NeonField(this.font, this.x, 70, this.w - 84, 20, this.text("Search query"), -13653761);
      query.setMaxLength(200);
      query.setValue(this.search);
      query.setResponder(value -> this.search = value);
      this.addRenderableWidget(query);
      this.button(this.model.state().status() == Status.LOADING ? "Cancel" : "Search", this.x + this.w - 80, 70, 80, () -> {
         if (this.model.state().status() == Status.LOADING) {
            this.model.close();
         } else {
            this.search(0);
         }

         this.rebuild();
      });
      if (this.filtersOpen) {
         NeonField versions = new NeonField(this.font, this.x, 122, 70, 20, this.text("Minecraft version"), -13653761);
         versions.setMaxLength(20);
         versions.setValue(this.version);
         versions.setResponder(value -> this.version = value);
         this.addRenderableWidget(versions);
         this.button("Sort: " + this.sort, this.x + 74, 122, this.w - 74, () -> {
            List<String> sorts = List.of("relevance", "downloads", "newest", "updated");
            this.sort = sorts.get((sorts.indexOf(this.sort) + 1) % sorts.size());
            this.search(0);
            this.rebuild();
         });
      }

      if (this.previousBatchTail && this.model.state().status() == Status.READY) {
         this.first = Math.max(0, (this.model.state().results().size() - 1) / 4 * 4);
         this.previousBatchTail = false;
      }

      this.first = Math.max(0, Math.min(this.first, Math.max(0, (this.model.state().results().size() - 1) / 4 * 4)));
      List<Rect> positions = ContentGridLayout.cards(this.width, this.height - this.gridTop() - 32, this.gridTop(), this.gridScroll);
      ArrayList<ContentResult> visibleResults = new ArrayList<>();
      if (this.categoriesOpen) {
         this.addCategoryChoices();
      }

      for (int i = 0; !this.categoriesOpen && i < 4 && this.first + i < this.model.state().results().size(); i++) {
         ContentResult result = (ContentResult)this.model.state().results().get(this.first + i);
         Rect r = positions.get(i);
         ContentResultCard card = new ContentResultCard(
            result,
            () -> this.minecraft.setScreen(new ContentDetailsScreen(this.runtime, this, result, this.model.query().minecraftVersion())),
            this.thumbnails,
            r.x(),
            r.y(),
            r.width(),
            r.height()
         );
         card.visible = r.y() >= this.gridTop() && r.y() + r.height() <= this.height - 32;
         this.addRenderableWidget(card);
         if (card.visible) {
            visibleResults.add(result);
         }
      }

      this.thumbnails.show(visibleResults);
      this.button("Back", this.x, this.height - 26, 60, this::onClose);
      this.button("Previous", this.x + this.w - 166, this.height - 26, 80, () -> {
         if (this.first > 0) {
            this.first = Math.max(0, this.first - 4);
            this.gridScroll = 0;
            this.rebuild();
         } else if (this.model.query().offset() > 0) {
            this.search(Math.max(0, this.model.query().offset() - 20));
            this.previousBatchTail = true;
         }

         this.focusCard(0);
      }).active = this.first > 0 || this.model.query().offset() > 0;
      this.button("Next", this.x + this.w - 80, this.height - 26, 80, () -> {
         if (this.first + 4 < this.model.state().results().size()) {
            this.first += 4;
            this.gridScroll = 0;
            this.rebuild();
         } else {
            this.search(Math.min(9980, this.model.query().offset() + 20));
         }

         this.focusCard(0);
      }).active = this.first + 4 < this.model.state().results().size() || this.model.state().results().size() == 20;
   }

   private void focusCard(int index) {
      List<ContentResultCard> cards = this.children().stream().filter(ContentResultCard.class::isInstance).map(ContentResultCard.class::cast).toList();
      if (index >= 0 && index < cards.size()) {
         ContentResultCard card = cards.get(index);
         if (!card.visible) {
            int top = this.gridTop();
            int bottom = this.height - 32;
            this.gridScroll = Math.max(0, this.gridScroll + (card.getY() < top ? card.getY() - top : card.getBottom() - bottom));
            this.rebuild();
            cards = this.children().stream().filter(ContentResultCard.class::isInstance).map(ContentResultCard.class::cast).toList();
            card = cards.get(index);
         }

         this.setFocused(card);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.getFocused() instanceof ContentResultCard focused) {
         List<ContentResultCard> cards = this.children().stream().filter(ContentResultCard.class::isInstance).map(ContentResultCard.class::cast).toList();

         int step = switch (input.key()) {
            case 258 -> (input.modifiers() & 1) != 0 ? -1 : 1;
            case 264 -> 2;
            case 265 -> -2;
            default -> 0;
         };
         int next = cards.indexOf(focused) + step;
         if (step != 0 && next >= 0 && next < cards.size()) {
            this.focusCard(next);
            return true;
         }
      }

      return super.keyPressed(input);
   }

   private void changeKind(Kind kind) {
      this.model.reset(kind);
      this.source = kind == Kind.WORLD ? "CurseForge" : "Modrinth";
      this.reset();
      this.openListing();
      this.rebuild();
   }

   private void reset() {
      this.previousBatchTail = false;
      this.categoryToken.cancel();
      this.categoryToken = new CancellationToken();
      this.model.reset(this.model.query().kind());
      this.categories = List.of();
      this.category = "";
      this.categoriesOpen = false;
      this.categoryRow = 0;
      this.first = 0;
      this.notice = "";
      this.sort = "downloads";
      this.search = "";
      this.gridScroll = 0;
   }

   public void credentialsChanged() {
      this.model.credentialsChanged();
   }

   private void openListing() {
      boolean available = true;
      if (this.source.equals("CurseForge")) {
         try {
            available = !this.runtime.content().credentials().load().isBlank();
         } catch (RuntimeException failure) {
            available = false;
         }
      }

      this.model.open(this.source, this.listingProvider.apply(this.source), this.model.query(), available);
   }

   private void search(int offset) {
      this.previousBatchTail = false;
      this.notice = "";
      this.first = 0;
      if (!this.version.matches("[a-zA-Z0-9.+_-]{1,20}")) {
         this.notice = "Enter a valid Minecraft version.";
      } else {
         this.model
            .search(
               this.listingProvider.apply(this.source),
               new SearchQuery(
                  this.search, this.model.query().kind(), this.version, this.category.isEmpty() ? List.of() : List.of(this.category), this.sort, offset
               )
            );
      }
   }

   private static String pretty(String slug) {
      StringBuilder words = new StringBuilder();

      for (String word : slug.split("[-_]")) {
         if (!word.isEmpty()) {
            words.append(words.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return words.toString();
   }

   private int categoryRows() {
      return Math.max(1, (this.height - this.gridTop() - 32) / 23);
   }

   private void addCategoryChoices() {
      ArrayList<String> choices = new ArrayList<>();
      choices.add("");
      choices.addAll(this.categories);
      if (!this.categories.isEmpty()) {
         int columns = 3;
         int width = (this.w - 8) / columns;
         int rows = this.categoryRows();
         int last = (choices.size() + columns - 1) / columns;
         this.categoryRow = Math.max(0, Math.min(this.categoryRow, last - rows));

         for (int i = this.categoryRow * columns; i < Math.min(choices.size(), (this.categoryRow + rows) * columns); i++) {
            String slug = choices.get(i);
            int row = i / columns - this.categoryRow;
            tab(this.button(slug.isEmpty() ? "All" : pretty(slug), this.x + i % columns * (width + 4), this.gridTop() + row * 23, width, () -> {
               this.category = slug;
               this.categoriesOpen = false;
               this.search(0);
               this.rebuild();
            }), slug.equals(this.category));
         }
      }
   }

   private void toggleCategories() {
      this.categoriesOpen = !this.categoriesOpen;
      this.categoryRow = 0;
      if (this.categoriesOpen && this.categories.isEmpty()) {
         this.loadCategories();
      } else {
         this.notice = "";
      }

      this.rebuild();
   }

   private void loadCategories() {
      this.categoryToken.cancel();
      this.categoryToken = new CancellationToken();
      CancellationToken token = this.categoryToken;
      this.notice = "Loading categories…";
      (this.listingProvider.apply(this.source) instanceof ContentCatalog catalog ? catalog : this.runtime.content().provider(this.source))
         .categories(this.model.query().kind(), token)
         .whenComplete((values, error) -> this.minecraft.execute(() -> {
            if (this.open && !token.isCancelled()) {
               this.categories = error == null ? values : List.of();
               this.notice = error == null ? "" : "Categories unavailable for this source.";
               this.rebuild();
            }
         }));
   }

   public void tick() {
      if (this.rendered != this.model.state()) {
         this.rebuild();
      }
   }

   @Override
   protected boolean scrollContents(double mx, double my, double horizontal, double vertical) {
      if (this.categoriesOpen) {
         this.categoryRow = this.categoryRow - (int)Math.signum(vertical);
         this.rebuild();
         return true;
      } else {
         int row = ((Rect)ContentGridLayout.cards(this.width, this.height - this.gridTop() - 32, this.gridTop(), 0).getFirst()).height() + 8;
         int max = Math.max(0, 2 * row - 8 - (this.height - this.gridTop() - 32));
         this.gridScroll = Math.clamp(this.gridScroll - (int)(vertical * 24.0), 0, max);
         this.rebuild();
         return true;
      }
   }

   @Override
   protected void renderBody(GuiGraphics draw) {
      if (this.categoriesOpen ? !this.notice.isEmpty() : this.model.state().results().isEmpty() || !this.notice.isEmpty()) {
         this.line(draw, this.notice.isEmpty() ? this.model.state().message() : this.notice, this.gridTop() + 4);
      }
   }

   public void removed() {
      this.open = false;
      this.model.close();
      this.thumbnails.close();
      this.categoryToken.cancel();
   }
}
