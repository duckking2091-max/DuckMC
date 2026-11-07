# DuckMC

Client-side Fabric mod for Minecraft 1.21.11: settings hub, profiles, performance tools, PvP visuals,
Worlds & Resource Packs browser (Modrinth/CurseForge), and opt-in "Advantages".
GitHub: https://github.com/duckking2091-max/DuckMC

## Important history
The original source was lost in a PC reset. This code was **recovered by decompiling the 8.4.0 release jar**
(remapped to Mojang mappings first). So: no original comments, some local variables may have generic names,
and anything added in 8.5–8.19 is missing. If the user finds a newer jar, decompile it and diff against this.

## Layout (Loom split source sets)
- `src/main/java/io/duckmc/` – shared logic: config, content, advantages, performance, pvp, modules, ui/model
- `src/client/java/io/duckmc/` – client code: `DuckMcClient` (entrypoint), screens, widgets, HUD, `mixin/` (21 mixins)
- `src/main/resources/` – `fabric.mod.json`, assets (textures, lang)
- `src/client/resources/duckmc.mixins.json`

## Tools on this PC
- Java 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`
- Gradle via wrapper (9.5.1), Fabric Loom 1.17 (Loom 1.19+ needs Java 25 — don't upgrade without installing it)
- Git: `C:\Program Files\Git\cmd\git.exe`

## Build
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
& .\gradlew.bat build --no-configuration-cache
```
Output: `build/libs/duckmc-<version>.jar`. Version is in `gradle.properties`.
Mappings: Mojang official. In mixins, compare/cast `this` via `(Object)this`.

## Notes
- The recovered 8.4.0 build compiles but was never tested in-game; test before releasing.
- The user isn't very technical: explain steps plainly and commit + push after changes so nothing is lost again.
  Pushing may need the user to sign in to GitHub themselves.
