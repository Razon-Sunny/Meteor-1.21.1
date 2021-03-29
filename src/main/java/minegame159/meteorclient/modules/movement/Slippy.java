package minegame159.meteorclient.modules.movement;

import minegame159.meteorclient.modules.Categories;
import minegame159.meteorclient.modules.Module;
import minegame159.meteorclient.settings.*;
import net.minecraft.block.Block;

import java.util.Collections;
import java.util.List;

public class Slippy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> slippness = sgGeneral.add(new DoubleSetting.Builder()
            .name("slippness")
            .description("Decide how slippery blocks should be")
            .min(0.0)
            .max(1.10)
            .sliderMax(1.10)
            .defaultValue(1.02)
            .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("ignored blocks")
            .description("Decide which blocks not to slip on")
            .defaultValue(Collections.emptyList())
            .build()
    );

    public Slippy(){
        super(Categories.Movement, "Slippy", "Makes blocks you step on act slippery");
    }

    public Float getSlippnessValue(){
        return Float.parseFloat(this.slippness.get()+"F");
    }

    public List<Block> getUnSlippedBlocks(){
        return blocks.get();
    }


}
