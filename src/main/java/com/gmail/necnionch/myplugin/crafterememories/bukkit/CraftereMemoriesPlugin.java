package com.gmail.necnionch.myplugin.crafterememories.bukkit;

import com.sk89q.worldedit.WorldEdit;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class CraftereMemoriesPlugin extends JavaPlugin {

    private CoreProtectAPI coreProtect;
    private WorldEdit worldEdit;

    @Override
    public void onEnable() {
        if (!setupCrafterePost()) {
            getLogger().severe("Failed to hook to CrafterePost");
            setEnabled(false);
            return;
        }

        if (!setupWorldEdit()) {
            getLogger().severe("Failed to hook to WorldEdit");
            setEnabled(false);
            return;
        }

        if (!setupCoreProtectAPI()) {
            getLogger().severe("Failed to hook to CoreProtect (v9 API)");
            setEnabled(false);
            return;
        }

        Optional.ofNullable(getCommand("crafterepostmemories")).ifPresent(cmd ->
                cmd.setExecutor(new CraftereMemoriesCommand(this)));

        getLogger().info("Enabled CraftereMemories");
    }

    @Override
    public void onDisable() {
    }

    private boolean setupCrafterePost() {
        Plugin tmp = getServer().getPluginManager().getPlugin("CrafterePost");
        return tmp != null && tmp.isEnabled();
    }

    private boolean setupWorldEdit() {
        Plugin tmp = getServer().getPluginManager().getPlugin("WorldEdit");
        WorldEdit worldEdit = WorldEdit.getInstance();
        if (worldEdit != null && tmp != null && tmp.isEnabled()) {
            this.worldEdit = worldEdit;
            return true;
        }
        return false;
    }

    private boolean setupCoreProtectAPI() {
        Plugin tmp = getServer().getPluginManager().getPlugin("CoreProtect");
        try {
            if (tmp instanceof CoreProtect) {
                CoreProtectAPI api = ((CoreProtect) tmp).getAPI();
                if (api.isEnabled() && api.APIVersion() >= 9) {
                    coreProtect = api;
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public WorldEdit getWorldEdit() {
        return worldEdit;
    }

    public CoreProtectAPI getCoreProtect() {
        return coreProtect;
    }

}
