package io.duckmc.content;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;

public final class PreviewImage {
   public static final int MAX_BYTES = 8388608;
   private static final int MAX_SOURCE = 8192;
   private static final int MAX_TEXTURE = 1024;

   private PreviewImage() {
   }

   public static byte[] png(Path file) throws IOException {
      PreviewImage.Dimensions size = inspect(file, 8388608L, 8192);
      BufferedImage image = decode(file, size);

      try {
         ByteArrayOutputStream output = new ByteArrayOutputStream();
         if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder unavailable");
         } else {
            return output.toByteArray();
         }
      } finally {
         image.flush();
      }
   }

   private static BufferedImage decode(Path file, PreviewImage.Dimensions size) throws IOException {
      int step = Math.max(1, (Math.max(size.width(), size.height()) + 1024 - 1) / 1024);

      try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
         Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
         if (!readers.hasNext()) {
            throw new IOException("Unsupported preview image");
         }

         ImageReader reader = readers.next();

         try {
            reader.setInput(input);
            ImageReadParam param = reader.getDefaultReadParam();
            if (step > 1) {
               param.setSourceSubsampling(step, step, 0, 0);
            }

            return reader.read(0, param);
         } finally {
            reader.dispose();
         }
      }
   }

   static void requireComplete(byte[] data) throws IOException {
      if (data.length < 12) {
         throw new IOException("Incomplete preview");
      }

      boolean complete = true;
      if ((data[0] & 255) == 137 && data[1] == 80) {
         complete = hasTail(data, new byte[]{73, 69, 78, 68});
      } else if ((data[0] & 255) == 255 && (data[1] & 255) == 216) {
         complete = hasTail(data, new byte[]{-1, -39});
      } else if (data[0] == 82 && data[1] == 73) {
         long size = data[4] & 255L | (data[5] & 255L) << 8 | (data[6] & 255L) << 16 | (data[7] & 255L) << 24;
         complete = data.length >= size + 8L;
      } else if (data[0] == 71 && data[1] == 73) {
         complete = data[data.length - 1] == 59;
      }

      if (!complete) {
         throw new IOException("Incomplete preview");
      }
   }

   private static boolean hasTail(byte[] data, byte[] marker) {
      for (int i = data.length - marker.length; i >= Math.max(0, data.length - 4096); i--) {
         if (Arrays.equals(data, i, i + marker.length, marker, 0, marker.length)) {
            return true;
         }
      }

      return false;
   }

   public static PreviewImage.Dimensions inspect(Path file) throws IOException {
      return inspect(file, 4194304L, 2048);
   }

   private static PreviewImage.Dimensions inspect(Path file, long maxBytes, int maxSide) throws IOException {
      if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= maxBytes) {
         requireComplete(Files.readAllBytes(file));

         try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
               throw new IOException("Unsupported preview image");
            }

            ImageReader reader = readers.next();

            try {
               reader.setInput(input);
               int width = reader.getWidth(0);
               int height = reader.getHeight(0);
               if (width < 1 || height < 1 || width > maxSide || height > maxSide) {
                  throw new IOException("Preview exceeds dimension limit");
               } else {
                  return new PreviewImage.Dimensions(width, height);
               }
            } finally {
               reader.dispose();
            }
         }
      } else {
         throw new IOException("Preview exceeds size limit");
      }
   }

   static {
      IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
   }

   public record Dimensions(int width, int height) {
   }
}
