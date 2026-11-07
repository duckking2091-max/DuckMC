package io.duckmc.ui.render;

import io.duckmc.ui.model.Theme;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class UiText {
   private UiText() {
   }

   public static Component styled(Component text, Theme theme) {
      return text.copy().withStyle(style -> style.withFont(new Resource(Identifier.parse(theme.font()))));
   }

   public static List<String> availableFonts(Minecraft client) {
      TreeSet<String> fonts = new TreeSet<>();
      fonts.add("minecraft:default");
      fonts.add("minecraft:uniform");
      client.getResourceManager().listResources("font", id -> id.getPath().endsWith(".json")).keySet().forEach(id -> {
         String path = id.getPath();
         fonts.add(Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(5, path.length() - 5)).toString());
      });
      return List.copyOf(fonts);
   }
}
