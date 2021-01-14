/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.modules.render;

import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.Module;

public class EChestPreview extends Module {
    public EChestPreview() {
        super(Category.Render, "EChest-preview", "Stores what's inside your Ender Chest and displays when you hover over it.");
    }
}