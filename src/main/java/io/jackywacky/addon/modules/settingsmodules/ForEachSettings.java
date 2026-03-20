package io.jackywacky.addon.modules.settingsmodules;

import io.jackywacky.addon.DupersUnitedPublicAddon;
import it.unimi.dsi.fastutil.doubles.Double2BooleanSortedMap;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ForEachSettings extends Module {
    public ForEachSettings() {
        super(DupersUnitedPublicAddon.CATEGORY_UTILITIES, "foreach-player-cmd", "Settings for .foreachPlayer");
    }


    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
            .name("include-self")
            .description("Include yourself in the foreach loop")
            .defaultValue(false)
            .build());

    public final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay between each Command")
            .defaultValue(0)
            .sliderRange(0, 20000)
            .build());

}
