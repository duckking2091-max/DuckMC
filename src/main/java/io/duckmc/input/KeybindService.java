package io.duckmc.input;

import com.google.gson.JsonObject;
import io.duckmc.config.ConfigCodec;
import io.duckmc.config.ConfigDocument;
import io.duckmc.config.ConfigValidator;
import io.duckmc.config.SettingsSchema;
import java.util.List;

public final class KeybindService {
   private ConfigDocument document;

   public KeybindService(ConfigDocument document) {
      this.document = validated(document);
   }

   private static ConfigDocument validated(ConfigDocument d) {
      d = ConfigValidator.validate(d);

      for (JsonObject p : d.profiles().values()) {
         for (String action : actions()) {
            new KeyBindingSpec(action, p.get("key." + action).getAsInt(), p.get("key." + action + ".modifiers").getAsInt());
         }
      }

      return d;
   }

   public static List<String> actions() {
      return SettingsSchema.definitions()
         .keySet()
         .stream()
         .filter(k -> k.startsWith("key.") && !k.endsWith(".modifiers"))
         .map(k -> k.substring(4))
         .filter(action -> !action.equals("anchor_assist"))
         .toList();
   }

   public KeyBindingSpec binding(String action) {
      if (!actions().contains(action)) {
         throw new IllegalArgumentException("Unknown action");
      }

      JsonObject p = this.document.activeSettings();
      return new KeyBindingSpec(action, p.get("key." + action).getAsInt(), p.get("key." + action + ".modifiers").getAsInt());
   }

   public KeybindService.BindingUpdate bind(String action, int key, int modifiers) {
      this.binding(action);
      KeyBindingSpec spec = new KeyBindingSpec(action, key, modifiers);
      List<KeyBindingSpec> conflicts = actions()
         .stream()
         .filter(a -> !a.equals(action))
         .map(this::binding)
         .filter(b -> !spec.unbound() && b.key() == key && b.modifiers() == modifiers)
         .toList();
      JsonObject p = this.document.activeSettings();
      p.addProperty("key." + action, key);
      p.addProperty("key." + action + ".modifiers", modifiers);
      this.document = this.document.withActiveSettings(p);
      return new KeybindService.BindingUpdate(spec, conflicts);
   }

   public void unbind(String action) {
      this.bind(action, -1, 0);
   }

   public void restoreDefault(String action) {
      JsonObject d = SettingsSchema.defaults();
      this.bind(action, d.get("key." + action).getAsInt(), d.get("key." + action + ".modifiers").getAsInt());
   }

   public ConfigDocument document() {
      return this.document;
   }

   public String exportConfig() {
      return new ConfigCodec().encode(this.document);
   }

   public void importConfig(String text) {
      this.document = validated(new ConfigCodec().decode(text));
   }

   public record BindingUpdate(KeyBindingSpec binding, List<KeyBindingSpec> conflicts) {
      public BindingUpdate {
         conflicts = List.copyOf(conflicts);
      }
   }
}
