package com.mehanic.improvedscroll;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("improved_scroll")
public class ImprovedScrollMod
{
    public static final String MOD_ID = "improved_scroll";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> IMPROVED_SCROLL = ITEMS.register("improved_resource_scroll", 
            () -> new ItemImprovedResourceScroll(new Item.Properties().stacksTo(1)));

    public ImprovedScrollMod()
    {
        checkCompatibility();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void checkCompatibility()
    {
        try
        {
            com.minecolonies.api.colony.IColony.class.getMethod("getBuildingManager");
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Improved Scroll: INCOMPATIBLE MINECOLONIES VERSION! Method 'getBuildingManager' not found in IColony. Please update MineColonies.", e);
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(IMPROVED_SCROLL);
        }
    }
}