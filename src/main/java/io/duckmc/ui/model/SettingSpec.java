package io.duckmc.ui.model;

import io.duckmc.config.SettingsSchema;

public sealed interface SettingSpec permits SettingSpec.Value, SettingSpec.Action {
   record Action(String id) implements SettingSpec {
   }

   record Value(SettingsSchema.Definition definition) implements SettingSpec {
   }
}
