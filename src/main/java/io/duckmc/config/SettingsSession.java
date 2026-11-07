package io.duckmc.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.duckmc.input.KeybindService;
import io.duckmc.performance.PerformancePreset;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

public final class SettingsSession {
   private static final long DEBOUNCE_NANOS = 400000000L;
   private static final Set<String> SECTIONS = Set.of("ui", "category", "hud", "key", "performance", "module");
   private final SettingsSession.Writer writer;
   private final LongSupplier clock;
   private final boolean readOnly;
   private final Thread owner = Thread.currentThread();
   private ConfigDocument document;
   private ConfigDocument saved;
   private long changedAt;
   private boolean scheduled;
   private String error = "";
   private ConfigDocument pendingReset;
   private boolean autosaveHeld;

   public SettingsSession(ConfigDocument initial, SettingsSession.Writer writer, LongSupplier clock, boolean readOnly) {
      this.document = ConfigValidator.validate(initial);
      this.saved = this.document;
      this.writer = Objects.requireNonNull(writer);
      this.clock = Objects.requireNonNull(clock);
      this.readOnly = readOnly;
   }

   public ConfigDocument document() {
      return this.document;
   }

   public boolean dirty() {
      return !this.document.equals(this.saved);
   }

   public String error() {
      return this.error;
   }

   public boolean readOnly() {
      return this.readOnly;
   }

   public boolean hasPendingReset() {
      return this.pendingReset != null;
   }

   public void preview(String key, JsonElement value) {
      this.writable();
      SettingsSchema.Definition definition = SettingsSchema.definitions().get(key);
      if (definition == null) {
         throw new IllegalArgumentException("Unknown setting: " + key);
      }

      JsonElement validated = definition.validate(value);
      if (key.startsWith("module.")
         && SettingsSchema.ADVANTAGES.contains(key.substring(7))
         && validated.getAsBoolean()
         && !this.document.advantagesAcknowledged()) {
         throw new IllegalStateException("Advantages require explicit acknowledgement");
      }

      JsonObject settings = this.document.activeSettings();
      if (key.startsWith("module.") && !key.equals("module.fps_boost") && settings.get("module.fps_boost").getAsBoolean()) {
         JsonObject ownership;
         try {
            ownership = StrictJson.object(settings.get("performance.fpsBoostOwnership").getAsString());
         } catch (IllegalArgumentException invalid) {
            ownership = new JsonObject();
         }

         ownership.remove(key);
         settings.addProperty("performance.fpsBoostOwnership", ownership.toString());
      }

      settings.add(key, validated);
      this.change(this.document.withActiveSettings(settings));
   }

   public void tick() {
      this.assertOwner();
      if (!this.autosaveHeld && this.scheduled && this.clock.getAsLong() - this.changedAt >= 400000000L) {
         this.flush();
      }
   }

   public void holdAutosave(boolean held) {
      this.assertOwner();
      this.autosaveHeld = held;
   }

   public void discard() {
      this.assertOwner();
      this.document = this.saved;
      this.scheduled = false;
      this.pendingReset = null;
   }

   public void settle() {
      if (!this.autosaveHeld) {
         this.flush();
      }
   }

   public boolean savePerformanceDraft(String profile, JsonObject changes) {
      this.writable();

      for (String key : changes.keySet()) {
         if (!key.startsWith("performance.") && !PerformancePreset.fpsBoostSettings().contains(key)) {
            throw new IllegalArgumentException("Not a performance setting: " + key);
         }
      }

      if (!this.document.activeProfile().equals(profile)) {
         this.error = "Active profile changed. Reopen this page before editing.";
         return false;
      }

      JsonObject settings = this.document.activeSettings();
      changes.entrySet().forEach(entry -> settings.add((String)entry.getKey(), ((JsonElement)entry.getValue()).deepCopy()));
      settings.addProperty("profile.modified", System.currentTimeMillis());
      ConfigDocument candidate = ConfigValidator.validate(this.document.withActiveSettings(settings));
      new ConfigCodec().encode(candidate);

      try {
         this.writer.save(candidate);
      } catch (IOException | RuntimeException exception) {
         this.error = "Settings were not saved or applied. Check storage and retry.";
         return false;
      }

      this.document = candidate;
      this.saved = candidate;
      this.scheduled = false;
      this.pendingReset = null;
      this.error = "";
      return true;
   }

   public boolean flush() {
      this.assertOwner();
      if (this.readOnly) {
         return false;
      }

      this.scheduled = false;
      if (!this.dirty()) {
         return true;
      }

      try {
         this.writer.save(this.document);
         this.saved = this.document;
         this.error = "";
         return true;
      } catch (IOException | RuntimeException exception) {
         this.error = "Settings were not saved. Check storage permissions or free space, then retry.";
         return false;
      }
   }

   public boolean acknowledgeAdvantages() {
      this.assertOwner();
      if (this.readOnly) {
         this.error = "Advantages remain locked: this configuration is read-only.";
         return false;
      }

      ConfigDocument candidate = ConfigValidator.validate(
         new ConfigDocument(this.document.schemaVersion(), this.document.activeProfile(), this.document.profiles(), true)
      );
      new ConfigCodec().encode(candidate);

      try {
         this.writer.save(candidate);
      } catch (IOException | RuntimeException failure) {
         this.error = "Advantages remain locked. The acknowledgement could not be saved; retry after checking storage.";
         return false;
      }

      this.document = candidate;
      this.saved = candidate;
      this.scheduled = false;
      this.pendingReset = null;
      this.error = "";
      return true;
   }

   public void createProfile(String name) {
      this.writable();
      this.addProfile(name, this.document.activeSettings());
   }

   public void createProfile(String name, String description, boolean copyActive) {
      this.writable();
      JsonObject settings = copyActive ? this.document.activeSettings() : SettingsSchema.defaults();
      settings.addProperty("profile.description", description);
      settings.addProperty("profile.modified", System.currentTimeMillis());
      this.addProfile(name, settings);
   }

   public void duplicateProfile(String source, String name) {
      this.writable();
      JsonObject settings = this.known(source);
      settings.addProperty("profile.modified", System.currentTimeMillis());
      this.addProfile(name, settings);
   }

   public void editProfile(String current, String name, String description) {
      this.writable();
      JsonObject settings = this.known(current);
      if (current.equals("Default") && !name.equals(current)) {
         throw new IllegalArgumentException("Default cannot be renamed");
      }

      ConfigValidator.profileName(name);
      if (!name.equals(current) && this.document.profiles().containsKey(name)) {
         throw new IllegalArgumentException("A profile with this name already exists");
      }

      settings.addProperty("profile.description", description);
      settings.addProperty("profile.modified", System.currentTimeMillis());
      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>();
      this.document.profiles().forEach((key, value) -> {
         if (key.equals(current)) {
            profiles.put(name, settings);
         } else {
            profiles.put(key, value);
         }
      });
      String active = this.document.activeProfile().equals(current) ? name : this.document.activeProfile();
      this.change(new ConfigDocument(1, active, profiles, this.document.advantagesAcknowledged()));
   }

   public void deleteProfile(String name) {
      this.writable();
      this.known(name);
      if (name.equals("Default")) {
         throw new IllegalStateException("Default is protected");
      }

      if (name.equals(this.document.activeProfile())) {
         throw new IllegalStateException("Load another profile before deleting this one");
      }

      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
      profiles.remove(name);
      this.change(new ConfigDocument(1, this.document.activeProfile(), profiles, this.document.advantagesAcknowledged()));
   }

   private JsonObject known(String name) {
      JsonObject settings = this.document.profiles().get(name);
      if (settings == null) {
         throw new IllegalArgumentException("Unknown profile");
      } else {
         return settings;
      }
   }

   public KeybindService.BindingUpdate bind(String action, int key, int modifiers) {
      this.writable();
      KeybindService service = new KeybindService(this.document);
      KeybindService.BindingUpdate result = service.bind(action, key, modifiers);
      this.change(service.document());
      return result;
   }

   public void renameProfile(String name) {
      this.writable();
      ConfigValidator.profileName(name);
      if (this.document.activeProfile().equals("Default")) {
         throw new IllegalArgumentException("Default cannot be renamed");
      }

      if (this.document.profiles().containsKey(name)) {
         throw new IllegalArgumentException("Profile name already exists");
      }

      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
      JsonObject settings = profiles.remove(this.document.activeProfile());
      profiles.put(name, settings);
      this.change(new ConfigDocument(1, name, profiles, this.document.advantagesAcknowledged()));
   }

   public void deleteActiveProfile() {
      this.writable();
      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
      if (profiles.size() == 1) {
         throw new IllegalStateException("Keep at least one profile");
      }

      if (this.document.activeProfile().equals("Default")) {
         throw new IllegalStateException("Default is protected");
      }

      profiles.remove(this.document.activeProfile());
      this.pendingReset = new ConfigDocument(1, profiles.keySet().iterator().next(), profiles, this.document.advantagesAcknowledged());
   }

   public void switchProfile(String name) {
      this.writable();
      if (!this.document.profiles().containsKey(name)) {
         throw new IllegalArgumentException("Unknown profile");
      }

      this.change(new ConfigDocument(1, name, this.document.profiles(), this.document.advantagesAcknowledged()));
   }

   public void importProfile(String text) {
      this.importProfile(text, false);
   }

   public void importProfile(String text, boolean replace) {
      this.writable();
      ProfileService.Candidate candidate = new ProfileService().importProfile(text);
      JsonObject settings = candidate.settings();
      settings.addProperty("profile.modified", System.currentTimeMillis());
      if (replace && this.document.profiles().containsKey(candidate.name())) {
         LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
         profiles.put(candidate.name(), settings);
         this.change(new ConfigDocument(1, this.document.activeProfile(), profiles, this.document.advantagesAcknowledged()));
      } else {
         this.addProfile(candidate.name(), settings);
      }
   }

   public String importedProfileName(String text) {
      this.assertOwner();
      return new ProfileService().importProfile(text).name();
   }

   public String exportProfile() {
      return this.exportProfile(this.document.activeProfile());
   }

   public String exportProfile(String name) {
      this.assertOwner();
      return new ProfileService().exportProfile(name, this.known(name));
   }

   private void addProfile(String name, JsonObject settings) {
      ConfigValidator.profileName(name);
      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
      if (profiles.containsKey(name)) {
         throw new IllegalArgumentException("A profile with this name already exists");
      }

      profiles.put(name, settings);
      this.change(new ConfigDocument(1, this.document.activeProfile(), profiles, this.document.advantagesAcknowledged()));
   }

   public void resetSection(String section) {
      this.writable();
      if (!SECTIONS.contains(section)) {
         throw new IllegalArgumentException("Unknown settings section");
      }

      JsonObject settings = this.document.activeSettings();
      SettingsSchema.definitions().forEach((key, definition) -> {
         if (key.startsWith(section + ".") && !key.equals("performance.fpsBoostOwnership")) {
            settings.add(key, definition.initial().deepCopy());
         }
      });
      this.pendingReset = this.document.withActiveSettings(settings);
   }

   public void resetAll() {
      this.writable();
      JsonObject settings = SettingsSchema.defaults();
      settings.add("profile.description", this.document.activeSettings().get("profile.description"));
      this.pendingReset = this.document.withActiveSettings(settings);
   }

   public void resetAcknowledgement() {
      this.writable();
      LinkedHashMap<String, JsonObject> profiles = new LinkedHashMap<>(this.document.profiles());
      profiles.values().forEach(settings -> SettingsSchema.ADVANTAGES.forEach(id -> settings.addProperty("module." + id, false)));
      this.pendingReset = new ConfigDocument(1, this.document.activeProfile(), profiles, false);
   }

   public boolean confirmReset() {
      this.writable();
      if (this.pendingReset == null) {
         return false;
      }

      ConfigDocument candidate = this.pendingReset;
      this.change(candidate);
      return true;
   }

   public void cancelReset() {
      this.assertOwner();
      this.pendingReset = null;
   }

   private void change(ConfigDocument candidate) {
      ConfigDocument validated = this.stamped(ConfigValidator.validate(candidate));
      new ConfigCodec().encode(validated);
      this.pendingReset = null;
      if (!validated.equals(this.document)) {
         this.document = validated;
         this.changedAt = this.clock.getAsLong();
         this.scheduled = true;
      }
   }

   private ConfigDocument stamped(ConfigDocument next) {
      String active = this.document.activeProfile();
      if (next.activeProfile().equals(active) && !next.profiles().get(active).equals(this.document.profiles().get(active))) {
         JsonObject settings = next.activeSettings();
         settings.addProperty("profile.modified", System.currentTimeMillis());
         return next.withActiveSettings(settings);
      } else {
         return next;
      }
   }

   private void writable() {
      this.assertOwner();
      if (this.readOnly) {
         throw new IllegalStateException("Newer configuration is read-only");
      }
   }

   private void assertOwner() {
      if (Thread.currentThread() != this.owner) {
         throw new IllegalStateException("Settings must be edited on their owner thread");
      }
   }

   @FunctionalInterface
   public interface Writer {
      void save(ConfigDocument var1) throws IOException;
   }
}
