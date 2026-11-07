package io.duckmc.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class ModuleRegistry {
   private static final int MAX_REASON_LENGTH = 256;
   private static final int MAX_EXCEPTION_DETAIL_LENGTH = 96;
   private static final String ADVANTAGES_CATEGORY = "Advantages";
   private final Thread ownerThread = Thread.currentThread();
   private final Map<ModuleId, ModuleRegistry.Entry> entries = new LinkedHashMap<>();

   public synchronized void register(ModuleDescriptor descriptor, ModuleHook hook) {
      this.assertOwnerThread();
      Objects.requireNonNull(descriptor, "descriptor");
      Objects.requireNonNull(hook, "hook");
      if (this.entries.containsKey(descriptor.id())) {
         throw new IllegalArgumentException("Duplicate module ID: " + descriptor.id());
      }

      this.entries.put(descriptor.id(), new ModuleRegistry.Entry(descriptor, hook));
   }

   public synchronized void activateDefaults() {
      this.assertOwnerThread();

      for (ModuleRegistry.Entry entry : this.entries.values()) {
         if (entry.descriptor.defaultEnabled() && !"Advantages".equalsIgnoreCase(entry.descriptor.category()) && !entry.requestSupplied) {
            entry.requestSupplied = true;
            entry.requestedEnabled = true;
            this.enableIfAvailable(entry);
         }
      }
   }

   public synchronized void setEnabled(ModuleId id, boolean enabled) {
      this.assertOwnerThread();
      ModuleRegistry.Entry entry = this.entry(id);
      entry.requestSupplied = true;
      if (entry.requestedEnabled != enabled) {
         entry.requestedEnabled = enabled;
         if (enabled) {
            this.enableIfAvailable(entry);
         } else {
            this.disableForRequest(entry);
         }
      }
   }

   public synchronized void setAvailability(ModuleId id, boolean available, String reason) {
      this.assertOwnerThread();
      ModuleRegistry.Entry entry = this.entry(id);
      String unavailableReason = available ? null : unavailableReason(reason);
      if (entry.available == available) {
         if (!available) {
            entry.availabilityReason = unavailableReason;
            if (entry.state == ModuleState.UNAVAILABLE) {
               entry.reason = unavailableReason;
            }
         }
      } else {
         entry.available = available;
         entry.availabilityReason = unavailableReason;
         if (!available) {
            this.makeUnavailable(entry);
         } else if (entry.state != ModuleState.FAILED_SAFE) {
            if (entry.requestedEnabled) {
               this.enableIfAvailable(entry);
            } else {
               entry.state = ModuleState.DISABLED;
               entry.reason = null;
            }
         }
      }
   }

   public synchronized ModuleState state(ModuleId id) {
      return this.entry(id).state;
   }

   public synchronized Optional<String> reason(ModuleId id) {
      return Optional.ofNullable(this.entry(id).reason);
   }

   private void enableIfAvailable(ModuleRegistry.Entry entry) {
      if (!entry.available) {
         entry.state = ModuleState.UNAVAILABLE;
         entry.reason = entry.availabilityReason;
      } else if (entry.state != ModuleState.ENABLED && entry.state != ModuleState.FAILED_SAFE) {
         entry.state = ModuleState.ENABLING;
         entry.reason = null;

         try {
            entry.hook.enable();
            entry.state = ModuleState.ENABLED;
         } catch (Exception enableFailure) {
            this.cleanUpFailedEnable(entry, enableFailure);
         }
      }
   }

   private void cleanUpFailedEnable(ModuleRegistry.Entry entry, Exception enableFailure) {
      String enableReason = failureReason("Enable failed", enableFailure);

      try {
         entry.hook.disable();
         entry.requestedEnabled = false;
         entry.state = ModuleState.DISABLED;
         entry.reason = enableReason;
      } catch (Exception cleanupFailure) {
         entry.state = ModuleState.FAILED_SAFE;
         entry.reason = bounded(enableReason + "; " + failureReason("cleanup failed", cleanupFailure));
      }
   }

   private void disableForRequest(ModuleRegistry.Entry entry) {
      if (entry.state != ModuleState.ENABLED && entry.state != ModuleState.FAILED_SAFE) {
         entry.state = entry.available ? ModuleState.DISABLED : ModuleState.UNAVAILABLE;
         entry.reason = entry.available ? null : entry.availabilityReason;
      } else {
         this.disable(entry, entry.available ? ModuleState.DISABLED : ModuleState.UNAVAILABLE);
      }
   }

   private void makeUnavailable(ModuleRegistry.Entry entry) {
      if (entry.state == ModuleState.ENABLED) {
         this.disable(entry, ModuleState.UNAVAILABLE);
      } else if (entry.state != ModuleState.FAILED_SAFE) {
         entry.state = ModuleState.UNAVAILABLE;
         entry.reason = entry.availabilityReason;
      }
   }

   private void disable(ModuleRegistry.Entry entry, ModuleState successState) {
      entry.state = ModuleState.DISABLING;

      try {
         entry.hook.disable();
         entry.state = successState;
         entry.reason = successState == ModuleState.UNAVAILABLE ? entry.availabilityReason : null;
      } catch (Exception disableFailure) {
         entry.state = ModuleState.FAILED_SAFE;
         entry.reason = failureReason("Disable failed", disableFailure);
      }
   }

   private ModuleRegistry.Entry entry(ModuleId id) {
      Objects.requireNonNull(id, "id");
      ModuleRegistry.Entry entry = this.entries.get(id);
      if (entry == null) {
         throw new NoSuchElementException("Unknown module ID: " + id);
      } else {
         return entry;
      }
   }

   private void assertOwnerThread() {
      if (Thread.currentThread() != this.ownerThread) {
         throw new IllegalStateException("ModuleRegistry mutations must run on its construction thread");
      }
   }

   private static String unavailableReason(String reason) {
      return reason != null && !reason.isBlank() ? bounded(oneLine(reason)) : "Unavailable";
   }

   private static String failureReason(String action, Exception failure) {
      String message = failure.getMessage();
      String detail = message != null && !message.isBlank() ? oneLine(message) : failure.getClass().getSimpleName();
      if (detail.length() > 96) {
         detail = detail.substring(0, 95) + "…";
      }

      return bounded(action + ": " + detail);
   }

   private static String oneLine(String text) {
      return text.replaceAll("\\s+", " ").trim();
   }

   private static String bounded(String text) {
      return text.length() <= 256 ? text : text.substring(0, 255) + "…";
   }

   private static final class Entry {
      private final ModuleDescriptor descriptor;
      private final ModuleHook hook;
      private boolean requestedEnabled;
      private boolean requestSupplied;
      private boolean available = true;
      private String availabilityReason;
      private ModuleState state = ModuleState.DISABLED;
      private String reason;

      private Entry(ModuleDescriptor descriptor, ModuleHook hook) {
         this.descriptor = descriptor;
         this.hook = hook;
      }
   }
}
