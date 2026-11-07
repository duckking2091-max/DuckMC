package io.duckmc.content;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

final class WorldNbt {
   private int tags;

   static void validate(InputStream compressed, CancellationToken token) throws IOException {
      try (GZIPInputStream gzip = new GZIPInputStream(compressed)) {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         byte[] buffer = new byte[8192];

         int read;
         while ((read = gzip.read(buffer)) >= 0) {
            token.throwIfCancelled();
            if (bytes.size() + read > 16777216) {
               throw new IOException("World metadata too large");
            }

            bytes.write(buffer, 0, read);
         }

         DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
         if (in.readUnsignedByte() != 10) {
            throw new IOException("World root is not a compound");
         }

         in.readUTF();
         WorldNbt reader = new WorldNbt();
         boolean data = false;

         while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) {
               if (!data || in.read() != -1) {
                  throw new IOException("Invalid world metadata");
               }

               return;
            }

            String name = in.readUTF();
            if (name.equals("Data")) {
               if (type != 10 || data) {
                  throw new IOException("Invalid world Data tag");
               }

               data = true;
            }

            reader.payload(in, type, 0, token);
         }
      }
   }

   private void payload(DataInputStream in, int type, int depth, CancellationToken token) throws IOException {
      token.throwIfCancelled();
      if (++this.tags <= 1000000 && depth <= 64) {
         switch (type) {
            case 1:
               in.readByte();
               break;
            case 2:
               in.readShort();
               break;
            case 3:
            case 5:
               in.readInt();
               break;
            case 4:
            case 6:
               in.readLong();
               break;
            case 7:
            case 11:
            case 12: {
               int count = count(in);
               int size = type == 7 ? 1 : (type == 11 ? 4 : 8);
               in.skipNBytes((long)count * size);
               break;
            }
            case 8:
               in.readUTF();
               break;
            case 9: {
               int child = in.readUnsignedByte();
               int count = count(in);
               if (child == 0 && count != 0) {
                  throw new IOException();
               }

               for (int i = 0; i < count; i++) {
                  this.payload(in, child, depth + 1, token);
               }
               break;
            }
            case 10:
               while (true) {
                  int child = in.readUnsignedByte();
                  if (child == 0) {
                     return;
                  }

                  in.readUTF();
                  this.payload(in, child, depth + 1, token);
               }
            default:
               throw new IOException("Invalid NBT tag");
         }
      } else {
         throw new IOException("World NBT limit exceeded");
      }
   }

   private static int count(DataInputStream in) throws IOException {
      int count = in.readInt();
      if (count >= 0 && count <= 16777216) {
         return count;
      } else {
         throw new IOException();
      }
   }
}
