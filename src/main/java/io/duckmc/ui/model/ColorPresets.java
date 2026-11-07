package io.duckmc.ui.model;

import java.util.List;

public final class ColorPresets {
   public static final List<ColorPresets.Preset> ALL = List.of(
      new ColorPresets.Preset("Red", 4294916923L),
      new ColorPresets.Preset("Orange", 4294937626L),
      new ColorPresets.Preset("Yellow (Default)", 4294956103L),
      new ColorPresets.Preset("Light Green", 4286376010L),
      new ColorPresets.Preset("Green", 4280465482L),
      new ColorPresets.Preset("Dark Green", 4279200562L),
      new ColorPresets.Preset("Cyan", 4281327840L),
      new ColorPresets.Preset("Blue", 4281297919L),
      new ColorPresets.Preset("Purple", 4287250400L),
      new ColorPresets.Preset("Pink", 4294930352L),
      new ColorPresets.Preset("Black", 4279375132L),
      new ColorPresets.Preset("White", 4294967295L)
   );

   private ColorPresets() {
   }

   public static long apply(long current, ColorPresets.Preset preset) {
      return current & 4278190080L | preset.argb() & 16777215L;
   }

   public static boolean matches(long current, ColorPresets.Preset preset) {
      return (current & 16777215L) == (preset.argb() & 16777215L);
   }

   public record Preset(String name, long argb) {
   }
}
