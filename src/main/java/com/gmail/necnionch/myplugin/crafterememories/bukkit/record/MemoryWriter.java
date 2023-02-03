package com.gmail.necnionch.myplugin.crafterememories.bukkit.record;

import com.github.nova_27.mcplugin.crafterepost.Utils;
import com.github.nova_27.mcplugin.crafterepost.record.TickData;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTOutputStream;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.*;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public abstract class MemoryWriter {
    protected final Region region;
    protected final long startTicks;
    protected final File outputFile;
    private final CompoundTag data;
    private final CompoundTag eventsData;
    private final Map<String, Integer> blockPalette;
    private int blockPaletteMax;
    private final Map<String, Integer> playerPalette;
    private int playerPaletteMax;

    public MemoryWriter(Region region, long startTicks, File outputFile) {
        this.region = region;
        this.startTicks = startTicks;
        this.outputFile = outputFile;
        data = new CompoundTag();
        eventsData = new CompoundTag();
        blockPalette = new HashMap<>();
        blockPaletteMax = 0;
        playerPalette = new HashMap<>();
        playerPaletteMax = 0;

        data.put("schem", getSchematicCompoundTag());
    }

    public File getOutputFile() {
        return outputFile;
    }

    protected @Nullable Tag<?> getSchematicCompoundTag() {
        var outputStream = new ByteArrayOutputStream();
        NamedTag schematicTag;
        try {
            Utils.writeSchematic(region, outputStream);
            var inputStream = new ByteArrayInputStream(outputStream.toByteArray());
            schematicTag = new NBTDeserializer(true).fromStream(inputStream);
        } catch (WorldEditException | IOException e) {
            e.printStackTrace();
            return null;
        }

        return schematicTag.getTag();
    }

    /**
     * 録画ファイルを保存する
     */
    public void save() throws IOException {
        var blockPaletteTag = new CompoundTag();
        blockPalette.forEach((blockKey, internalId) -> blockPaletteTag.put(blockKey, new IntTag(internalId)));
        var playerPaletteTag = new CompoundTag();
        playerPalette.forEach((uuid, internalId) -> playerPaletteTag.put(uuid, new IntTag(internalId)));
        eventsData.put("BlockPalette", blockPaletteTag);
        eventsData.put("BlockPaletteMax", new IntTag(blockPaletteMax));
        eventsData.put("PlayerPalette", playerPaletteTag);
        eventsData.put("PlayerPaletteMax", new IntTag(playerPaletteMax));
        data.put("events", eventsData);

        try (var nbtOut = new NBTOutputStream(new GZIPOutputStream(new FileOutputStream(outputFile), true))) {
            nbtOut.writeTag(new NamedTag(null, data), Tag.DEFAULT_MAX_DEPTH);
        }
    }

    public void saveBukkitEvents(Map<Location, String> blockChanges, Map<UUID, Location> playerLocations, long elapsedTicks) {
        var tickData = new TickData();
        tickData.saveEvents("BlockChange", blockChanges, (worldLoc, blockKey) -> {
            if (!isInRegion(worldLoc)) return null;
            var minPos = region.getMinimumPoint();
            var loc = worldLoc.subtract(minPos.getBlockX(), minPos.getBlockY(), minPos.getBlockZ());

            var data = new CompoundTag();
            data.put("BlockId", new IntTag(getInternalBlockId(blockKey)));
            data.put("Pos", locToListTag(loc));
            return data;
        });
        tickData.saveEvents("PlayerMove", playerLocations, (uuid, worldLoc) -> {
            var minPos = region.getMinimumPoint();
            var loc = worldLoc.subtract(minPos.getBlockX(), minPos.getBlockY(), minPos.getBlockZ());

            var data = new CompoundTag();
            data.put("Id", new IntTag(getInternalPlayerId(uuid)));
            data.put("Pos", locToListTag(loc));
            data.put("Yaw", new FloatTag(loc.getYaw()));
            return data;
        });

        var tickDataCompoundTag = tickData.getCompoundTag();
        if (tickDataCompoundTag.size() == 0) return;
        eventsData.put(String.valueOf(elapsedTicks - startTicks), tickDataCompoundTag);
    }

    private ListTag<DoubleTag> locToListTag(Location loc) {
        var tag = new ListTag<>(DoubleTag.class);
        tag.addDouble(loc.getX());
        tag.addDouble(loc.getY());
        tag.addDouble(loc.getZ());
        return tag;
    }

    private int getInternalBlockId(String blockKey) {
        var id = blockPalette.get(blockKey);
        if (id != null) return id;

        blockPalette.put(blockKey, blockPaletteMax);
        return blockPaletteMax++;
    }

    private int getInternalPlayerId(UUID uuid) {
        var id = playerPalette.get(uuid.toString());
        if (id != null) return id;

        playerPalette.put(uuid.toString(), playerPaletteMax);
        return playerPaletteMax++;
    }

    /**
     * 座標がRegion範囲内かどうか
     *
     * @param loc 座標
     * @return 範囲内ならtrue
     */
    private boolean isInRegion(Location loc) {
        var locWorld = new BukkitWorld(loc.getWorld());
        return locWorld.equals(region.getWorld()) && region.contains(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }


    public abstract @NotNull Result convert();


    public interface Result {
        boolean isSuccess();

        static Result success() {
            return () -> true;
        }

        static Result fail() {
            return () -> false;
        }
    }

}