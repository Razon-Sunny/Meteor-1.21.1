/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.meteorclient;

import net.minecraft.nbt.NbtCompound;

public abstract class MeteorAddon {
    public abstract void onInitialize();

    public void onRegisterCategories() {}

    public abstract NbtCompound getAddonNBT();
}
