package io.duckmc.performance;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToLongFunction;

public final class CacheBudget<K, V> {
   private final long maxWeight;
   private final ToLongFunction<? super V> weigher;
   private final LinkedHashMap<K, CacheBudget.Entry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
   private long currentWeight;

   public CacheBudget(long maxWeight, ToLongFunction<? super V> weigher) {
      if (maxWeight < 0L) {
         throw new IllegalArgumentException("Maximum weight must be nonnegative");
      }

      this.maxWeight = maxWeight;
      this.weigher = Objects.requireNonNull(weigher, "weigher");
   }

   public synchronized Optional<V> get(K key) {
      Objects.requireNonNull(key, "key");
      CacheBudget.Entry<V> entry = this.entries.get(key);
      return entry == null ? Optional.empty() : Optional.of(entry.value());
   }

   public synchronized boolean put(K key, V value) {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(value, "value");
      long weight = this.weigher.applyAsLong(value);
      if (weight < 0L) {
         throw new IllegalArgumentException("Entry weight must be nonnegative");
      }

      if (weight == 0L && this.maxWeight > 0L) {
         throw new IllegalArgumentException("Retained entries must declare positive weight");
      }

      if (this.maxWeight != 0L && weight <= this.maxWeight) {
         CacheBudget.Entry<V> previous = this.entries.remove(key);
         if (previous != null) {
            this.currentWeight = this.currentWeight - previous.weight();
         }

         Iterator<Map.Entry<K, CacheBudget.Entry<V>>> iterator = this.entries.entrySet().iterator();

         while (this.currentWeight > this.maxWeight - weight && iterator.hasNext()) {
            Map.Entry<K, CacheBudget.Entry<V>> eldest = iterator.next();
            this.currentWeight = this.currentWeight - eldest.getValue().weight();
            iterator.remove();
         }

         this.entries.put(key, new CacheBudget.Entry<>(value, weight));
         this.currentWeight += weight;
         return true;
      } else {
         return false;
      }
   }

   public synchronized Optional<V> remove(K key) {
      Objects.requireNonNull(key, "key");
      CacheBudget.Entry<V> removed = this.entries.remove(key);
      if (removed == null) {
         return Optional.empty();
      }

      this.currentWeight = this.currentWeight - removed.weight();
      return Optional.of(removed.value());
   }

   public synchronized void clear() {
      this.entries.clear();
      this.currentWeight = 0L;
   }

   public synchronized int size() {
      return this.entries.size();
   }

   public synchronized long currentWeight() {
      return this.currentWeight;
   }

   public long maxWeight() {
      return this.maxWeight;
   }

   private record Entry<V>(V value, long weight) {
   }
}
