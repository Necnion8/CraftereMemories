package com.gmail.necnionch.myplugin.crafterememories.bukkit;

import com.github.nova_27.mcplugin.crafterepost.Utils;
import com.gmail.necnionch.myplugin.crafterememories.bukkit.converter.CoreProtectMemoryWriter;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class CraftereMemoriesCommand implements TabExecutor {

    private final CraftereMemoriesPlugin plugin;

    public CraftereMemoriesCommand(CraftereMemoriesPlugin plugin) {
        this.plugin = plugin;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player p = (Player) sender;
        Region selection;
        try {
            selection = Utils.getSelection(p);
        } catch (IncompleteRegionException e) {
            p.sendMessage(ChatColor.DARK_RED + "wandで保存範囲を選択してください！");
            return true;
        }

        File outputFile = new File(plugin.getDataFolder(), "test.mcsr");
        File outputDir = outputFile.getParentFile();
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            p.sendMessage(ChatColor.DARK_RED + "ディレクトリを作成できませんでした");
            return true;
        }

        BlockVector3 min = selection.getMinimumPoint();
        BlockVector3 max = selection.getMaximumPoint();
        BlockVector3 center = max.subtract(min);

        int radius = (int) Math.ceil(center.distance(max));
        Location radiusLocation = BukkitAdapter.adapt(p.getWorld(), center);
        CoreProtectAPI cp = plugin.getCoreProtect();
        List<String[]> logs = cp.performLookup(45 * 60, null, null, null, null, null, radius, radiusLocation);

        if (logs == null) {
            p.sendMessage(ChatColor.DARK_RED + "ログ情報をありませんでした");
            return true;
        }

        CoreProtectAPI.ParseResult[] logRecords = logs.stream()
                .map(cp::parseResult)
                .toArray(CoreProtectAPI.ParseResult[]::new);

        CoreProtectMemoryWriter writer = new CoreProtectMemoryWriter(selection, outputFile, logRecords);
        CoreProtectMemoryWriter.Result result = writer.convert();

        if (result.getChangedBlocks() <= 0) {
            p.sendMessage(ChatColor.DARK_RED + "ログ情報を得られませんでした");
            return true;
        }

        try {
            writer.save();
        } catch (Throwable e) {
            e.printStackTrace();
            p.sendMessage(ChatColor.DARK_RED + "ファイルを書き出せませんでした");
            return true;
        }

        p.sendMessage(ChatColor.GOLD.toString() + result.getChangedBlocks() + "ブロックの変更を書き出しました");
        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }

}
