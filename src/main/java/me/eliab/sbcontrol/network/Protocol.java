package me.eliab.sbcontrol.network;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import me.eliab.sbcontrol.network.versions.Version;
import me.eliab.sbcontrol.util.Reflection;

import java.lang.invoke.MethodHandle;

/**
 * The Protocol class facilitates the storage and retrieval of packet IDs and their corresponding packet classes.
 * It offers the flexibility to either manually assign packet IDs or automatically retrieve them from minecraft packet classes.
 */
public class Protocol {

    private final Codec codec;
    private final BiMap<Integer, Class<? extends Packet>> packets = HashBiMap.create();

    Protocol(Version version) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        codec = getCodec(version);
    }

    /**
     * Sets the packet ID of a minecraft packet class to a given packet class.
     *
     * @param type           The protocol type of the minecraft packet.
     * @param direction      The direction of minecraft the packet.
     * @param nmsPacketClass The minecraft packet class to retrieve the id from.
     * @param packetClass    The packet class to set the id to.
     */
    public void setPacketID(Type type, Direction direction, Class<?> nmsPacketClass, Class<? extends Packet> packetClass) {
        try {
            int id = codec.getNmsPacketID(type, direction, nmsPacketClass);
            registerPacket(id, packetClass);
        } catch (Throwable e) {
            throw new RuntimeException("Protocol could not retrieve id of minecraft packet class: '" + nmsPacketClass + "'", e);
        }
    }

    /**
     * Registers a packet with its corresponding ID.
     *
     * @param id          The packet ID.
     * @param packetClass The packet class.
     */
    public void registerPacket(int id, Class<? extends Packet> packetClass) {
        Preconditions.checkArgument(packets.putIfAbsent(id, packetClass) == null,
                "Protocol has already registered a packet with the same id: '" + id + "'");
    }

    /**
     * Retrieves the packet ID for the given packet class.
     *
     * @param packetClass The packet class from which to retrieve the packet ID.
     * @return The corresponding packet ID.
     * @throws IllegalArgumentException If the packet ID for the specified packet class is not registered.
     */
    public int getPacketID(Class<? extends Packet> packetClass) {
        Integer id = packets.inverse().get(packetClass);
        Preconditions.checkArgument(id != null,
                "Protocol has not registered an id for packet: '" + packetClass + "'");
        return id;
    }

    /**
     * Retrieves the packet class for the given packet ID.
     *
     * @param id The packet ID for which to retrieve the packet class.
     * @return The corresponding packet class.
     * @throws IllegalArgumentException If no packet class is registered with the specified ID.
     */
    public Class<? extends Packet> getPacketClass(int id) {
        Class<? extends Packet> packetClass = packets.get(id);
        Preconditions.checkArgument(packetClass != null,
                "Protocol has not registered a packet class with id: " + id);
        return packetClass;
    }

    /**
     * Serializes a packet into a PacketSerializer.
     *
     * @param packet           The packet to serialize.
     * @param packetSerializer The PacketSerializer instance to use.
     * @return The PacketSerializer instance given with all the packet's data.
     */
    public PacketSerializer serialize(Packet packet, PacketSerializer packetSerializer) {
        packetSerializer.writeByte(getPacketID(packet.getClass()));
        packet.write(packetSerializer);
        return packetSerializer;
    }

    /**
     * Deserializes a packet from a PacketSerializer.
     *
     * @param packetSerializer The PacketSerializer containing the packet data.
     * @return The deserialized packet.
     */
    public Packet deserialize(PacketSerializer packetSerializer) {

        Class<? extends Packet> packetClass = getPacketClass(packetSerializer.readByte());

        try {
            Packet packet = Reflection.createInstance(packetClass);
            packet.read(packetSerializer);
            return packet;
        } catch (Throwable e) {
            throw new RuntimeException("Protocol could not deserialize PacketSerializer into: '" + packetClass + "'", e);
        }

    }

    /**
     * Retrieves the codec associated with this Protocol instance.
     * @return The codec.
     */
    public Codec getCodec() {
        return codec;
    }

    /**
     * Enum representing different protocol types.
     */
    public enum Type {

        HANDSHAKING,
        PLAY,
        STATUS,
        LOGIN,
        CONFIGURATION

    }

    /**
     * Enum representing packet direction.
     */
    public enum Direction {

        SERVERBOUND,
        CLIENTBOUND

    }

    /**
     * The Codec interface defines the method, depending on the version, for retrieving minecraft packet IDs
     * based on protocol type, direction, and the corresponding NMS packet class.
     */
    public interface Codec {

        /**
         * Retrieves the NMS packet ID for a specific packet type, protocol direction, and NMS packet class.
         *
         * @param type      The protocol type of the packet.
         * @param direction The direction of the packet (serverbound or clientbound).
         * @param nmsClass  The NMS packet class associated with the packet.
         * @return The NMS packet ID.
         * @throws Throwable If an error occurs during the retrieval process.
         */
        int getNmsPacketID(Protocol.Type type, Protocol.Direction direction, Class<?> nmsClass) throws Throwable;

    }

    private static Codec getCodec(Version version) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {

        Class<?> enumProtocolClass = Reflection.getNmsClass("network", "EnumProtocol");
        Class<?> enumProtocolDirectionClass = Reflection.getNmsClass("network.protocol", "EnumProtocolDirection");
        Class<?> packetClass = Reflection.getNmsClass("network.protocol", "Packet");

        Enum<?>[] protocolEnum = Reflection.getEnumConstants(enumProtocolClass);
        Enum<?>[] protocolDirectionEnum = Reflection.getEnumConstants(enumProtocolDirectionClass);

        if (version.isHigherOrEqualThan(Version.V1_20_2)) {

            Class<?> codecClass = enumProtocolClass.getDeclaredClasses()[1];

            MethodHandle getCodecMethod = Reflection.findMethod(enumProtocolClass, codecClass, enumProtocolDirectionClass);
            MethodHandle getPacketIDMethod = Reflection.findMethod(codecClass, int.class, packetClass);

            return (type, direction, nmsClass) -> {

                Enum<?> protocol = protocolEnum[type.ordinal()];
                Enum<?> protocolDirection = protocolDirectionEnum[direction.ordinal()];
                Object codec = getCodecMethod.invoke(protocol, protocolDirection);

                return (int) getPacketIDMethod.invoke(codec, Reflection.createInstance(nmsClass));

            };

        } else {

            Class<?> returnType = (version == Version.V1_20) ? int.class : Integer.class;
            MethodHandle getPacketIDMethod = Reflection.findMethod(enumProtocolClass, returnType, enumProtocolDirectionClass, packetClass);

            return (type, direction, nmsClass) -> {

                Enum<?> protocol = protocolEnum[type.ordinal()];
                Enum<?> protocolDirection = protocolDirectionEnum[direction.ordinal()];

                return (int) getPacketIDMethod.invoke(protocol, protocolDirection, Reflection.createInstance(nmsClass));

            };

        }

    }

}