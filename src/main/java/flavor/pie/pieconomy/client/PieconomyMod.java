package flavor.pie.pieconomy.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid = "pieconomy_client", name = "PieconomyClient", version = "1.0-SNAPSHOT", clientSideOnly = true)
public class PieconomyMod {

    public static Logger LOGGER;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent e) {
        LOGGER = e.getModLog();
        MinecraftForge.EVENT_BUS.register(new PieconomyEventListener());
        PieconomyPacketHandler.INSTANCE.registerMessage(DeclareCurrenciesMessage.Handler.class,
                DeclareCurrenciesMessage.class, 0, Side.CLIENT);
    }

}
