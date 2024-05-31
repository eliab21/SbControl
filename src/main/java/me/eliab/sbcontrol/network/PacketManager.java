package me.eliab.sbcontrol.network;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapMaker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import me.eliab.sbcontrol.SbControl;
import me.eliab.sbcontrol.network.game.*;
import me.eliab.sbcontrol.network.versions.Version;
import me.eliab.sbcontrol.util.Reflection;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.UUID;

/**
 * The PacketManager class handles the creation, serialization, and transmission of scoreboard packets
 * using the Netty network framework, closely emulating the methodology employed by Minecraft.
 *
 * <p>
 * This class serves as a central hub for handling various aspects of packet management within the context of
 * a scoreboard system. It encapsulates the logic for packet creation, serialization, and dispatch, providing
 * a streamlined interface for working with scoreboard-related network communication.
 * </p>
 *
 * <p>To get an instance of the versioned {@code PacketManager} use {@link SbControl#getPacketManager()}</p>
 */
public abstract class PacketManager {

    private final Version version;
    private final Protocol protocol;

    private final MethodHandle getEntityPlayerMethod;
    private final MethodHandle playerConnectionGetter;
    private final MethodHandle networkManagerGetter;
    private final MethodHandle channelGetter;

    private final Map<UUID, Channel> channels = new MapMaker().weakValues().makeMap();

    public PacketManager(Version version) throws ReflectiveOperationException {

        this.version = version;
        protocol = new Protocol(version);
        registerSbPackets();

        // nms classes
        Class<?> craftPlayerClass = Reflection.getBukkitClass("entity.CraftPlayer");
        Class<?> entityPlayerClass = Reflection.getNmsClass("server.level", "EntityPlayer");
        Class<?> playerConnectionClass = Reflection.getNmsClass("server.network", "PlayerConnection");
        Class<?> networkManagerClass = Reflection.getNmsClass("network", "NetworkManager");

        // methods and fields
        getEntityPlayerMethod = Reflection.getMethod(craftPlayerClass, "getHandle", entityPlayerClass);
        playerConnectionGetter = Reflection.findFieldGetter(entityPlayerClass, playerConnectionClass);

        if (version.isHigherOrEqualThan(Version.V1_20_2)) {
            Class<?> serverCommonPacketListenerImplClass = Reflection.getNmsClass("server.network", "ServerCommonPacketListenerImpl");
            networkManagerGetter = Reflection.findFieldGetter(serverCommonPacketListenerImplClass, networkManagerClass);
        } else {
            networkManagerGetter = Reflection.findFieldGetter(playerConnectionClass, networkManagerClass);
        }

        channelGetter = Reflection.findFieldGetter(networkManagerClass, Channel.class);

    }

    private void registerSbPackets() throws ClassNotFoundException {

        String protocolPackage = "network.protocol.game";

        Class<?> packetDisplayObjectiveClass = Reflection.getNmsClass(protocolPackage, "PacketPlayOutScoreboardDisplayObjective");
        Class<?> packetObjectiveClass = Reflection.getNmsClass(protocolPackage, "PacketPlayOutScoreboardObjective");
        Class<?> packetTeamClass = Reflection.getNmsClass(protocolPackage, "PacketPlayOutScoreboardTeam");
        Class<?> packetScoreClass = Reflection.getNmsClass(protocolPackage, "PacketPlayOutScoreboardScore");

        protocol.setPacketId(Protocol.Type.PLAY, Protocol.Direction.CLIENTBOUND, packetDisplayObjectiveClass, createPacketDisplayObjective().getClass());
        protocol.setPacketId(Protocol.Type.PLAY, Protocol.Direction.CLIENTBOUND, packetObjectiveClass, createPacketObjective().getClass());
        protocol.setPacketId(Protocol.Type.PLAY, Protocol.Direction.CLIENTBOUND, packetTeamClass, createPacketTeam().getClass());
        protocol.setPacketId(Protocol.Type.PLAY, Protocol.Direction.CLIENTBOUND, packetScoreClass, createPacketScore().getClass());

        if (version.isHigherOrEqualThan(Version.V1_20_3)) {
            Class<?> packetResetScoreClass = Reflection.getNmsClass(protocolPackage, "ClientboundResetScorePacket");
            protocol.setPacketId(Protocol.Type.PLAY, Protocol.Direction.CLIENTBOUND, packetResetScoreClass, createPacketResetScore().getClass());
        }

    }

    /**
     * Sends one or more packets to a specific player.
     *
     * <p>
     * This method simplifies the process of sending packets to a particular player. It internally retrieves the channel
     * associated with the player and then each packet is serialized using a dynamically created {@link PacketSerializer},
     * and the resulting byte data is sent through the channel.
     * </p>
     *
     * @param player  The player to send the packets to.
     * @param packets The packets to be sent.
     * @throws IllegalArgumentException If the player is null.
     */
    public void sendPacket(Player player, Packet... packets) {
        sendPacket(getPlayerChannel(player), packets);
    }

    /**
     * Sends one or more packets to a specified channel.
     *
     * <p>
     * This method facilitates the sending of one or more packets to a given channel. Each packet is serialized using
     * a dynamically created {@link PacketSerializer}, and the resulting byte data is sent through the channel.
     * </p>
     *
     * @param channel The channel to which packets will be sent.
     * @param packets The packets to be sent.
     * @throws IllegalArgumentException If the channel is null.
     */
    public void sendPacket(Channel channel, Packet... packets) {
        Preconditions.checkArgument(channel != null, "PacketManager cannot send packet to null channel");
        for (Packet packet : packets) {
            channel.writeAndFlush( protocol.serialize(packet, createPacketSerializer()).getHandle() );
        }
    }

    /**
     * Retrieves the communication channel associated with the specified player.
     *
     * <p>
     * This method is used to obtain the network communication channel (Netty Channel) linked to a particular player.
     * The player's unique identifier is used to retrieve the associated channel from the cache. If the channel is not
     * cached or is no longer open, the method attempts to extract the channel from the player's internal network
     * components using reflection.
     * </p>
     *
     * <p>
     * Note: Reflection is utilized to access internal Minecraft server components, and the method may throw a
     * {@code RuntimeException} if there are unexpected issues during the reflection process.
     * </p>
     *
     * @param player The player for whom to retrieve the communication channel.
     * @return The Netty Channel associated with the given player.
     * @throws IllegalArgumentException If the player is null.
     * @throws RuntimeException         If there are issues accessing or invoking internal components via reflection.
     */
    public Channel getPlayerChannel(Player player) {

        Preconditions.checkArgument(player != null, "PacketManager cannot get channel from null player");

        Channel channel = channels.get(player.getUniqueId());

        if (channel == null || !channel.isOpen()) {
            try {
                Object entityPlayer = getEntityPlayerMethod.invoke(player);
                Object playerConnection = playerConnectionGetter.invoke(entityPlayer);
                Object networkManager = networkManagerGetter.invoke(playerConnection);
                channels.put(player.getUniqueId(), channel = (Channel) channelGetter.invoke(networkManager));
            } catch (Throwable t) {
                throw new RuntimeException("PacketManager encounter an error retrieving player channel via reflection.", t);
            }
        }

        return channel;

    }

    /**
     * Retrieves the {@link Version} instance currently used by the PacketManager.
     * @return The version currently configured for the PacketManager.
     */
    public Version getVersion() {
        return version;
    }

    /**
     * Retrieves the {@link Protocol} instance currently used by the PacketManager.
     * @return The protocol instance associated with the PacketManager.
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Creates a new {@link PacketSerializer} with an empty {@link ByteBuf}.
     * @return A new PacketSerializer instance.
     */
    public PacketSerializer createPacketSerializer() {
        return createPacketSerializer(Unpooled.buffer());
    }

    /**
     * Creates a new {@link PacketSerializer} that uses the given {@link ByteBuf}.
     *
     * @param byteBuf The ByteBuf to be used by the new PacketSerializer.
     * @return A new PacketSerializer instance.
     */
    public abstract PacketSerializer createPacketSerializer(ByteBuf byteBuf);

    /**
     * Creates a new instance of {@link PacketDisplayObjective}.
     * @return A new PacketDisplayObjective instance, ready for customization.
     */
    public abstract PacketDisplayObjective createPacketDisplayObjective();

    /**
     * Creates a new instance of {@link PacketObjective}.
     * @return A new PacketObjective instance, ready for customization.
     */
    public abstract PacketObjective createPacketObjective();

    /**
     * Creates a new instance of {@link PacketTeam}.
     * @return A new PacketTeam instance, ready for customization.
     */
    public abstract PacketTeam createPacketTeam();

    /**
     * Creates a new instance of {@link PacketScore}.
     * @return A new PacketScore instance, ready for customization.
     */
    public abstract PacketScore createPacketScore();

    /**
     * <p><strong>(Available in 1.20.3 or greater versions)</strong></p>
     * Creates a new instance of {@link PacketResetScore}.
     *
     * @return A new PacketResetScore instance, ready for customization.
     * @throws UnsupportedOperationException If the versioned PacketManager does not support this feature.
     */
    public abstract PacketResetScore createPacketResetScore();

}
