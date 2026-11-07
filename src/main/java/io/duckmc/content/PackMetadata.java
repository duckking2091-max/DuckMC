package io.duckmc.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.duckmc.config.StrictJson;
import io.duckmc.storage.ArchiveInspector;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public record PackMetadata(int minimumMajor, int minimumMinor, int maximumMajor, int maximumMinor, String description) {
   public boolean compatible(int major, int minor) {
      return compare(major, minor, this.minimumMajor, this.minimumMinor) >= 0 && compare(major, minor, this.maximumMajor, this.maximumMinor) <= 0;
   }

   private static int compare(int a, int b, int c, int d) {
      int major = Integer.compare(a, c);
      return major != 0 ? major : Integer.compare(b, d);
   }

   public static PackMetadata read(Path archive) throws IOException {
      return read(archive, new CancellationToken());
   }

   public static PackMetadata read(Path archive, CancellationToken token) throws IOException {
      new ArchiveInspector().inspect(archive, token);

      try (ZipFile zip = new ZipFile(archive.toFile())) {
         ZipEntry entry = zip.getEntry("pack.mcmeta");
         if (entry == null || entry.isDirectory() || entry.getSize() > 65536L) {
            throw new IOException("Root pack.mcmeta missing or oversized");
         }

         byte[] data;
         try (InputStream in = zip.getInputStream(entry)) {
            data = in.readNBytes(65537);
         }

         if (data.length > 65536) {
            throw new IOException("Pack metadata too large");
         }

         String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
         JsonObject pack = StrictJson.object(json).getAsJsonObject("pack");
         if (pack == null || !pack.has("description") || pack.get("description").isJsonNull()) {
            throw new IllegalArgumentException();
         }

         int[] min;
         int[] max;
         if (pack.has("min_format") && pack.has("max_format")) {
            min = format(pack.get("min_format"), false);
            max = format(pack.get("max_format"), true);
            if (min[0] < 65) {
               if (!pack.has("pack_format") || !pack.has("supported_formats")) {
                  throw new IllegalArgumentException();
               }

               integer(pack.get("pack_format"));
               legacyRange(pack.get("supported_formats"));
            } else if (pack.has("supported_formats")) {
               throw new IllegalArgumentException();
            }
         } else {
            if (pack.has("min_format") || pack.has("max_format")) {
               throw new IllegalArgumentException();
            }

            int version = integer(pack.get("pack_format"));
            if (version >= 65) {
               throw new IllegalArgumentException();
            }

            min = new int[]{version, 0};
            max = new int[]{version, Integer.MAX_VALUE};
            if (pack.has("supported_formats")) {
               int[] range = legacyRange(pack.get("supported_formats"));
               min[0] = range[0];
               max[0] = range[1];
               if (max[0] >= 65) {
                  throw new IllegalArgumentException();
               }
            }
         }

         if (compare(min[0], min[1], max[0], max[1]) > 0) {
            throw new IllegalArgumentException();
         } else {
            return new PackMetadata(min[0], min[1], max[0], max[1], pack.get("description").toString());
         }
      } catch (RuntimeException error) {
         if (error instanceof CancellationException) {
            throw error;
         } else {
            throw new IOException("Invalid resource pack metadata");
         }
      }
   }

   private static int integer(JsonElement value) {
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
         int n = value.getAsBigDecimal().intValueExact();
         if (n < 0) {
            throw new IllegalArgumentException();
         } else {
            return n;
         }
      } else {
         throw new IllegalArgumentException();
      }
   }

   private static int[] format(JsonElement value, boolean upper) {
      if (value.isJsonArray()) {
         JsonArray a = value.getAsJsonArray();
         if (!a.isEmpty() && a.size() <= 2) {
            return new int[]{integer(a.get(0)), a.size() == 2 ? integer(a.get(1)) : (upper ? Integer.MAX_VALUE : 0)};
         } else {
            throw new IllegalArgumentException();
         }
      } else {
         return new int[]{integer(value), upper ? Integer.MAX_VALUE : 0};
      }
   }

   private static int[] legacyRange(JsonElement value) {
      if (value.isJsonObject()) {
         JsonObject o = value.getAsJsonObject();
         return new int[]{integer(o.get("min_inclusive")), integer(o.get("max_inclusive"))};
      }

      if (value.isJsonArray()) {
         JsonArray a = value.getAsJsonArray();
         if (a.size() != 2) {
            throw new IllegalArgumentException();
         } else {
            return new int[]{integer(a.get(0)), integer(a.get(1))};
         }
      } else {
         int version = integer(value);
         return new int[]{version, version};
      }
   }
}
