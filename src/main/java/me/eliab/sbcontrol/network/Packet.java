package me.eliab.sbcontrol.network;

/**
 * The Packet interface represents a unit of data that can be transmitted over the network in the context of minecraft multiplayer,
 * used for communication between server and clients. Implementing classes should define the specific structure and content of the packet.
 */
public interface Packet {

    /**
     * Reads the packet data from the provided {@link PacketSerializer}. This method is responsible for deserializing
     * the serialized data from the PacketSerializer and populating the fields of the Packet instance accordingly.
     *
     * @param packetSerializer The serializer instance that contains the serialized data to be read.
     */
    void read(PacketSerializer packetSerializer);

    /**
     * Writes the packet data into the provided {@link PacketSerializer}. This method is responsible for serializing
     * the content of the Packet instance into a format suitable for transmission over the network using a PacketSerializer.
     *
     * @param packetSerializer The serializer instance that will be used to serialize the packet data.
     */
    void write(PacketSerializer packetSerializer);

}
