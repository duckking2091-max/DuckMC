package io.duckmc.ui.screen;

import io.duckmc.DuckMcRuntime;
import io.duckmc.content.CancellationToken;
import io.duckmc.content.ContentDetails;
import io.duckmc.content.ContentResult;
import io.duckmc.content.ProviderException;
import io.duckmc.content.ContentDetails.File;
import io.duckmc.content.ContentResult.Kind;
import io.duckmc.content.DownloadManager.Handle;
import io.duckmc.content.DownloadManager.Progress;
import io.duckmc.content.DownloadManager.State;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ContentDetailsScreen extends ContentPage {
   private final ContentResult result;
   private String minecraftVersion = "1.21.11";
   private ContentDetails details;
   private final CancellationToken token = new CancellationToken();
   private CancellationToken importToken = new CancellationToken();
   private Handle download;
   private Path downloaded;
   private boolean requested;
   private boolean open;
   private boolean busy;
   private boolean committingPack;
   private int selected;
   String status = "Loading compatible versions…";

   public ContentDetailsScreen(DuckMcRuntime runtime, Screen parent, ContentResult result) {
      super(runtime, result.title(), parent);
      this.result = result;
   }

   public ContentDetailsScreen(DuckMcRuntime runtime, Screen parent, ContentDetails details) {
      this(runtime, parent, details.result());
      this.details = details;
      this.requested = true;
      this.status = "Download does not install or enable content.";
   }

   public ContentDetailsScreen(DuckMcRuntime runtime, Screen parent, ContentResult result, String version) {
      this(runtime, parent, result);
      this.minecraftVersion = version;
   }

   public ContentDetailsScreen(DuckMcRuntime runtime, Screen parent, ContentDetails details, String version) {
      this(runtime, parent, details);
      this.minecraftVersion = version;
   }

   protected void init() {
      this.layoutPage();
      this.open = true;
      if (!this.requested) {
         this.requested = true;
         this.runtime
            .content()
            .provider(this.result.provider())
            .details(this.result, this.minecraftVersion, this.token)
            .whenComplete((value, error) -> this.minecraft.execute(() -> {
               if (this.open && !this.token.isCancelled()) {
                  if (error == null) {
                     this.details = value;
                     this.status = value.files().isEmpty() ? "No compatible downloadable files available." : "Download does not install or enable content.";
                  } else {
                     this.status = message(error, "Content details are unavailable.");
                  }

                  this.rebuild();
               }
            }));
      }

      File file = this.file();
      this.button(file == null ? "No compatible file" : file.filename(), this.x, 44, this.w, () -> {
         if (this.details != null && !this.details.files().isEmpty() && !this.busy) {
            this.selected = (this.selected + 1) % this.details.files().size();
            this.downloaded = null;
            this.rebuild();
         }
      }).active = file != null && !this.busy;
      this.button("Screenshots", this.x, 124, this.w, () -> this.minecraft.setScreen(new ContentPreviewScreen(this.runtime, this, this.details.gallery()))).active = this.details
            != null
         && !this.details.gallery().isEmpty()
         && !this.busy;
      this.button("Download", this.x, this.height - 78, this.w / 2 - 2, () -> this.start(false)).active = file != null && !this.busy;
      Button installButton = this.button(
         this.result.kind() == Kind.WORLD ? "Import world" : "Install & Enable",
         this.x + this.w / 2 + 2,
         this.height - 78,
         this.w / 2 - 2,
         () -> this.start(true)
      );
      installButton.active = file != null && !this.busy && this.minecraftVersion.equals("1.21.11");
      if (!this.minecraftVersion.equals("1.21.11")) {
         installButton.setTooltip(Tooltip.create(Component.literal("Installation is restricted to Minecraft 1.21.11 content. Downloads remain available.")));
      }

      this.button("Cancel operation", this.x, this.height - 52, this.w, () -> {
         if (!this.committingPack) {
            this.importToken.cancel();
            if (this.download != null) {
               this.download.cancel();
            }

            this.status = "Cancellation requested.";
         }
      }).active = this.busy && !this.committingPack;
      this.button("Back", this.x, this.height - 26, this.w, this::onClose).active = !this.committingPack;
   }

   private File file() {
      return this.details != null && !this.details.files().isEmpty()
         ? (File)this.details.files().get(Math.min(this.selected, this.details.files().size() - 1))
         : null;
   }

   private void start(boolean install) {
      File file = this.file();
      if (file != null && !this.busy) {
         this.importToken = new CancellationToken();
         CancellationToken operation = this.importToken;
         this.busy = true;
         this.rebuild();
         if (this.downloaded != null) {
            this.finishDownload(this.downloaded, install, file);
         } else {
            this.status = "Downloading…";
            this.download = this.runtime.content().download(file);
            this.download.completion().whenComplete((path, error) -> this.minecraft.execute(() -> {
               if (this.open && operation == this.importToken) {
                  if (operation.isCancelled()) {
                     this.busy = false;
                     this.status = "Cancelled. Not installed or enabled.";
                     this.rebuild();
                  } else if (error != null) {
                     this.busy = false;
                     this.status = "Download cancelled or failed validation.";
                     this.rebuild();
                  } else {
                     this.downloaded = path;
                     this.finishDownload(path, install, file);
                  }
               }
            }));
         }
      }
   }

   private void finishDownload(Path path, boolean install, File file) {
      if (this.importToken.isCancelled()) {
         this.busy = false;
         this.status = "Cancelled. Not installed or enabled.";
         this.rebuild();
      } else if (!install) {
         this.busy = false;
         this.status = "Downloaded to duckmc-downloads. Not installed or enabled.";
         this.rebuild();
      } else {
         this.status = this.result.kind() == Kind.WORLD ? "Validating and importing…" : "Installing/reloading; cannot cancel this step. Please wait…";
         if (this.result.kind() == Kind.WORLD) {
            this.runtime
               .content()
               .importWorld(path, this.result.title(), this.importToken)
               .whenComplete(
                  (saved, error) -> this.minecraft
                     .execute(
                        () -> {
                           if (this.open) {
                              this.busy = false;
                              this.status = error == null
                                 ? "Imported as " + saved.getFileName() + ". Open it from Singleplayer."
                                 : "Import cancelled or rejected; existing saves untouched.";
                              this.rebuild();
                           }
                        }
                     )
               );
         } else {
            this.committingPack = true;
            this.rebuild();
            this.runtime.content().install(path, this.result, file).whenComplete((outcome, error) -> this.minecraft.execute(() -> {
               this.committingPack = false;
               this.busy = false;
               if (this.open) {
                  this.status = error == null ? outcome.message() : "Pack installation failed; it was not enabled.";
                  this.rebuild();
               }
            }));
         }
      }
   }

   @Override
   protected void renderBody(GuiGraphics draw) {
      ContentResult metadata = this.details == null ? this.result : this.details.result();
      File file = this.file();
      this.line(draw, "Author: " + metadata.author() + " | Source: " + metadata.provider(), 72);
      this.line(draw, "Minecraft: " + this.minecraftVersion + " | Version: " + (file == null ? "unavailable" : file.version()), 84);
      this.line(draw, "Size: " + (file == null ? "unknown" : String.format(Locale.ROOT, "%.2f MiB", file.size() / 1048576.0)), 96);
      this.line(draw, "License: " + metadata.license().orElse("not provided"), 108);
      String progress = this.status;
      if (this.busy && this.download != null && (this.download.progress().state() == State.DOWNLOADING || this.download.progress().state() == State.VALIDATING)
         )
       {
         Progress p = this.download.progress();
         progress = p.message() + " " + p.receivedBytes() / 1024L + " KiB" + (p.totalBytes() < 0L ? "" : " / " + p.totalBytes() / 1024L + " KiB");
      }

      this.line(draw, progress, this.height - 94);
      if (this.height > 290 && this.details != null) {
         draw.drawWordWrap(
            this.font,
            this.text(
               this.details.description().replaceAll("<[^>]*>", "").substring(0, Math.min(180, this.details.description().replaceAll("<[^>]*>", "").length()))
            ),
            this.x + 6,
            152,
            this.w - 12,
            -4208428,
            false
         );
      }
   }

   private static String message(Throwable error, String fallback) {
      while (error instanceof CompletionException && error.getCause() != null) {
         error = error.getCause();
      }

      return error instanceof ProviderException ? error.getMessage() : fallback;
   }

   public void removed() {
      this.open = false;
      if (this.details == null) {
         this.token.cancel();
      }

      if (this.download != null) {
         this.download.cancel();
      }

      this.importToken.cancel();
   }

   @Override
   public void onClose() {
      if (!this.committingPack) {
         super.onClose();
      }
   }
}
