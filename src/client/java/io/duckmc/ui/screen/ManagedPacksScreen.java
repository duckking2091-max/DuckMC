package io.duckmc.ui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import io.duckmc.DuckMcRuntime;
import io.duckmc.content.CancellationToken;
import io.duckmc.content.ContentUpdates;
import io.duckmc.content.ContentUpdates.Result;
import io.duckmc.content.ContentUpdates.Status;
import io.duckmc.content.ManagedPackStore.Entry;
import io.duckmc.content.ResourcePackService.Outcome;
import io.duckmc.ui.render.NeonUi;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;

@Environment(EnvType.CLIENT)
public final class ManagedPacksScreen extends ContentPage {
   private static final int ROW_HEIGHT = 24;
   private static final int TOP = 40;
   private List<Entry> entries;
   private List<ManagedPacksScreen.Row> rows = List.of();
   private final Map<String, Identifier> icons = new HashMap<>();
   private boolean loading;
   private boolean busy;
   private boolean open;
   private int first;
   private int pageSize = 1;
   private String status = "Loading packs…";
   private CancellationToken token = new CancellationToken();

   public ManagedPacksScreen(DuckMcRuntime runtime, Screen parent) {
      super(runtime, "My Packs", parent);
   }

   private void loadEntries() {
      this.loading = true;
      this.runtime.content().work(() -> {
         try {
            return this.runtime.content().store().entries();
         } catch (IOException error) {
            throw new CompletionException(error);
         }
      }).whenComplete((value, error) -> this.minecraft.execute(() -> {
         this.loading = false;
         if (this.open) {
            this.entries = error == null ? value : List.of();
            if (error != null) {
               this.status = "DuckMC's pack list could not be read.";
            }

            this.refreshRows();
            this.rebuild();
         }
      }));
   }

   private void refreshRows() {
      PackRepository manager = this.minecraft.getResourcePackRepository();
      manager.reload();
      Collection<String> enabled = manager.getSelectedIds();
      ArrayList<ManagedPacksScreen.Row> found = new ArrayList<>();

      for (Pack profile : manager.getAvailablePacks()) {
         String id = profile.getId();
         if (id.startsWith("file/")) {
            Entry entry = this.entries == null ? null : this.entries.stream().filter(e -> e.packId().equals(id)).findFirst().orElse(null);
            found.add(new ManagedPacksScreen.Row(id, profile.getTitle().getString(), profile.getDescription().getString(), enabled.contains(id), entry));
            if (!this.icons.containsKey(id)) {
               this.icons.put(id, this.icon(profile));
            }
         }
      }

      found.sort(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)));
      this.rows = found;
      if (this.status.startsWith("Loading")) {
         this.status = this.rows.isEmpty() ? "No resource packs found." : "";
      }
   }

   private Identifier icon(Pack profile) {
      try {
         PackResources pack = profile.open();

         Object var13;
         label69: {
            Identifier var7;
            try {
               IoSupplier<InputStream> supplier = pack.getRootResource(new String[]{"pack.png"});
               if (supplier == null) {
                  var13 = null;
                  break label69;
               }

               try (InputStream input = (InputStream)supplier.get()) {
                  NativeImage image = NativeImage.read(input);
                  Identifier id = Identifier.fromNamespaceAndPath("duckmc", "pack_icon/" + UUID.randomUUID());
                  this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "DuckMC pack icon", image));
                  var7 = id;
               }
            } catch (Throwable var11) {
               if (pack != null) {
                  try {
                     pack.close();
                  } catch (Throwable var8) {
                     var11.addSuppressed(var8);
                  }
               }

               throw var11;
            }

            if (pack != null) {
               pack.close();
            }

            return var7;
         }

         if (pack != null) {
            pack.close();
         }

         return (Identifier)var13;
      } catch (IOException | RuntimeException unreadable) {
         return null;
      }
   }

   protected void init() {
      this.layoutPage();
      this.open = true;
      if (this.entries == null && !this.loading) {
         this.loadEntries();
      }

      this.pageSize = Math.max(1, (this.height - 40 - 44) / 24);
      this.first = Math.max(0, Math.min(this.first, this.rows.size() - this.pageSize));

      for (int i = this.first; i < Math.min(this.rows.size(), this.first + this.pageSize); i++) {
         ManagedPacksScreen.Row row = this.rows.get(i);
         int y = 40 + (i - this.first) * 24 + 3;
         int right = this.x + this.w - 4;
         if (row.entry() != null) {
            right -= 48;
            this.button("Delete", right, y, 46, () -> this.delete(row)).active = !this.busy;
         }

         right -= 52;
         this.button("Details", right, y, 50, () -> this.details(row)).active = !this.busy;
         right -= 54;
         this.button(row.enabled() ? "Disable" : "Enable", right, y, 52, () -> this.toggle(row)).active = !this.busy;
      }

      this.button("Back", this.x, this.height - 26, 60, this::onClose);
   }

   private void toggle(ManagedPacksScreen.Row row) {
      if (row.entry() != null) {
         this.run(row.enabled() ? this.runtime.content().packs().disable(row.entry().id()) : this.runtime.content().packs().enable(row.entry().id()));
      } else {
         PackRepository manager = this.minecraft.getResourcePackRepository();
         if (row.enabled()) {
            manager.removePack(row.id());
         } else {
            manager.addPack(row.id());
         }

         this.minecraft.options.updateResourcePacks(manager);
         this.status = row.name() + (row.enabled() ? " disabled." : " enabled.");
         this.refreshRows();
         this.rebuild();
      }
   }

   private void details(ManagedPacksScreen.Row row) {
      if (row.entry() != null) {
         this.updates(row.entry());
      } else {
         this.status = row.description().isBlank() ? "No description provided." : row.description();
         this.rebuild();
      }
   }

   private void delete(ManagedPacksScreen.Row row) {
      this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
         this.minecraft.setScreen(this);
         if (confirmed) {
            this.run(this.runtime.content().packs().delete(row.entry().id()));
         }
      }, Component.literal("Remove this pack?"), Component.literal("It will be disabled and moved to resourcepacks/.duckmc-trash.")));
   }

   private void run(CompletableFuture<Outcome> operation) {
      this.busy = true;
      this.status = "Working…";
      this.rebuild();
      operation.whenComplete((outcome, error) -> this.minecraft.execute(() -> {
         this.busy = false;
         this.entries = null;
         this.status = error == null ? outcome.message() : "Operation could not be completed.";
         if (this.open) {
            this.rebuild();
         }
      }));
   }

   private void updates(Entry entry) {
      this.token.cancel();
      this.token = new CancellationToken();
      CancellationToken request = this.token;
      this.busy = true;
      this.status = "Checking compatible provider versions…";
      this.rebuild();
      this.runtime
         .content()
         .provider(entry.provider())
         .details(entry.result(), "1.21.11", request)
         .whenComplete((details, error) -> this.minecraft.execute(() -> {
            if (this.open && !request.isCancelled()) {
               this.busy = false;
               if (error != null) {
                  this.status = "Update check unavailable. No files or selections changed.";
                  this.rebuild();
               } else {
                  ContentDetailsScreen screen = new ContentDetailsScreen(this.runtime, this, details);
                  Result update = ContentUpdates.check(entry.fileId(), details.files());
                  if (update.status() != Status.CURRENT && update.status() != Status.NO_COMPATIBLE_FILE) {
                     screen.status = update.message();
                     this.minecraft.setScreen(screen);
                  } else {
                     this.status = update.message();
                     this.rebuild();
                  }
               }
            }
         }));
   }

   @Override
   protected void renderBody(GuiGraphics draw) {
      for (int i = this.first; i < Math.min(this.rows.size(), this.first + this.pageSize); i++) {
         ManagedPacksScreen.Row row = this.rows.get(i);
         int y = 40 + (i - this.first) * 24;
         NeonUi.row(draw, this.x, y, this.w, 22, -13653761, 0.0F);
         Identifier icon = this.icons.get(row.id());
         if (icon != null) {
            draw.blit(RenderPipelines.GUI_TEXTURED, icon, this.x + 5, y + 2, 0.0F, 0.0F, 18, 18, 18, 18);
         } else {
            draw.fillGradient(this.x + 5, y + 2, this.x + 23, y + 20, -14001002, -15717816);
            draw.drawCenteredString(this.font, "P", this.x + 14, y + 7, -1);
         }

         int textWidth = this.w - 190 - (row.entry() != null ? 48 : 0);
         draw.drawString(this.font, this.font.plainSubstrByWidth(row.name(), Math.max(20, textWidth)), this.x + 28, y + 3, -1);
         String detail = (row.enabled() ? "Enabled" : "Disabled") + (row.entry() != null ? " · " + row.entry().provider() : "");
         draw.drawString(
            this.font, this.font.plainSubstrByWidth(detail, Math.max(20, textWidth)), this.x + 28, y + 12, row.enabled() ? -7549953 : -7629407, false
         );
      }

      draw.drawString(this.font, this.font.plainSubstrByWidth(this.status, this.w - 12), this.x + 6, this.height - 38, -3351058, false);
   }

   @Override
   protected boolean scrollContents(double mx, double my, double h, double v) {
      int next = Math.max(0, Math.min(this.first - (int)Math.signum(v), this.rows.size() - this.pageSize));
      if (next != this.first) {
         this.first = next;
         this.rebuild();
      }

      return true;
   }

   public void removed() {
      this.open = false;
      this.token.cancel();
      this.icons.values().stream().filter(Objects::nonNull).forEach(id -> this.minecraft.getTextureManager().release(id));
      this.icons.clear();
      this.entries = null;
   }

   @Environment(EnvType.CLIENT)
   private record Row(String id, String name, String description, boolean enabled, Entry entry) {
   }
}
