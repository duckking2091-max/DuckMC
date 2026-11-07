package io.duckmc.ui.screen;

import com.google.gson.JsonObject;
import io.duckmc.DuckMcRuntime;
import io.duckmc.ui.model.SectionPalette;
import io.duckmc.ui.render.NeonUi;
import io.duckmc.ui.widget.NeonField;
import io.duckmc.ui.widget.ProfileCard;
import io.duckmc.ui.widget.SectionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ProfileScreen extends DuckUiScreen {
   private static final int CARD_HEIGHT = 58;
   private static final int GAP = 6;
   private static final int GRID_TOP = 34;
   private final Screen parent;
   private final DuckMcRuntime runtime;
   private final List<ProfileCard> cards = new ArrayList<>();
   private final List<Button> dots = new ArrayList<>();
   private ProfileScreen.Dialog dialog = ProfileScreen.Dialog.NONE;
   private String selected;
   private String target = "";
   private String pendingImport = "";
   private String message = "";
   private boolean copyCurrent;
   private int scroll;
   private int contentHeight;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private NeonField nameField;
   private NeonField descriptionField;

   public ProfileScreen(Screen parent, DuckMcRuntime runtime) {
      super(Component.literal("Menu Profiles"), runtime);
      this.parent = parent;
      this.runtime = runtime;
      this.selected = runtime.config().activeProfile();
   }

   protected void init() {
      this.prepareLayout();
      this.cards.clear();
      this.dots.clear();
      this.nameField = null;
      this.descriptionField = null;
      if (!this.runtime.config().profiles().containsKey(this.selected)) {
         this.selected = this.runtime.config().activeProfile();
      }

      if (this.dialog == ProfileScreen.Dialog.NONE) {
         this.initGrid();
      } else {
         this.initDialog();
      }
   }

   private SectionButton button(String label, int x, int y, int w, Runnable action) {
      return SectionButton.create(Component.literal(label), b -> action.run(), SectionPalette.GOLD).dimensions(x, y, w, 20).build();
   }

   private void initGrid() {
      boolean writable = !this.runtime.settings().readOnly();
      int x = 12;
      int w = this.width - 24;
      int create = 136;
      ((SectionButton)this.addRenderableWidget(this.button("+ Create New Profile", x + w - create, 8, create, this::startCreate))).active = writable;
      int columns = Math.max(1, Math.min(3, (w + 6) / 132));
      int cardWidth = (w - 6 * (columns - 1)) / columns;
      int index = 0;
      String active = this.runtime.config().activeProfile();

      for (Entry<String, JsonObject> entry : this.runtime.config().profiles().entrySet()) {
         String name = entry.getKey();
         JsonObject settings = entry.getValue();
         ProfileCard card = new ProfileCard(
            name,
            settings.get("profile.description").getAsString(),
            settings.get("profile.modified").getAsLong(),
            name.equals(active),
            name.equals(this.selected),
            cardWidth,
            58,
            () -> {
               this.selected = name;
               this.message = "";
               this.rebuild();
            }
         );
         card.setX(x + index % columns * (cardWidth + 6));
         this.cards.add((ProfileCard)this.addRenderableWidget(card));
         SectionButton more = this.button("...", 0, 0, 20, () -> this.openOptions(name));
         more.setSize(18, 14);
         more.setX(card.getRight() - 22);
         this.dots.add((Button)this.addRenderableWidget(more));
         index++;
      }

      int rows = (this.cards.size() + columns - 1) / columns;
      this.contentHeight = rows * 64;
      this.layoutGrid(columns);
      int y = this.height - 28;
      this.addRenderableWidget(this.button("Back", x, y, 58, this::onClose));
      int third = (w - 66 - 8) / 3;
      ((SectionButton)this.addRenderableWidget(this.button("Import from Clipboard", x + 64, y, third, this::importClipboard))).active = writable;
      this.addRenderableWidget(this.button("Export Selected", x + 68 + third, y, third, this::exportSelected));
      SectionButton load = this.button("Load Selected Profile", x + 72 + 2 * third, y, w - 72 - 2 * third, this::load);
      load.active = writable && !this.selected.equals(active);
      load.markSelected(load.active);
      this.addRenderableWidget(load);
   }

   private void layoutGrid(int columns) {
      int view = this.height - 62 - 34;
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.contentHeight - view)));

      for (int i = 0; i < this.cards.size(); i++) {
         int y = 34 + i / columns * 64 - this.scroll;
         boolean shown = y >= 34 && y + 58 <= 34 + view;
         this.cards.get(i).setY(y);
         this.cards.get(i).visible = shown;
         this.dots.get(i).setY(y + 4);
         this.dots.get(i).visible = shown;
      }
   }

   private void startCreate() {
      this.copyCurrent = false;
      this.show(ProfileScreen.Dialog.CREATE);
   }

   private void openOptions(String name) {
      this.target = name;
      this.selected = name;
      this.show(ProfileScreen.Dialog.OPTIONS);
   }

   private void show(ProfileScreen.Dialog next) {
      this.dialog = next;
      this.message = "";
      this.rebuild();
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   private NeonField field(String label, int y, int limit, String value) {
      NeonField field = new NeonField(this.font, this.panelX + 12, y, this.panelW - 24, 18, Component.literal(label), -15058);
      field.setMaxLength(limit);
      field.setValue(value);
      return (NeonField)this.addRenderableWidget(field);
   }

   private void initDialog() {
      this.panelW = Math.min(300, this.width - 24);

      this.panelH = switch (this.dialog) {
         case CREATE -> 158;
         case OPTIONS -> 136;
         case EDIT -> 134;
         case DUPLICATE -> 100;
         default -> 92;
      };
      this.panelH = Math.min(this.panelH, this.height - 8);
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      int x = this.panelX + 12;
      int w = this.panelW - 24;
      int half = (w - 6) / 2;
      int bottom = this.panelY + this.panelH - 28;
      JsonObject settings = (JsonObject)this.runtime.config().profiles().get(this.target);
      switch (this.dialog) {
         case CREATE:
            this.nameField = this.field("Profile name", this.panelY + 38, 40, "");
            this.setFocused(this.nameField);
            this.descriptionField = this.field("Description", this.panelY + 74, 80, "");
            this.addRenderableWidget(this.button(this.copyCurrent ? "Start: Copy current profile" : "Start: Default settings", x, this.panelY + 100, w, () -> {
               this.copyCurrent = !this.copyCurrent;
               String name = this.nameField.getValue();
               String about = this.descriptionField.getValue();
               this.rebuild();
               this.nameField.setValue(name);
               this.descriptionField.setValue(about);
            }));
            this.addRenderableWidget(this.button("Cancel", x, bottom, half, () -> this.show(ProfileScreen.Dialog.NONE)));
            this.addRenderableWidget(
               this.button(
                  "Create",
                  x + half + 6,
                  bottom,
                  half,
                  () -> this.act(
                     () -> this.runtime.settings().createProfile(this.nameField.getValue().trim(), this.descriptionField.getValue().trim(), this.copyCurrent),
                     "Profile created. Load it when you are ready."
                  )
               )
            );
            break;
         case OPTIONS:
            boolean writable = !this.runtime.settings().readOnly();
            boolean isDefault = this.target.equals("Default");
            boolean active = this.target.equals(this.runtime.config().activeProfile());
            ((SectionButton)this.addRenderableWidget(this.button("Edit Details", x, this.panelY + 28, half, () -> this.show(ProfileScreen.Dialog.EDIT)))).active = writable;
            ((SectionButton)this.addRenderableWidget(
                  this.button("Duplicate", x + half + 6, this.panelY + 28, half, () -> this.show(ProfileScreen.Dialog.DUPLICATE))
               ))
               .active = writable;
            this.addRenderableWidget(this.button("Export", x, this.panelY + 54, half, this::exportSelected));
            ((SectionButton)this.addRenderableWidget(this.button("Delete", x + half + 6, this.panelY + 54, half, () -> this.show(ProfileScreen.Dialog.DELETE))))
               .active = writable && !isDefault && !active;
            this.addRenderableWidget(this.button("Close", x, bottom, w, () -> this.show(ProfileScreen.Dialog.NONE)));
            break;
         case EDIT:
            this.nameField = this.field("Profile name", this.panelY + 38, 40, this.target);
            this.nameField.setEditable(!this.target.equals("Default"));
            this.descriptionField = this.field("Description", this.panelY + 74, 80, settings.get("profile.description").getAsString());
            this.addRenderableWidget(this.button("Cancel", x, bottom, half, () -> this.show(ProfileScreen.Dialog.NONE)));
            this.addRenderableWidget(this.button("Save", x + half + 6, bottom, half, () -> this.act(() -> {
               String name = this.nameField.getValue().trim();
               this.runtime.settings().editProfile(this.target, name, this.descriptionField.getValue().trim());
               this.selected = name;
            }, "Profile updated.")));
            break;
         case DUPLICATE:
            this.nameField = this.field("New profile name", this.panelY + 38, 40, "");
            this.setFocused(this.nameField);
            this.addRenderableWidget(this.button("Cancel", x, bottom, half, () -> this.show(ProfileScreen.Dialog.NONE)));
            this.addRenderableWidget(
               this.button(
                  "Duplicate",
                  x + half + 6,
                  bottom,
                  half,
                  () -> this.act(() -> this.runtime.settings().duplicateProfile(this.target, this.nameField.getValue().trim()), "Profile duplicated.")
               )
            );
            break;
         case DELETE:
            this.addRenderableWidget(this.button("Cancel", x, bottom, half, () -> this.show(ProfileScreen.Dialog.NONE)));
            this.addRenderableWidget(
               this.button(
                  "Confirm Delete", x + half + 6, bottom, half, () -> this.act(() -> this.runtime.settings().deleteProfile(this.target), "Profile deleted.")
               )
            );
            break;
         case REPLACE:
            this.addRenderableWidget(this.button("Cancel", x, bottom, half, () -> this.show(ProfileScreen.Dialog.NONE)));
            this.addRenderableWidget(this.button("Replace", x + half + 6, bottom, half, () -> this.act(() -> {
               this.runtime.settings().importProfile(this.pendingImport, true);
               this.runtime.performance().sync();
            }, "Profile replaced by the import.")));
            break;
         default:
            throw new IllegalStateException();
      }
   }

   private void act(Runnable action, String success) {
      try {
         action.run();
         this.runtime.settings().flush();
         this.dialog = ProfileScreen.Dialog.NONE;
         this.pendingImport = "";
         this.message = success;
         this.rebuild();
      } catch (IllegalArgumentException | IllegalStateException exception) {
         this.message = exception.getMessage();
      }
   }

   private void load() {
      String name = this.selected;
      this.act(() -> {
         this.runtime.settings().switchProfile(name);
         this.runtime.performance().sync();
      }, "Loaded " + name + ".");
   }

   private void exportSelected() {
      this.minecraft.keyboardHandler.setClipboard(this.runtime.settings().exportProfile(this.selected));
      this.dialog = ProfileScreen.Dialog.NONE;
      this.message = "Copied " + this.selected + " to the clipboard.";
      this.rebuild();
   }

   private void importClipboard() {
      String text = this.minecraft.keyboardHandler.getClipboard();

      try {
         if (text.length() > 1048576) {
            throw new IllegalArgumentException("Profile is too large");
         }

         this.target = this.runtime.settings().importedProfileName(text);
         if (this.runtime.config().profiles().containsKey(this.target)) {
            this.pendingImport = text;
            this.show(ProfileScreen.Dialog.REPLACE);
            return;
         }
      } catch (IllegalArgumentException | IllegalStateException exception) {
         this.message = "Clipboard does not hold a valid profile.";
         return;
      }

      this.act(() -> this.runtime.settings().importProfile(text, false), "Imported " + this.target + ".");
   }

   @Override
   protected void renderContents(GuiGraphics draw, int mouseX, int mouseY, float delta) {
      draw.drawString(this.font, this.getTitle(), 14, 14, -15058);
      String status = !this.runtime.settings().error().isBlank() ? this.runtime.settings().error() : this.message;
      if (this.dialog != ProfileScreen.Dialog.NONE) {
         draw.fill(2, 2, this.width - 2, this.height - 2, -1341781486);
         NeonUi.panel(draw, this.panelX, this.panelY, this.panelW, this.panelH, -15058);
         draw.drawCenteredString(this.font, this.dialogTitle(), this.width / 2, this.panelY + 10, -15058);
         int x = this.panelX + 12;
         int w = this.panelW - 24;
         if (this.nameField != null) {
            draw.drawString(this.font, "Name", x, this.panelY + 28, -4208428, false);
         }

         if (this.descriptionField != null) {
            draw.drawString(this.font, "Description (optional)", x, this.panelY + 64, -4208428, false);
         }

         if (this.dialog == ProfileScreen.Dialog.DELETE) {
            draw.drawWordWrap(this.font, Component.literal("This permanently removes its saved settings."), x, this.panelY + 28, w, -9824, false);
         }

         if (this.dialog == ProfileScreen.Dialog.REPLACE) {
            draw.drawWordWrap(
               this.font, Component.literal("A profile with this name exists. Its saved settings will be replaced."), x, this.panelY + 28, w, -9824, false
            );
         }

         if (this.dialog == ProfileScreen.Dialog.OPTIONS && this.target.equals("Default")) {
            draw.drawString(this.font, "Default is protected.", x, this.panelY + 82, -4208428, false);
         } else if (this.dialog == ProfileScreen.Dialog.OPTIONS && this.target.equals(this.runtime.config().activeProfile())) {
            draw.drawString(this.font, "Load another profile to delete this one.", x, this.panelY + 82, -4208428, false);
         }

         if (!status.isEmpty()) {
            draw.drawString(this.font, this.font.plainSubstrByWidth(status, w), x, this.panelY + this.panelH - 40, -25974, false);
         }
      } else {
         NeonUi.inset(draw, 10, 31, this.width - 20, this.height - 62 - 34 + 6, -15058);
         if (!status.isEmpty()) {
            draw.drawString(this.font, this.font.plainSubstrByWidth(status, this.width - 28), 14, this.height - 39, -8014, false);
         }
      }

      super.renderContents(draw, mouseX, mouseY, delta);
   }

   private String dialogTitle() {
      return switch (this.dialog) {
         case CREATE -> "Create New Profile";
         default -> this.target;
         case EDIT -> "Rename Profile";
         case DUPLICATE -> "Duplicate " + this.target;
         case DELETE -> "Delete " + this.target + "?";
         case REPLACE -> "Replace " + this.target + "?";
      };
   }

   @Override
   protected boolean scrollContents(double mx, double my, double h, double v) {
      if (this.dialog == ProfileScreen.Dialog.NONE && !this.cards.isEmpty()) {
         this.scroll -= (int)(v * 30.0);
         int first = this.cards.getFirst().getX();
         int columns = (int)this.cards.stream().filter(card -> card.getX() >= first).map(AbstractWidget::getX).distinct().count();
         this.layoutGrid(columns);
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.key() == 256 && this.dialog != ProfileScreen.Dialog.NONE) {
         this.show(ProfileScreen.Dialog.NONE);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   public void select(String name) {
      this.selected = name;
      this.rebuild();
   }

   public void options(String name) {
      this.openOptions(name);
   }

   public NeonField nameField() {
      return this.nameField;
   }

   public void onClose() {
      this.runtime.settings().cancelReset();
      this.runtime.settings().flush();
      this.minecraft.setScreen(this.parent);
   }

   @Environment(EnvType.CLIENT)
   private enum Dialog {
      NONE,
      CREATE,
      OPTIONS,
      EDIT,
      DUPLICATE,
      DELETE,
      REPLACE;
   }
}
