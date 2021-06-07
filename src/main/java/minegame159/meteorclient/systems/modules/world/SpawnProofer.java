/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package minegame159.meteorclient.systems.modules.world;

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.events.world.TickEvent;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.systems.modules.Categories;
import minegame159.meteorclient.systems.modules.Module;
import minegame159.meteorclient.utils.misc.Pool;
import minegame159.meteorclient.utils.player.FindItemResult;
import minegame159.meteorclient.utils.player.InvUtils;
import minegame159.meteorclient.utils.world.BlockIterator;
import minegame159.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class SpawnProofer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range for block placement and rendering")
        .min(0)
        .sliderMax(10)
        .defaultValue(3)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Block to use for spawn proofing")
        .defaultValue(getDefaultBlocks())
        .filter(this::filterBlocks)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between placing blocks")
        .defaultValue(0)
        .min(0).sliderMax(10)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the blocks being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> alwaysSpawns = sgGeneral.add(new BoolSetting.Builder()
        .name("always-spawns")
        .description("Spawn Proofs spots that will spawn mobs")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> potentialSpawns = sgGeneral.add(new BoolSetting.Builder()
        .name("potential-spawns")
        .description("Spawn Proofs spots that will potentially spawn mobs (eg at night)")
        .defaultValue(true)
        .build()
    );


    private final Pool<Spawn> spawnPool = new Pool<Spawn>(Spawn::new);
    private final List<Spawn> spawns = new ArrayList<>();
    private int ticksWaited;

    public SpawnProofer() {
        super(Categories.World, "spawn-proofer", "Automatically spawnproofs using blocks.");
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Tick delay
        if (delay.get() != 0 && ticksWaited < delay.get() - 1) {
            return;
        }

        // Find slot
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        if (!block.found()) {
            error("Found none of the chosen blocks in hotbar");
            toggle();
            return;
        }

        for (Spawn spawn : spawns) spawnPool.free(spawn);
        spawns.clear();
        BlockIterator.register(range.get(), range.get(), (blockPos, blockState) -> {
            BlockUtils.MobSpawn validSpawn = BlockUtils.isValidMobSpawn(blockPos);
            if ((alwaysSpawns.get() && validSpawn == BlockUtils.MobSpawn.Always) ||
                (potentialSpawns.get() && validSpawn == BlockUtils.MobSpawn.Potential)) spawns.add(spawnPool.get().set(blockPos));
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (delay.get() != 0 && ticksWaited < delay.get() - 1) {
            ticksWaited++;
            return;
        }

        if (!spawns.isEmpty()) {

            // Find slot
            FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));

            // Place blocks
            if (delay.get() == 0) {
                for (Spawn spawn : spawns) BlockUtils.place(spawn.blockPos, block, rotate.get(), -50, false);
            } else {

                // Check if light source
                if (isLightSource(Block.getBlockFromItem(mc.player.inventory.getStack(block.slot).getItem()))) {

                    // Find lowest light level
                    int lowestLightLevel = 16;
                    Spawn selectedSpawn = spawns.get(0);
                    for (Spawn spawn : spawns) {
                        int lightLevel = mc.world.getLightLevel(spawn.blockPos);
                        if (lightLevel < lowestLightLevel) {
                            lowestLightLevel = lightLevel;
                            selectedSpawn = spawn;
                        }
                    }

                    BlockUtils.place(selectedSpawn.blockPos, block, rotate.get(), -50, false);

                } else {
                    BlockUtils.place(spawns.get(0).blockPos, block, rotate.get(), -50, false);
                }

            }
        }

        ticksWaited = 0;
    }

    private List<Block> getDefaultBlocks() {

        ArrayList<Block> defaultBlocks = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            if (filterBlocks(block)) defaultBlocks.add(block);
        }
        return defaultBlocks;
    }

    private boolean filterBlocks(Block block) {
        return isNonOpaqueBlock(block) || isLightSource(block);
    }

    private boolean isNonOpaqueBlock(Block block) {
        return block instanceof AbstractButtonBlock ||
            block instanceof SlabBlock ||
            block instanceof AbstractPressurePlateBlock ||
            block instanceof TransparentBlock ||
            block instanceof TripwireBlock;
    }

    private boolean isLightSource(Block block) {
        return block.getDefaultState().getLuminance() > 0;
    }

    private static class Spawn {
        public BlockPos.Mutable blockPos = new BlockPos.Mutable();

        public Spawn set(BlockPos blockPos) {
            this.blockPos.set(blockPos);

            return this;
        }
    }
}
