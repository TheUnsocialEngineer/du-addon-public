package io.jackywacky.addon;

import com.mojang.logging.LogUtils;
import io.jackywacky.addon.commands.*;
import io.jackywacky.addon.modules.AttributeSwap;
import io.jackywacky.addon.modules.ChatGames;
import io.jackywacky.addon.modules.PacketDelay;
import io.jackywacky.addon.modules.PayAll;
import io.jackywacky.addon.modules.crashes.ArmorPlace;
import io.jackywacky.addon.modules.crashes.BundleCrash;
import io.jackywacky.addon.modules.crashes.ChestCrash;
import io.jackywacky.addon.modules.crashes.TradeCrash;
import io.jackywacky.addon.modules.dupes.*;
import io.jackywacky.addon.modules.settingsmodules.ForEachSettings;
import io.jackywacky.addon.modules.settingsmodules.GuiMacros;
import io.jackywacky.addon.modules.settingsmodules.GuiSlotNbt;
import lombok.SneakyThrows;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;


public class DupersUnitedPublicAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY_UTILITIES = new Category("DU Utilities", Items.COMPASS.getDefaultStack());
    public static final Category CATEGORY_CRASHES = new Category("DU Crashes", Items.WITHER_SKELETON_SKULL.getDefaultStack());
    public static final Category CATEGORY_DUPES = new Category("DU Dupes", Items.BUNDLE.getDefaultStack());
    // Backward compatibility for older modules that still reference CATEGORY.
    public static final Category CATEGORY = CATEGORY_UTILITIES;

    @Override
    @SneakyThrows
    public void onInitialize() {
        LOG.info("Initializing DupersUnited Public Addon");

   /*     Method add = Systems.class.getDeclaredMethod("add", System.class);
        add.setAccessible(true);
        add.invoke(null, new DupeDBApi());*/
        //FIXME Not fully implemented

        initModules();
        initCommands();

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY_UTILITIES);
        Modules.registerCategory(CATEGORY_CRASHES);
        Modules.registerCategory(CATEGORY_DUPES);
    }

    @Override
    public String getPackage() {
        return "io.jackywacky.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("TheUnsocialEngineer", "du-addon-public");
    }


    private void initModules() {

        //Util
        Modules.get().add(new GuiMacros());
        Modules.get().add(new GuiSlotNbt());
        Modules.get().add(new ForEachSettings());

        //Exploits
        Modules.get().add(new AttributeSwap());
        Modules.get().add(new PacketDelay());
        Modules.get().add(new ChatGames());
        Modules.get().add(new PayAll());

        //Crashes
        Modules.get().add(new BundleCrash());
        Modules.get().add(new ArmorPlace());
        Modules.get().add(new ChestCrash());
        Modules.get().add(new TradeCrash());

        //Dupes
        Modules.get().add(new PaperBookDupe());
        Modules.get().add(new ShulkerDupe());
        Modules.get().add(new TradeDupe());
        Modules.get().add(new TridentDupe());
        Modules.get().add(new BundleDupe());
        Modules.get().add(new AuctionPacketDelay());
        Modules.get().add(new Essentials());
        Modules.get().add(new ZAH214());
    }

    private void initCommands() {
        Commands.add(new ClickSlotCommand());
        Commands.add(new WaitCommand());
        Commands.add(new RepeatCommand());
        Commands.add(new RepeatDelayCommand());
        Commands.add(new ForEachPlayerCommand());
        Commands.add(new DupedbSearch());
    }
}
