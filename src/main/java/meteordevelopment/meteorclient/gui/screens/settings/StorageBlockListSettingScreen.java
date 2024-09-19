/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StorageBlockListSetting;
import net.fabricmc.loader.impl.util.StringUtil;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StorageBlockListSettingScreen extends RegistryListSettingScreen<BlockEntityType<?>> {
    private static final Pair<String,Item> UNKNOWN = new ObjectObjectImmutablePair<>("Unknown", Items.BARRIER);
    private static final Map<BlockEntityType<?>, Pair<String,Item>> STORAGE_BLOCK_ENTITY_MAP = new Object2ObjectOpenHashMap<>();

    public StorageBlockListSettingScreen(GuiTheme theme, Setting<List<BlockEntityType<?>>> setting) {
        super(theme, "Select Storage Blocks", setting, setting.get(), StorageBlockListSetting.REGISTRY);
    }

    @Override
    protected WWidget getValueWidget(BlockEntityType<?> value) {
        Item item = STORAGE_BLOCK_ENTITY_MAP.getOrDefault(value, UNKNOWN).right();
        return theme.itemWithLabel(item.getDefaultStack(), getValueName(value));
    }

    @Override
    protected String getValueName(BlockEntityType<?> value) {
        return STORAGE_BLOCK_ENTITY_MAP.getOrDefault(value, UNKNOWN).left();
    }

    private static Field findFieldObject(Field[] fields, Predicate<Field> condition) {
        for (Field field : fields) if (condition.test(field)) return field;
        return null;
    }

    static {
        Field[] BlockEntityFields = BlockEntityType.class.getDeclaredFields();
        Field[] ItemsFields = Items.class.getDeclaredFields();
        for (BlockEntityType<?> block : StorageBlockListSetting.STORAGE_BLOCKS) {
            try {
                Field nameField = findFieldObject(BlockEntityFields, field -> {
                    try {
                        return field.getType() == BlockEntityType.class && field.get(null) == block;
                    } catch (IllegalAccessException ignored) {}
                    return false;
                });
                if (nameField == null) continue;
                Field itemField = findFieldObject(ItemsFields, field -> {
                    if (field.getType() == Item.class) return field.getName().equals(nameField.getName());
                    return false;
                });
                if (itemField == null) continue;
                String displayName = Arrays.stream(nameField.getName().toLowerCase().split("_")).map(StringUtil::capitalize).collect(Collectors.joining(" "));
                STORAGE_BLOCK_ENTITY_MAP.put(block, new ObjectObjectImmutablePair<>(displayName, (Item) itemField.get(null)));
            } catch (IllegalAccessException ignored) {}
        }
    }
}
