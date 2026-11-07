package io.duckmc.performance;

import java.util.Arrays;
import java.util.Locale;

public final class FrameBenchmark {
   private static final int MAX_FRAMES = 1048576;
   private long[] frames;
   private int count;
   private long total;
   private long target;
   private long peakHeap;

   public void start(int seconds) {
      if (seconds >= 1 && seconds <= 600) {
         this.frames = new long[1048576];
         this.count = 0;
         this.total = 0L;
         this.peakHeap = 0L;
         this.target = seconds * 1000000000L;
      } else {
         throw new IllegalArgumentException("Invalid duration");
      }
   }

   public boolean running() {
      return this.frames != null;
   }

   public FrameBenchmark.Report record(long nanos, long usedHeap) {
      if (this.running() && nanos > 0L) {
         this.frames[this.count++] = nanos;
         this.total += nanos;
         this.peakHeap = Math.max(this.peakHeap, usedHeap);
         if (this.total < this.target && this.count < 1048576) {
            return null;
         }

         long[] sample = Arrays.copyOf(this.frames, this.count);
         this.frames = null;
         return report(sample, this.total, this.peakHeap);
      } else {
         return null;
      }
   }

   static FrameBenchmark.Report report(long[] sample, long total, long peakHeap) {
      Arrays.sort(sample);
      int n = sample.length;
      long median = sample[n / 2];
      int slowest = Math.max(1, n / 100);
      int stutters = 0;
      long slowTotal = 0L;

      for (int i = n - slowest; i < n; i++) {
         slowTotal += sample[i];
      }

      for (long frame : sample) {
         if (frame > median * 2L) {
            stutters++;
         }
      }

      return new FrameBenchmark.Report(
         n,
         total / 1.0E9,
         n * 1.0E9 / total,
         slowest * 1.0E9 / slowTotal,
         median / 1000000.0,
         sample[Math.max(0, (int)Math.ceil(n * 0.99) - 1)] / 1000000.0,
         sample[n - 1] / 1000000.0,
         stutters,
         peakHeap
      );
   }

   public record Report(
      int frames,
      double seconds,
      double averageFps,
      double onePercentLowFps,
      double medianMillis,
      double p99Millis,
      double worstMillis,
      int stutters,
      long peakHeapBytes
   ) {
      public static String csvHeader() {
         return "frames,seconds,avg_fps,one_percent_low_fps,median_ms,p99_ms,worst_ms,stutters,peak_heap_mib";
      }

      public String csv() {
         return String.format(
            Locale.ROOT,
            "%d,%.1f,%.1f,%.1f,%.2f,%.2f,%.2f,%d,%d",
            this.frames,
            this.seconds,
            this.averageFps,
            this.onePercentLowFps,
            this.medianMillis,
            this.p99Millis,
            this.worstMillis,
            this.stutters,
            this.peakHeapBytes / 1048576L
         );
      }
   }
}
