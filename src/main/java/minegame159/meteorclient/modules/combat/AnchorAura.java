/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.modules.combat;

//Created by squidoodly 03/08/2020

import com.google.common.collect.Streams;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.world.PostTickEvent;
import minegame159.meteorclient.friends.FriendManager;
import minegame159.meteorclient.mixininterface.IKeyBinding;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.Module;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.utils.player.Chat;
import minegame159.meteorclient.utils.player.DamageCalcUtils;
import minegame159.meteorclient.utils.player.PlayerUtils;
import minegame159.meteorclient.utils.player.RotationUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

public class AnchorAura extends Module {

    public enum Mode {
        Safe,
        Suicide
    }

    public enum RotationMode {
        Place,
        Break,
        Both,
        None
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgDamage = settings.createGroup("Damages");
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgRange = settings.createGroup("Ranges");

    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("The mode to rotate you server-side.")
            .defaultValue(RotationMode.Place)
            .build()
    );

    // Place

    private final Setting<Boolean> place = sgPlace.add(new BoolSetting.Builder()
            .name("place")
            .description("Allows Anchor Aura to place anchors.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Mode> placeMode = sgPlace.add(new EnumSetting.Builder<Mode>()
            .name("place-mode")
            .description("The way anchors are placed.")
            .defaultValue(Mode.Safe)
            .build()
    );

    // Break

    private final Setting<Mode> breakMode = sgBreak.add(new EnumSetting.Builder<Mode>()
            .name("break-mode")
            .description("The way anchors are broken.")
            .defaultValue(Mode.Safe)
            .build()
    );

    // Delay

    private final Setting<Integer> placeDelay = sgDelay.add(new IntSetting.Builder()
            .name("place-delay")
            .description("The amount of delay in ticks for placement.")
            .defaultValue(2)
            .min(0)
            .max(10)
            .build()
    );

    private final Setting<Integer> breakDelay = sgDelay.add(new IntSetting.Builder()
            .name("break-delay")
            .description("The amount of delay in ticks for breaking.")
            .defaultValue(2)
            .min(0)
            .max(10)
            .build()
    );

    // Range

    private final Setting<Double> placeRange = sgRange.add(new DoubleSetting.Builder()
            .name("place-range")
            .description("The radius in which the anchors are placed in.")
            .defaultValue(3)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Double> breakRange = sgRange.add(new DoubleSetting.Builder()
            .name("break-range")
            .description("The radius in which the anchors are broken in.")
            .defaultValue(3)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Double> targetRange = sgRange.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("The radius in which players get targeted.")
            .defaultValue(3)
            .min(0)
            .sliderMax(5)
            .build()
    );

    // Damages

    private final Setting<Double> maxDamage = sgDamage.add(new DoubleSetting.Builder()
            .name("max-damage")
            .description("The maximum self-damage allowed.")
            .defaultValue(3)
            .build()
    );

    private final Setting<Double> minHealth = sgDamage.add(new DoubleSetting.Builder()
            .name("min-health")
            .description("The minimum health you have to be for this to work.")
            .defaultValue(15)
            .build()
    );

    public AnchorAura() {super(Category.Combat, "anchor-aura", "Automatically places and breaks Respawn Anchors to harm entities.");}

    private int placeDelayLeft = placeDelay.get();
    private int breakDelayLeft = breakDelay.get();
    private BlockPos targetBlockPos;
    private PlayerEntity target;
    private int glowSlot = -1;
    private int anchorSlot = -1;

    @EventHandler
    private final Listener<PostTickEvent> onTick = new Listener<>(event -> {
        assert mc.player != null;
        assert mc.world != null;
        assert mc.interactionManager != null;
        placeDelayLeft --;
        breakDelayLeft --;
        if (mc.world.getDimension().isRespawnAnchorWorking()) {
            Chat.error(this, "You are not in the Overworld... Disabling!");
            this.toggle();
            return;
        }
        if (getTotalHealth(mc.player) <= minHealth.get() && placeMode.get() != Mode.Suicide && breakMode.get() != Mode.Suicide) return;

        findTarget();

        if (target == null) return;

        findSlots();

        if (breakDelayLeft <= 0) {
            if (findAnchors() != null) if (rotationMode.get() == RotationMode.Both || rotationMode.get() == RotationMode.Break) RotationUtils.packetRotate(findAnchors());
            breakAnchor(findAnchors(), glowSlot, anchorSlot);
            breakDelayLeft = breakDelay.get();
        }

        if (place.get() && placeDelayLeft <= 0) {
            if (findValidBlock() != null) if (rotationMode.get() == RotationMode.Both || rotationMode.get() == RotationMode.Place) RotationUtils.packetRotate(findValidBlock());
            PlayerUtils.placeBlock(findValidBlock(), anchorSlot, Hand.MAIN_HAND);
            placeDelayLeft = placeDelay.get();
        }
    });

    private BlockPos findValidBlock() {
        assert mc.world != null;
        assert mc.player != null;
        if (mc.world.getBlockState(targetBlockPos.down()).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.down())) <= placeRange.get()
                && getDamagePlace(targetBlockPos.down())) {
            return targetBlockPos.down();
        } else if (mc.world.getBlockState(targetBlockPos.up(2)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.up(2))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.up(2))) {
            return targetBlockPos.up(2);
        } else if (mc.world.getBlockState(targetBlockPos.add(1, 0, 0)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(1, 0, 0))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(1, 0, 0))) {
            return targetBlockPos.add(1, 0, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(-1, 0, 0)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(-1, 0, 0))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(-1, 0, 0))) {
            return targetBlockPos.add(-1, 0, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 0, 1)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 0, 1))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(0, 0, 1))) {
            return targetBlockPos.add(0, 0, 1);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 0, -1)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 0, -1))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(0, 0, -1))) {
            return targetBlockPos.add(0, 0, -1);
        } else if (mc.world.getBlockState(targetBlockPos.add(1, 1, 0)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(1, 1, 0))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(1, 1, 0))) {
            return targetBlockPos.add(1, 1, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(-1, 1, 0)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(-1, 1, 0))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(-1, 1, 0))) {
            return targetBlockPos.add(-1, 1, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 1, 1)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 1, 1))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(0, 1, 1))) {
            return targetBlockPos.add(0, 1, 1);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 1, -1)).isAir() && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 1, -1))) <= placeRange.get()
                && getDamagePlace(targetBlockPos.add(0, 1, -1))) {
            return targetBlockPos.add(0, 1, -1);
        }
        return null;
    }

    private BlockPos findAnchors() {
        assert mc.player != null;
        assert mc.world != null;
        if (mc.world.getBlockState(targetBlockPos.down()).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.down())) <= breakRange.get()
                && getDamageBreak(targetBlockPos.down())) {
            return targetBlockPos.down();
        } else if (mc.world.getBlockState(targetBlockPos.up(2)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.up(2))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.up(2))) {
            return targetBlockPos.up(2);
        } else if (mc.world.getBlockState(targetBlockPos.add(1, 0, 0)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(1, 0, 0))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(1, 0, 0))) {
            return targetBlockPos.add(1, 0, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(-1, 0, 0)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(-1, 0, 0))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(-1, 0, 0))) {
            return targetBlockPos.add(-1, 0, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 0, 1)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 0, 1))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(0, 0, 1))) {
            return targetBlockPos.add(0, 0, 1);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 0, -1)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 0, -1))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(0, 0, -1))) {
            return targetBlockPos.add(0, 0, -1);
        } else if (mc.world.getBlockState(targetBlockPos.add(1, 1, 0)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(1, 1, 0))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(1, 1, 0))) {
            return targetBlockPos.add(1, 1, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(-1, 1, 0)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(-1, 1, 0))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(-1, 1, 0))) {
            return targetBlockPos.add(-1, 1, 0);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 1, 1)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 1, 1))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(0, 1, 1))) {
            return targetBlockPos.add(0, 1, 1);
        } else if (mc.world.getBlockState(targetBlockPos.add(0, 1, -1)).getBlock() == Blocks.RESPAWN_ANCHOR && Math.sqrt(mc.player.getBlockPos().getSquaredDistance(targetBlockPos.add(0, 1, -1))) <= breakRange.get()
                && getDamageBreak(targetBlockPos.add(0, 1, -1))) {
            return targetBlockPos.add(0, 1, -1);
        }
        return null;
    }

    private boolean getDamagePlace(BlockPos pos){
        assert mc.player != null;
        return placeMode.get() == Mode.Suicide || DamageCalcUtils.bedDamage(mc.player, new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) <= maxDamage.get();
    }

    private boolean getDamageBreak(BlockPos pos){
        assert mc.player != null;
        return breakMode.get() == Mode.Suicide || DamageCalcUtils.anchorDamage(mc.player, new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) <= maxDamage.get();
    }

    private void findTarget(){
        assert mc.world != null;
        assert mc.player != null;
        Optional<PlayerEntity> livingEntity = Streams.stream(mc.world.getEntities())
                .filter(entity -> entity instanceof PlayerEntity)
                .filter(Entity::isAlive)
                .filter(entity -> entity != mc.player)
                .filter(entity -> FriendManager.INSTANCE.attack((PlayerEntity) entity))
                .filter(entity -> entity.distanceTo(mc.player) <= targetRange.get() * 2)
                .min(Comparator.comparingDouble(o -> o.distanceTo(mc.player)))
                .map(entity -> (PlayerEntity) entity);
        if (!livingEntity.isPresent()) {
            target = null;
            return;
        }
        target = livingEntity.get();
        targetBlockPos = target.getBlockPos();
    }

    private void findSlots(){
        assert mc.player != null;
        glowSlot = -1;
        anchorSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.inventory.getStack(i).getItem() == Items.GLOWSTONE) {
                glowSlot = i;
            } else if (mc.player.inventory.getStack(i).getItem() == Items.RESPAWN_ANCHOR) {
                anchorSlot = i;
            }
        }
    }

    private void breakAnchor(BlockPos pos, int glowSlot, int nonGlowSlot){
        assert mc.player != null;
        assert mc.interactionManager != null;
        if (pos == null) return;
        Vec3d vecPos = new Vec3d(pos.getX() + 0.5, pos.getX(), pos.getZ() + 0.5);
        if (glowSlot != -1 && nonGlowSlot != -1) {
            mc.player.setSneaking(false);
            ((IKeyBinding) mc.options.keySneak).setPressed(false);
            int preSlot = mc.player.inventory.selectedSlot;
            mc.player.inventory.selectedSlot = glowSlot;
            mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, new BlockHitResult(vecPos, Direction.UP, pos, false));
            mc.player.inventory.selectedSlot = nonGlowSlot;
            mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, new BlockHitResult(vecPos, Direction.UP, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.inventory.selectedSlot = preSlot;
        }
    }

    private float getTotalHealth(PlayerEntity target) {
        return target.getHealth() + target.getAbsorptionAmount();
    }
}
