package io.duckmc.ui.model;

import io.duckmc.config.SettingsSchema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingRegistry {
   private SettingRegistry() {
   }

   public static Map<String, SettingSpec.Value> values() {
      LinkedHashMap<String, SettingSpec.Value> result = new LinkedHashMap<>();
      SettingsSchema.definitions().forEach((k, d) -> result.put(k, new SettingSpec.Value(d)));
      return Collections.unmodifiableMap(result);
   }

   public static List<SettingSpec.Action> actions() {
      return List.of(
            "profile.switch", "profile.import", "profile.export", "update.check", "reset.setting", "reset.profile", "reset.all", "advantages.resetWarning"
         )
         .stream()
         .map(SettingSpec.Action::new)
         .toList();
   }
}
