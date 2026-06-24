package com.InstallYuanShen.GenshinImpactDownload;

import com.InstallYuanShen.GenshinImpactDownload.Handle;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(main.MOD_ID)
public class main {
    public static final String MOD_ID = "main";
    public static final Logger LOGGER = LogManager.getLogger();

    public main() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("开始检测并运行YuanShen.exe...");
            Handle.handleLauncher();
        });
    }
}