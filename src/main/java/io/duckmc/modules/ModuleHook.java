package io.duckmc.modules;

public interface ModuleHook {
   void enable() throws Exception;

   void disable() throws Exception;
}
