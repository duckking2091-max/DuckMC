package io.duckmc.performance;

import java.util.Arrays;
import java.util.Locale;

public final class RenderPhases {
   public static final int WORLD = 0;
   public static final int CHUNK_UPDATES = 1;
   public static final int FRUSTUM = 2;
   public static final int ENTITIES = 3;
   public static final int BLOCK_LAYERS = 4;
   public static final int GAME_TICK = 5;
   public static final int GAME_RENDER = 6;
   public static final int HUD = 7;
   public static final int SWAP = 8;
   public static final int SECTION_DRAW = 9;
   private static final long[] STARTS = new long[10];
   private static final long[] TOTALS = new long[10];
   private static boolean recording;

   private RenderPhases() {
   }

   public static void start() {
      Arrays.fill(TOTALS, 0L);
      Arrays.fill(STARTS, 0L);
      recording = true;
   }

   public static void begin(int phase) {
      if (recording) {
         STARTS[phase] = System.nanoTime();
      }
   }

   public static void end(int phase) {
      if (recording && STARTS[phase] != 0L) {
         TOTALS[phase] = TOTALS[phase] + (System.nanoTime() - STARTS[phase]);
      }
   }

   public static String finish(int frames) {
      recording = false;
      StringBuilder row = new StringBuilder();

      for (long total : TOTALS) {
         row.append(String.format(Locale.ROOT, ",%.3f", total / 1000000.0 / Math.max(1, frames)));
      }

      return row.toString();
   }
}
