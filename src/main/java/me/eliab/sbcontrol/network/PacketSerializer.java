package me.eliab.sbcontrol.network;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dev.dewy.nbt.api.Tag;
import dev.dewy.nbt.api.registry.TagTypeRegistry;
import dev.dewy.nbt.api.registry.TagTypeRegistryException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import me.eliab.sbcontrol.scores.NumberFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * The PacketSerializer class is designed to work easier with a {@link ByteBuf},
 * providing simplified methods to help serialize minecraft packet data.
 * This class aims to replicate the PacketDataSerializer class that minecraft uses to achieve the same.
 */
public abstract class PacketSerializer {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;
    private static final TagTypeRegistry REGISTRY = new TagTypeRegistry();

    protected final ByteBuf byteBuf;

    public PacketSerializer(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    /**
     * Reads a {@code boolean} value from the ByteBuf.
     * @return The boolean value read.
     */
    public boolean readBoolean() {
        return byteBuf.readBoolean();
    }

    /**
     * Reads a {@code byte} value from the ByteBuf.
     * @return The byte value read.
     */
    public byte readByte() {
        return byteBuf.readByte();
    }

    /**
     * Reads a {@code byte} array from the ByteBuf.
     * @return The array read.
     */
    public byte[] readBytes() {
        int length = readVarInt();
        byte[] value = new byte[length];
        byteBuf.readBytes(value);
        return value;
    }

    /**
     * Reads an {@code int} value from the ByteBuf.
     * @return The int value read.
     */
    public int readInt() {
        return byteBuf.readInt();
    }

    /**
     * Reads a {@code long} value from the ByteBuf.
     * @return The long value read.
     */
    public long readLong() {
        return byteBuf.readLong();
    }

    /**
     * Reads a VarInt value from the ByteBuf.
     * <p>Code adapted from: <a href="https://wiki.vg/Protocol#VarInt_and_VarLong">https://wiki.vg/Protocol#VarInt_and_VarLong</a>
     *
     * @return The VarInt read decoded to its int value.
     */
    public int readVarInt() {

        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = readByte();
            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) {
                break;
            }

            position += 7;

            if (position >= 32) {
                throw new RuntimeException("PacketSerializer could not read VarInt because it is too big");
            }
        }

        return value;

    }

    /**
     * Reads a VarLong value from the ByteBuf.
     * <p>Code adapted from: <a href="https://wiki.vg/Protocol#VarInt_and_VarLong">https://wiki.vg/Protocol#VarInt_and_VarLong</a>
     *
     * @return The VarLong read decoded to its long value.
     */
    public long readVarLong() {

        long value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = readByte();
            value |= (long) (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) {
                break;
            }

            position += 7;

            if (position >= 64) {
                throw new RuntimeException("PacketSerializer could not read VarLong because it is too big");
            }
        }

        return value;

    }

    /**
     * Reads a {@code String} from the ByteBuf, decode as UFT-8.
     * @return The string value read.
     */
    public String readString() {
        int length = readVarInt();
        String value = byteBuf.toString(byteBuf.readerIndex(), length, StandardCharsets.UTF_8);
        byteBuf.readerIndex(byteBuf.readerIndex() + length);
        return value;
    }

    /**
     * Reads a collection of elements from the ByteBuf using the given reader and stores them into the provided collection.
     *
     * @param provider A function that provides an empty collection of the desired type, taking the expected size as a parameter.
     * @param reader   A function that defines the deserialization operation to be applied to each element.
     * @return A collection containing all the elements read from the ByteBuf.
     * @throws IllegalArgumentException If the provided provider or reader is null.
     */
    public <T, C extends Collection<T>> C readCollection(IntFunction<C> provider, Reader<T> reader) {

        Preconditions.checkArgument(provider != null, "PacketSerializer cannot read a collection without a provider");
        Preconditions.checkArgument(reader != null, "PacketSerializer cannot read a collection without a reader");

        int size = readVarInt();
        C value = provider.apply(size);

        for (int i = 0; i < size; i++) {
            value.add(reader.apply(this));
        }

        return value;

    }

    /**
     * Reads a collection of elements from the ByteBuf using the given reader and stores them into a list.
     *
     * @param reader The operation to be applied to each element for deserialization.
     * @return A list containing all the elements read.
     * @throws IllegalArgumentException If the provided collection or the reader is null.
     */
    public <T> List<T> readList(Reader<T> reader) {
        return readCollection(Lists::newArrayListWithCapacity, reader);
    }

    /**
     * Reads a collection of elements from the ByteBuf using the given reader and stores them into a set.
     *
     * @param reader   The operation to be applied to each element for deserialization.
     * @return A set containing all the elements read.
     * @throws IllegalArgumentException If the provided collection or the reader is null.
     */
    public <T> Set<T> readSet(Reader<T> reader) {
        return readCollection(Sets::newHashSetWithExpectedSize, reader);
    }

    /**
     * Reads an {@code Enum} constant from the ByteBuf.
     *
     * @param enumClass The enum constant class.
     * @return The value read.
     */
    public <T extends Enum<?>> T readEnum(Class<T> enumClass) {
        int ordinal = readVarInt();
        return enumClass.getEnumConstants()[ordinal];
    }

    /**
     * Reads a nullable element from the ByteBuf using the given reader.
     *
     * <pre>
     * {@code String value = readNullable(PacketSerializer::readString);}
     * </pre>
     *
     * @param reader The function to be applied for deserializing the non-null element.
     * @return The deserialized value found, or null if none is present.
     * @throws IllegalArgumentException If the provided reader is null.
     */
    public <T> T readNullable(Reader<T> reader) {
        Preconditions.checkArgument(reader != null, "PacketSerializer cannot read nullable value without a reader");
        return readBoolean() ? reader.apply(this) : null;
    }

    /**
     * Reads the content of an NBT Tag from the ByteBuf.
     *
     * @return The NBT Tag read.
     * @throws RuntimeException If an IOException occurs while reading the NBT data.
     */
    public Tag readNBTTag() {

        try (ByteBufInputStream dataInput = new ByteBufInputStream(byteBuf)) {

            Class<? extends Tag> nbtTagClass = REGISTRY.getClassFromId(dataInput.readByte());
            Tag value = REGISTRY.instantiate(nbtTagClass);
            value.read(dataInput, 0, REGISTRY);
            return value;

        } catch (TagTypeRegistryException | IOException e) {
            throw new RuntimeException("PacketSerializer encountered an error while reading NBT Tag from ByteBuf", e);
        }

    }

    /**
     * Writes a {@code boolean} value into the ByteBuf.
     * @param value The boolean value to be written.
     */
    public void writeBoolean(boolean value) {
        byteBuf.writeBoolean(value);
    }

    /**
     * Writes a {@code byte} value into the ByteBuf.
     * @param value The byte value to be written.
     */
    public void writeByte(int value) {
        byteBuf.writeByte(value);
    }

    /**
     * Writes a {@code byte} array into the ByteBuf.
     * @param value The array to be written.
     */
    public void writeBytes(byte[] value) {
        byteBuf.writeBytes(value);
    }

    /**
     * Writes an {@code int} value into the ByteBuf.
     * @param value The int value to be written.
     */
    public void writeInt(int value) {
        byteBuf.writeInt(value);
    }

    /**
     * Writes a {@code long} value into the ByteBuf.
     * @param value The long value to be written.
     */
    public void writeLong(long value) {
        byteBuf.writeLong(value);
    }

    /**
     * Writes a {@code int} value using the VarInt format into the ByteBuf.
     * <p>Code adapted from: <a href="https://wiki.vg/Protocol#VarInt_and_VarLong">https://wiki.vg/Protocol#VarInt_and_VarLong</a>
     *
     * @param value The int value to be encoded and written as a VarInt.
     */
    public void writeVarInt(int value) {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                writeByte(value);
                return;
            }

            writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
    }

    /**
     * Writes a {@code long} value using the VarLong format into the ByteBuf.
     * <p>Code adapted from: <a href="https://wiki.vg/Protocol#VarInt_and_VarLong">https://wiki.vg/Protocol#VarInt_and_VarLong</a>
     *
     * @param value The long value to be encoded and written as VarLong.
     */
    public void writeVarLong(long value) {
        while (true) {
            if ((value & ~((long) SEGMENT_BITS)) == 0) {
                writeByte((int) value);
                return;
            }

            writeByte((int) ((value & SEGMENT_BITS) | CONTINUE_BIT));
            value >>>= 7;
        }
    }

    /**
     * Writes a {@code String} into the ByteBuf, encoded as UFT-8.
     *
     * @param value The string to be written.
     * @throws IllegalArgumentException If the provided string is null.
     */
    public void writeString(String value) {

        Preconditions.checkArgument(value != null, "PacketSerializer cannot write null string");

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        writeBytes(bytes);

    }

    /**
     * Writes a collection of elements into the ByteBuf using the provided operation.
     *
     * <pre>
     * {@code
     * List<String> values = Arrays.asList("alex", "john");
     * writeCollection(values, PacketSerializer::writeString);
     * }
     * </pre>
     *
     * @param values The collection to be serialized.
     * @param writer The operation to be applied to each element for serialization.
     * @throws IllegalArgumentException If the provided collection or the writer is null.
     */
    public <T> void writeCollection(Collection<T> values, Writer<T> writer) {

        Preconditions.checkArgument(values != null, "PacketSerializer cannot write null collection");
        Preconditions.checkArgument(writer != null, "PacketSerializer cannot write a collection without a writer");

        writeVarInt(values.size());

        for (T value : values) {
            writer.accept(this, value);
        }

    }

    /**
     * Writes an {@code Enum} constant into the ByteBuf.
     *
     * @param value The enum constant to be serialized.
     * @throws IllegalArgumentException If the provided value is null.
     */
    public void writeEnum(Enum<?> value) {
        Preconditions.checkArgument(value != null, "PacketSerializer cannot write null enum");
        writeVarInt(value.ordinal());
    }

    /**
     * Writes a nullable element into the ByteBuf using a provided operation.
     *
     * <pre>
     * {@code
     * String value = null;
     * writeNullable(value, PacketSerializer::writeString);
     * }
     * </pre>
     *
     * @param value  The nullable element to be serialized. It may be null.
     * @param writer The operation to be applied for serializing the non-null element.
     * @throws IllegalArgumentException If the provided writer is null.
     */
    public <T> void writeNullable(T value, Writer<T> writer) {

        Preconditions.checkArgument(writer != null, "PacketSerializer cannot write nullable value without a writer");

        if (value != null) {
            writeBoolean(true);
            writer.accept(this, value);
        } else {
            writeBoolean(false);
        }

    }

    /**
     * Writes the content of an NBT Tag into the ByteBuf.
     *
     * @param value The NBT Tag to be serialized and written into the ByteBuf.
     * @throws IllegalArgumentException If the provided NBT tag is null.
     * @throws RuntimeException         If an IOException occurs while writing the NBT data into the ByteBuf.
     */
    public void writeNbtTag(Tag value) {

        Preconditions.checkArgument(value != null, "PacketSerializer cannot write null NBT tag");

        try (ByteBufOutputStream dataOutput = new ByteBufOutputStream(byteBuf)) {
            dataOutput.writeByte(value.getTypeId());
            value.write(dataOutput, 0, null);
        } catch (IOException e) {
            throw new RuntimeException("Error while writing NBT data into the ByteBuf", e);
        }

    }

    /**
     * Retrieves the underlying ByteBuf instance associated with this PacketSerializer.
     *
     * <p>
     * This method provides access to the raw ByteBuf used by the PacketSerializer for efficient
     * reading and writing operations. Modifying the returned ByteBuf directly may affect the internal state
     * of the serializer, so caution is advised.
     * </p>
     *
     * @return The ByteBuf instance used by this PacketSerializer.
     */
    public ByteBuf getHandle() {
        return byteBuf;
    }

    /**
     * Retrieves a chat component converted into legacy text from the ByteBuf.
     * @return The legacy text converted from the chat component read.
     */
    public abstract String readComponent();

    /**
     * Reads a NumberFormat from the ByteBuf.
     *
     * @return The NumberFormat instance to be serialized into the ByteBuf.
     * @throws UnsupportedOperationException If the versioned PacketSerializer does not support this.
     */
    public abstract NumberFormat readNumberFormat();

    /**
     * Converts legacy text into a chat component and writes it into the ByteBuf.
     *
     * @param value The string to convert and write as a ChatComponent.
     * @throws IllegalArgumentException If the provided string is null.
     */
    public abstract void writeComponent(String value);

    /**
     * Writes a NumberFormat into the ByteBuf.
     *
     * @param value The NumberFormat instance to be serialized into the ByteBuf.
     * @throws UnsupportedOperationException If the versioned PacketSerializer does not support this.
     * @throws IllegalArgumentException      If the provided NumberFormat is null.
     */
    public abstract void writeNumberFormat(NumberFormat value);

    public interface Reader<T> extends Function<PacketSerializer, T> {}

    public interface Writer<T> extends BiConsumer<PacketSerializer, T> {}

}
