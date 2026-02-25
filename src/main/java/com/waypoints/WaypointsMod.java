package com.waypoints;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = WaypointsMod.MODID, name = "Waypoints", version = WaypointsMod.VERSION, clientSideOnly = true)
public class WaypointsMod {

    public static final String MODID   = "waypoints";
    public static final String VERSION = "1.0.0";
    /** Chat prefix – §2[WP] §r */
    public static final String PREFIX  = "\u00a72[WP]\u00a7r ";

    @Mod.Instance(MODID)
    public static WaypointsMod INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        // Point storage at the standard config directory (e.g. .minecraft/config/)
        WaypointStorage.getInstance().initFile(e.getSuggestedConfigurationFile().getParentFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        WaypointStorage.getInstance().load();
        MinecraftForge.EVENT_BUS.register(new WaypointRenderer());
        ClientCommandHandler.instance.registerCommand(new WaypointCommand());
    }
}