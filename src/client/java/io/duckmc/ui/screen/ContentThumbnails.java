package io.duckmc.ui.screen;

import com.mojang.blaze3d.platform.NativeImage;
import io.duckmc.DuckMcRuntime;
import io.duckmc.content.ContentResult;
import io.duckmc.content.PreviewImage;
import io.duckmc.content.DownloadManager.Handle;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ContentThumbnails implements AutoCloseable {
   private final DuckMcRuntime runtime;
   private final Minecraft client = Minecraft.getInstance();
   private final Function<URI, Handle> loader;
   private final Map<ContentThumbnails.Key, ContentThumbnails.Slot> slots = new LinkedHashMap<>();
   private static final int CACHED = 24;
   private final LinkedHashMap<ContentThumbnails.Key, Identifier> cache = new LinkedHashMap<>();
   private long generation;
   private int active;

   public ContentThumbnails(DuckMcRuntime runtime) {
      this(runtime, runtime.content()::preview);
   }

   public ContentThumbnails(DuckMcRuntime runtime, Function<URI, Handle> loader) {
      this.runtime = runtime;
      this.loader = loader;
   }

   public void show(List<ContentResult> visible) {
      List<ContentThumbnails.Key> keys = visible.stream()
         .limit(4L)
         .filter(r -> !r.previews().isEmpty())
         .map(r -> new ContentThumbnails.Key(r.provider(), r.id(), r.previews().stream().distinct().limit(6L).toList()))
         .toList();
      if (!new ArrayList<>(this.slots.keySet()).equals(keys)) {
         this.retire();

         for (ContentThumbnails.Key key : keys) {
            ContentThumbnails.Slot slot = new ContentThumbnails.Slot(key);
            slot.texture = this.cache.remove(key);
            slot.started = slot.texture != null;
            this.slots.put(key, slot);
         }

         while (this.cache.size() > 24) {
            ContentThumbnails.Key oldest = this.cache.keySet().iterator().next();
            this.client.getTextureManager().release(this.cache.remove(oldest));
         }

         this.pump();
      }
   }

   private void pump() {
      for (ContentThumbnails.Slot slot : this.slots.values()) {
         if (this.active >= 2) {
            break;
         }

         if (!slot.started) {
            slot.started = true;
            this.active++;
            long request = this.generation;

            try {
               slot.handle = this.loader.apply(slot.key.images().get(slot.candidate));
               slot.handle.completion().whenComplete((path, error) -> {
                  if (error != null) {
                     cleanup(path);
                     this.client.execute(() -> this.finish(slot, request, null));
                  } else {
                     this.runtime.content().work(() -> {
                        try {
                           return NativeImage.read(PreviewImage.png(path));
                        } catch (IOException failure) {
                           throw new CompletionException(failure);
                        } finally {
                           cleanup(path);
                        }
                     }).whenComplete((image, failure) -> {
                        cleanup(path);
                        this.client.execute(() -> this.finish(slot, request, image));
                     });
                  }
               });
            } catch (RuntimeException error) {
               this.client.execute(() -> this.finish(slot, request, null));
            }
         }
      }
   }

   private void finish(ContentThumbnails.Slot slot, long request, NativeImage image) {
      if (request == this.generation && this.slots.get(slot.key) == slot) {
         this.active--;
         if (image == null) {
            if (slot.retries < 1) {
               slot.retries++;
               slot.started = false;
               slot.handle = null;
            } else if (slot.candidate + 1 < slot.key.images().size()) {
               slot.candidate++;
               slot.retries = 0;
               slot.started = false;
               slot.handle = null;
            } else {
               slot.failed = true;
            }
         }

         if (image != null) {
            Identifier id = Identifier.fromNamespaceAndPath("duckmc", "thumbnail/" + UUID.randomUUID());
            DynamicTexture texture = new DynamicTexture(() -> "DuckMC thumbnail", image);

            try {
               this.client.getTextureManager().register(id, texture);
               slot.texture = id;
            } catch (RuntimeException failure) {
               texture.close();
            }
         }

         this.pump();
      } else {
         if (image != null) {
            image.close();
         }
      }
   }

   private static void cleanup(Path path) {
      if (path != null) {
         try {
            Files.deleteIfExists(path);
         } catch (IOException var2) {
         }
      }
   }

   public Optional<Identifier> image(String provider, String projectId) {
      return this.slots
         .values()
         .stream()
         .filter(s -> s.key.provider().equals(provider) && s.key.id().equals(projectId))
         .map(s -> s.texture)
         .filter(Objects::nonNull)
         .findFirst();
   }

   public boolean loading(String provider, String projectId) {
      return this.slots.values().stream().anyMatch(s -> s.key.provider().equals(provider) && s.key.id().equals(projectId) && s.texture == null && !s.failed);
   }

   public int residentCount() {
      return (int)this.slots.values().stream().filter(s -> s.texture != null).count();
   }

   public int activeCount() {
      return this.active;
   }

   private void retire() {
      this.generation++;
      List<ContentThumbnails.Slot> old = List.copyOf(this.slots.values());
      this.slots.clear();
      this.active = 0;

      for (ContentThumbnails.Slot slot : old) {
         if (slot.handle != null) {
            slot.handle.cancel();
         }

         if (slot.texture != null) {
            this.cache.put(slot.key, slot.texture);
         }
      }
   }

   @Override
   public void close() {
      this.retire();
      this.cache.values().forEach(this.client.getTextureManager()::release);
      this.cache.clear();
   }

   @Environment(EnvType.CLIENT)
   private record Key(String provider, String id, List<URI> images) {
   }

   @Environment(EnvType.CLIENT)
   private static final class Slot {
      final ContentThumbnails.Key key;
      Handle handle;
      Identifier texture;
      boolean started;
      int candidate;
      int retries;
      boolean failed;

      Slot(ContentThumbnails.Key key) {
         this.key = key;
      }
   }
}
