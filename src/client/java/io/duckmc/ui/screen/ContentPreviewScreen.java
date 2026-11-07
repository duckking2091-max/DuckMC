package io.duckmc.ui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import io.duckmc.DuckMcRuntime;
import io.duckmc.content.PreviewImage;
import io.duckmc.content.DownloadManager.Handle;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ContentPreviewScreen extends ContentPage {
   private final List<URI> gallery;
   private final Function<URI, Handle> loader;
   private int index;
   private int imageWidth;
   private int imageHeight;
   private Identifier texture;
   private Handle handle;
   private long generation;
   private String status = "Select a screenshot.";
   private boolean open;

   public ContentPreviewScreen(DuckMcRuntime runtime, Screen parent, List<URI> gallery) {
      this(runtime, parent, gallery, runtime.content()::preview);
   }

   public ContentPreviewScreen(DuckMcRuntime runtime, Screen parent, List<URI> gallery, Function<URI, Handle> loader) {
      super(runtime, "Content screenshots", parent);
      this.gallery = List.copyOf(gallery);
      this.loader = loader;
   }

   public String status() {
      return this.status;
   }

   protected void init() {
      this.layoutPage();
      this.open = true;
      this.button("Previous", this.x, 44, this.w / 2 - 2, () -> {
         this.index = Math.floorMod(this.index - 1, this.gallery.size());
         this.load();
      });
      this.button("Next", this.x + this.w / 2 + 2, 44, this.w / 2 - 2, () -> {
         this.index = (this.index + 1) % this.gallery.size();
         this.load();
      });
      this.button("Back", this.x, this.height - 26, this.w, this::onClose);
      if (this.handle == null && !this.gallery.isEmpty()) {
         this.load();
      }
   }

   private void load() {
      if (this.handle != null) {
         this.handle.cancel();
      }

      if (this.texture != null) {
         this.minecraft.getTextureManager().release(this.texture);
         this.texture = null;
      }

      long current = ++this.generation;
      this.status = "Loading screenshot " + (this.index + 1) + " / " + this.gallery.size() + "…";
      this.handle = this.loader.apply(this.gallery.get(this.index));
      this.handle.completion().whenComplete((path, error) -> {
         if (error != null) {
            this.minecraft.execute(() -> {
               if (this.open && current == this.generation) {
                  this.status = "Screenshot unavailable.";
               }
            });
         } else {
            this.runtime.content().work(() -> {
               try {
                  return NativeImage.read(PreviewImage.png(path));
               } catch (IOException failure) {
                  throw new CompletionException(failure);
               } finally {
                  try {
                     Files.deleteIfExists(path);
                  } catch (IOException var9) {
                  }
               }
            }).whenComplete((image, failure) -> this.minecraft.execute(() -> {
               if (this.open && current == this.generation) {
                  if (failure != null) {
                     this.status = "Screenshot is invalid or exceeds 2048×2048.";
                  } else {
                     this.imageWidth = image.getWidth();
                     this.imageHeight = image.getHeight();
                     this.texture = Identifier.fromNamespaceAndPath("duckmc", "preview/" + UUID.randomUUID());
                     this.minecraft.getTextureManager().register(this.texture, new DynamicTexture(() -> "DuckMC content preview", image));
                     this.status = "Screenshot " + (this.index + 1) + " / " + this.gallery.size();
                  }
               } else {
                  if (image != null) {
                     image.close();
                  }
               }
            }));
         }
      });
   }

   @Override
   protected void renderBody(GuiGraphics draw) {
      this.line(draw, this.status(), 74);
      if (this.texture != null) {
         double scale = Math.min((double)(this.w - 12) / this.imageWidth, (double)(this.height - 126) / this.imageHeight);
         int dw = Math.max(1, (int)(this.imageWidth * scale));
         int dh = Math.max(1, (int)(this.imageHeight * scale));
         draw.blit(
            RenderPipelines.GUI_TEXTURED,
            this.texture,
            this.x + (this.w - dw) / 2,
            94,
            0.0F,
            0.0F,
            dw,
            dh,
            this.imageWidth,
            this.imageHeight,
            this.imageWidth,
            this.imageHeight
         );
      }
   }

   public void removed() {
      this.open = false;
      this.generation++;
      if (this.handle != null) {
         this.handle.cancel();
      }

      if (this.texture != null) {
         this.minecraft.getTextureManager().release(this.texture);
         this.texture = null;
      }
   }
}
