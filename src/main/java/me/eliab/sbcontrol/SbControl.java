package me.eliab.sbcontrol;

import me.eliab.sbcontrol.network.PacketManager;
import me.eliab.sbcontrol.network.versions.Version;
import me.eliab.sbcontrol.util.Reflection;
import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;

/**
 * The SbControl class initializes the versioned packet manager based on the detected minecraft server version.
 */
public class SbControl {

    private static final Version VERSION;
    private static final PacketManager PACKET_MANAGER;

    private SbControl() {
        throw new UnsupportedOperationException();
    }

    static {

        if (Reflection.isRepackage()) {

            if (Reflection.getOptionalNmsClass("network.protocol", "PacketType").isPresent()) {
                VERSION = Version.V1_20_5;
            } else if (Reflection.getOptionalNmsClass("network.chat.numbers", "NumberFormat").isPresent()) {
                VERSION = Version.V1_20_3;
            } else if (Reflection.getOptionalNmsClass("world.scores", "DisplaySlot").isPresent()) {
                VERSION = Version.V1_20_2;
            } else {
                VERSION = Version.V1_16;
            }

        } else if (Reflection.getOptionalNmsClass(null, "ChatHexColor").isPresent()) {
            VERSION = Version.V1_16;
        } else if (Reflection.getOptionalNmsClass(null, "ScoreboardServer$Action").isPresent()) {
            VERSION = Version.V1_13;
        } else {
            VERSION = Version.V1_12;
        }

        try {

            Constructor<? extends PacketManager> constructor = VERSION.getPacketManagerClass().getDeclaredConstructor(Version.class);
            constructor.setAccessible(true);
            PACKET_MANAGER = constructor.newInstance(VERSION);

        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().severe("===============================");
            Bukkit.getLogger().severe(" SbControl could not be loaded");
            Bukkit.getLogger().severe("===============================");
            throw new RuntimeException(e);
        }

    }

    /**
     * Retrieves the detected server version.
     * @return The detected minecraft server version.
     */
    public static Version getVersion() {
        return VERSION;
    }

    /**
     * Retrieves the instance of the PacketManager associated with the server version.
     * @return The PacketManager instance.
     */
    public static PacketManager getPacketManager() {
        return PACKET_MANAGER;
    }

}
