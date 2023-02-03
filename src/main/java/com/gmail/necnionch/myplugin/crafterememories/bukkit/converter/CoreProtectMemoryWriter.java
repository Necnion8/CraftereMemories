package com.gmail.necnionch.myplugin.crafterememories.bukkit.converter;

import com.github.nova_27.mcplugin.crafterepost.Utils;
import com.gmail.necnionch.myplugin.crafterememories.bukkit.record.MemoryWriter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.coreprotect.CoreProtectAPI;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.tag.Tag;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CoreProtectMemoryWriter extends MemoryWriter {
    private final CoreProtectAPI.ParseResult[] results;
    private final World bWorld;  // caching

    public CoreProtectMemoryWriter(Region region, File outputFile, CoreProtectAPI.ParseResult[] results) {
        super(region, 0, outputFile);
        this.results = results;
        this.bWorld = BukkitAdapter.adapt(Objects.requireNonNull(region.getWorld()));
    }

    @Override
    protected @Nullable Tag<?> getSchematicCompoundTag() {
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        try {
            for (int x = 0; x <= max.getX() - min.getX(); x++) {
                for (int z = 0; z <= max.getZ() - min.getZ(); z++) {
                    clipboard.setBlock(BlockVector3.at(min.getX() + x, min.getY(), min.getZ() + z), BlockTypes.STONE.getDefaultState().toBaseBlock());
                }
            }

            var outputStream = new ByteArrayOutputStream();
            try (ClipboardWriter clipboardWriter = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(outputStream)) {
                clipboardWriter.write(clipboard);
            }
            var schematicTag = new NBTDeserializer(true).fromBytes(outputStream.toByteArray());
            return schematicTag.getTag();

        } catch (WorldEditException | IOException e) {
            e.printStackTrace();
            return null;  // nullを返すとCompoundTag.put が NPE を投げる
        }
    }

    @Override
    public @NotNull Result convert() {
        List<CoreProtectAPI.ParseResult> results = Stream.of(this.results)
                .filter(r -> !r.isRolledBack())
                .filter(r -> 0 <= r.getActionId() && r.getActionId() <= 2)  // 0: removed, 1: placed, 2: interaction
                .sorted(Comparator.comparingLong(CoreProtectAPI.ParseResult::getTimestamp))
                .collect(Collectors.toList());

        if (results.isEmpty())
            return new Result(true, 0);

        long startTimestamp = results.get(0).getTimestamp();
        long readTicks = 0;

        Set<String> blockLocations = Sets.newHashSet();
        Map<Location, String> changedBlocks = Maps.newHashMap();

        for (CoreProtectAPI.ParseResult record : results) {
            // CoreProtectのログの時間が1秒単位？
            long elapsedTicks = (long) Math.ceil(((double) (record.getTimestamp() - startTimestamp)) / 1000 * 20);
            if (readTicks != elapsedTicks) {
                readTicks = elapsedTicks;
                if (!changedBlocks.isEmpty()) {
                    saveBukkitEvents(changedBlocks, Collections.emptyMap(), elapsedTicks);
                    changedBlocks = Maps.newHashMap();
                }
            }

            String blockKey = Utils.getBlockKey(record.getBlockData());
            Location loc = new Location(bWorld, record.getX(), record.getY(), record.getZ());
            blockLocations.add(loc.toString());
            changedBlocks.put(loc, blockKey);
        }
        return new Result(true, blockLocations.size());
    }


    public static final class Result implements MemoryWriter.Result {

        private final boolean success;
        private final long changedBlocks;

        public Result(boolean success, long changedBlocks) {
            this.success = success;
            this.changedBlocks = changedBlocks;
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        public long getChangedBlocks() {
            return changedBlocks;
        }

    }

}
