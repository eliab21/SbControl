package me.eliab.sbcontrol.network.versions;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import dev.dewy.nbt.api.Tag;
import dev.dewy.nbt.tags.collection.CompoundTag;
import dev.dewy.nbt.tags.primitive.ByteTag;
import dev.dewy.nbt.tags.primitive.StringTag;
import io.netty.buffer.ByteBuf;
import me.eliab.sbcontrol.enums.NumberFormatType;
import me.eliab.sbcontrol.enums.RenderType;
import me.eliab.sbcontrol.network.PacketSerializer;
import me.eliab.sbcontrol.network.game.PacketObjective;
import me.eliab.sbcontrol.network.game.PacketResetScore;
import me.eliab.sbcontrol.network.game.PacketScore;
import me.eliab.sbcontrol.scores.BlankFormat;
import me.eliab.sbcontrol.scores.FixedFormat;
import me.eliab.sbcontrol.scores.NumberFormat;
import me.eliab.sbcontrol.scores.StyledFormat;
import me.eliab.sbcontrol.util.ChatUtils;

class PacketManager_v1_20_3 extends PacketManager_v1_20_2 {

    PacketManager_v1_20_3(Version version) throws ReflectiveOperationException {
        super(version);
    }

    @Override
    public PacketSerializer createPacketSerializer(ByteBuf byteBuf) {
        return new PacketSerializer_v1_20_3(byteBuf);
    }

    @Override
    public PacketObjective createPacketObjective() {
        return new PacketObjective_1_20_3();
    }

    @Override
    public PacketScore createPacketScore() {
        return new PacketScore_1_20_3();
    }

    @Override
    public PacketResetScore createPacketResetScore() {
        return new PacketResetScore_v1_20_3();
    }

    static class PacketSerializer_v1_20_3 extends PacketSerializer {

        PacketSerializer_v1_20_3(ByteBuf byteBuf) {
            super(byteBuf);
        }

        @Override
        public String readComponent() {
            return ChatUtils.legacyTextFromNbt(readNBTTag());
        }

        @Override
        public NumberFormat readNumberFormat() {

            NumberFormatType type = readEnum(NumberFormatType.class);

            if (type == NumberFormatType.BLANK) {
                return BlankFormat.getInstance();
            }

            CompoundTag compoundTag = ((Function<Tag, CompoundTag>) tag -> {

                if (tag instanceof CompoundTag) {
                    return (CompoundTag) tag;
                }
                throw new RuntimeException("PacketSerializer cannot read invalid number format");

            }).apply(readNBTTag());

            if (type == NumberFormatType.STYLED) {

                StringTag color = compoundTag.getString("color");
                ByteTag obfuscated = compoundTag.getByte("obfuscated");
                ByteTag bold = compoundTag.getByte("bold");
                ByteTag strikethrough = compoundTag.getByte("strikethrough");
                ByteTag underlined = compoundTag.getByte("underlined");
                ByteTag italic = compoundTag.getByte("italic");

                StyledFormat.Builder builder = new StyledFormat.Builder();
                if (color != null) builder.setColor(color.getValue());
                if (obfuscated != null) builder.setObfuscated(obfuscated.getValue() != 0);
                if (bold != null) builder.setBold(bold.getValue() != 0);
                if (strikethrough != null) builder.setStrikethrough(strikethrough.getValue() != 0);
                if (underlined != null) builder.setUnderlined(underlined.getValue() != 0);
                if (italic != null) builder.setItalic(italic.getValue() != 0);

                return builder.create();

            } else if (type == NumberFormatType.FIXED) {
                return new FixedFormat(ChatUtils.legacyTextFromNbt(compoundTag));
            } else {
                throw new RuntimeException("PacketSerializer found an unknown number format type");
            }

        }

        @Override
        public void writeComponent(String value) {
            Preconditions.checkArgument(value != null, "PacketSerializer cannot write null string as ChatComponent");
            writeNbtTag(ChatUtils.nbtFromLegacyText(value));
        }

        @Override
        public void writeNumberFormat(NumberFormat value) {

            writeEnum(value.getType());

            if (value instanceof BlankFormat) {

            } else if (value instanceof StyledFormat) {

                StyledFormat style = (StyledFormat) value;
                CompoundTag compoundTag = ChatUtils.compoundTagOf(
                        null,
                        style.getColor(),
                        style.isObfuscated(),
                        style.isBold(),
                        style.isStrikethrough(),
                        style.isUnderlined(),
                        style.isItalic()
                );

                writeNbtTag(compoundTag);

            } else if (value instanceof FixedFormat) {

                FixedFormat format = (FixedFormat) value;
                writeNbtTag(ChatUtils.nbtFromLegacyText(format.getText()));

            } else {
                throw new RuntimeException("PacketSerializer cannot write unknown NumberFormat instance");
            }

        }

    }

    static class PacketObjective_1_20_3 extends PacketObjective {

        @Override
        public void read(PacketSerializer packetSerializer) {

            objectiveName = packetSerializer.readString();
            mode = Mode.values()[packetSerializer.readByte()];

            if (mode != Mode.REMOVE) {
                objectiveValue = packetSerializer.readComponent();
                type = packetSerializer.readEnum(RenderType.class);
                numberFormat = packetSerializer.readNullable(PacketSerializer::readNumberFormat);
            }

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(objectiveName);
            packetSerializer.writeByte(mode.ordinal());

            if (mode != Mode.REMOVE) {
                packetSerializer.writeComponent(objectiveValue);
                packetSerializer.writeEnum(type);
                packetSerializer.writeNullable(numberFormat, PacketSerializer::writeNumberFormat);
            }

        }

    }

    static class PacketScore_1_20_3 extends PacketScore {

        @Override
        public void read(PacketSerializer packetSerializer) {

            entityName = packetSerializer.readString();
            objectiveName = packetSerializer.readString();
            value = packetSerializer.readVarInt();
            displayName = packetSerializer.readNullable(PacketSerializer::readComponent);
            numberFormat = packetSerializer.readNullable(PacketSerializer::readNumberFormat);

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(entityName);
            packetSerializer.writeString(objectiveName);
            packetSerializer.writeVarInt(value);
            packetSerializer.writeNullable(displayName, PacketSerializer::writeComponent);
            packetSerializer.writeNullable(numberFormat, PacketSerializer::writeNumberFormat);

        }

        @Override
        public void setAction(Action action) {
            throw new UnsupportedOperationException("PacketScore cannot have Action because it is not supported for this version");
        }

        @Override
        public Action getAction() {
            throw new UnsupportedOperationException("PacketScore cannot have Action because it is not supported for this version");
        }

    }

    static class PacketResetScore_v1_20_3 extends PacketResetScore {

        @Override
        public void read(PacketSerializer packetSerializer) {
            entityName = packetSerializer.readString();
            objectiveName = packetSerializer.readNullable(PacketSerializer::readString);
        }

        @Override
        public void write(PacketSerializer packetSerializer) {
            packetSerializer.writeString(entityName);
            packetSerializer.writeNullable(objectiveName, PacketSerializer::writeString);
        }

    }

}
