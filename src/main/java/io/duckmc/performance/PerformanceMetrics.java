package io.duckmc.performance;

import java.util.Arrays;

public final class PerformanceMetrics {
   public static final int DEFAULT_CAPACITY = 240;
   private final long[] durations;
   private int next;
   private int size;

   public PerformanceMetrics() {
      this(240);
   }

   public PerformanceMetrics(int capacity) {
      if (capacity <= 0) {
         throw new IllegalArgumentException("Capacity must be positive");
      }

      this.durations = new long[capacity];
   }

   public synchronized void recordFrame(long nanos) {
      if (nanos <= 0L) {
         throw new IllegalArgumentException("Frame duration must be positive");
      }

      this.durations[this.next] = nanos;
      this.next = (this.next + 1) % this.durations.length;
      if (this.size < this.durations.length) {
         this.size++;
      }
   }

   public synchronized PerformanceMetrics.Snapshot snapshot() {
      if (this.size == 0) {
         return PerformanceMetrics.Snapshot.empty();
      }

      long[] sample = new long[this.size];
      if (this.size < this.durations.length) {
         System.arraycopy(this.durations, 0, sample, 0, this.size);
      } else {
         int tail = this.durations.length - this.next;
         System.arraycopy(this.durations, this.next, sample, 0, tail);
         System.arraycopy(this.durations, 0, sample, tail, this.next);
      }

      double totalNanos = 0.0;

      for (long duration : sample) {
         totalNanos += duration;
      }

      double meanNanos = totalNanos / sample.length;
      Arrays.sort(sample);
      return new PerformanceMetrics.Snapshot(
         sample.length,
         meanNanos / 1000000.0,
         percentileMillis(sample, 95),
         percentileMillis(sample, 99),
         sample[0] / 1000000.0,
         sample[sample.length - 1] / 1000000.0,
         1.0E9 / meanNanos
      );
   }

   private static double percentileMillis(long[] sorted, int percentile) {
      int rank = (int)Math.ceil(percentile / 100.0 * sorted.length);
      return sorted[Math.max(0, rank - 1)] / 1000000.0;
   }

   public record Snapshot(int sampleCount, double meanMillis, double p95Millis, double p99Millis, double minMillis, double maxMillis, double framesPerSecond) {
      public static PerformanceMetrics.Snapshot empty() {
         return new PerformanceMetrics.Snapshot(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
      }
   }
}
