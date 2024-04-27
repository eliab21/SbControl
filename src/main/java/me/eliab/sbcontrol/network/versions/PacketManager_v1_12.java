package me.eliab.sbcontrol.network.versions;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import me.eliab.sbcontrol.enums.*;
import me.eliab.sbcontrol.network.PacketManager;
import me.eliab.sbcontrol.network.PacketSerializer;
import me.eliab.sbcontrol.network.game.*;
import me.eliab.sbcontrol.scores.NumberFormat;
import me.eliab.sbcontrol.util.ChatUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

class PacketManager_v1_12 extends PacketManager {

    PacketManager_v1_12(Version version) throws ReflectiveOperationException {
        super(version);
    }

    @Override
    public PacketSerializer createPacketSerializer(ByteBuf byteBuf) {
        return new PacketSerializer_v1_12(byteBuf);
    }

    @Override
    public PacketDisplayObjective createPacketDisplayObjective() {
        return new PacketDisplayObjective_v1_12();
    }

    @Override
    public PacketObjective createPacketObjective() {
        return new PacketObjective_v1_12();
    }

    @Override
    public PacketTeam createPacketTeam() {
        return new PacketTeam_v1_12();
    }

    @Override
    public PacketScore createPacketScore() {
        return new PacketScore_v1_12();
    }

    @Override
    public PacketResetScore createPacketResetScore() {
        throw new UnsupportedOperationException("PacketManager cannot create PacketResetScore because it is not supported for this version");
    }

    static class PacketSerializer_v1_12 extends PacketSerializer {

        PacketSerializer_v1_12(ByteBuf byteBuf) {
            super(byteBuf);
        }

        @Override
        public String readComponent() {
            return BaseComponent.toLegacyText(ComponentSerializer.parse(readString()));
        }

        @Override
        public NumberFormat readNumberFormat() {
            throw new UnsupportedOperationException("PacketSerializer cannot deserialize NumberFormat because this is not supported for this version");
        }

        @Override
        public void writeComponent(String value) {
            Preconditions.checkArgument(value != null, "PacketSerializer cannot write null string as ChatComponent");
            BaseComponent[] components = TextComponent.fromLegacyText(value);
            writeString(ComponentSerializer.toString(components));
        }

        @Override
        public void writeNumberFormat(NumberFormat value) {
            throw new UnsupportedOperationException("PacketSerializer cannot serialize NumberFormat because this is not supported for this version");
        }

    }

    static class PacketDisplayObjective_v1_12 extends PacketDisplayObjective {

        @Override
        public void read(PacketSerializer packetSerializer) {
            position = Position.values()[packetSerializer.readByte()];
            scoreName = packetSerializer.readString();
        }

        @Override
        public void write(PacketSerializer packetSerializer) {
            packetSerializer.writeByte(position.ordinal());
            packetSerializer.writeString(scoreName);
        }

    }

    static class PacketObjective_v1_12 extends PacketObjective {

        @Override
        public void read(PacketSerializer packetSerializer) {

            objectiveName = packetSerializer.readString();
            mode = Mode.values()[packetSerializer.readByte()];

            if (mode != Mode.REMOVE) {
                objectiveValue = packetSerializer.readString();
                type = RenderType.byValue(packetSerializer.readString());
            }

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(objectiveName);
            packetSerializer.writeByte(mode.ordinal());

            if (mode != Mode.REMOVE) {
                packetSerializer.writeString(objectiveValue);
                packetSerializer.writeString(type.getValue());
            }

        }

        @Override
        public void setNumberFormat(NumberFormat numberFormat) {
            throw new UnsupportedOperationException("PacketObjective cannot have NumberFormat because it is not supported for this version");
        }

        @Override
        public NumberFormat getNumberFormat() {
            throw new UnsupportedOperationException("PacketObjective cannot have NumberFormat because it is not supported for this version");
        }

    }

    static class PacketTeam_v1_12 extends PacketTeam {

        @Override
        public void read(PacketSerializer packetSerializer) {

            teamName = packetSerializer.readString();
            mode = Mode.values()[packetSerializer.readByte()];

            if (mode == Mode.CREATE || mode == Mode.UPDATE) {

                teamDisplayName = packetSerializer.readString();
                teamPrefix = packetSerializer.readString();
                teamSuffix = packetSerializer.readString();
                friendlyFlags = FriendlyFlags.values()[packetSerializer.readByte()];
                nameTagVisibility = NameTagVisibility.byValue(packetSerializer.readString());
                collisionRule = CollisionRule.byValue(packetSerializer.readString());
                teamColor = ChatUtils.colorByOrdinal(packetSerializer.readByte());

                if (mode == Mode.CREATE) {
                    entities = packetSerializer.readList(PacketSerializer::readString);
                }

            } else if (mode == Mode.ADD_ENTITIES || mode == Mode.REMOVE_ENTITIES) {
                entities = packetSerializer.readList(PacketSerializer::readString);
            }

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(teamName);
            packetSerializer.writeByte(mode.ordinal());

            if (mode == Mode.CREATE || mode == Mode.UPDATE) {

                packetSerializer.writeString(teamDisplayName);
                packetSerializer.writeString(teamPrefix);
                packetSerializer.writeString(teamSuffix);
                packetSerializer.writeByte(friendlyFlags.ordinal());
                packetSerializer.writeString(nameTagVisibility.getValue());
                packetSerializer.writeString(collisionRule.getValue());
                packetSerializer.writeByte(teamColor.ordinal());

                if (mode == Mode.CREATE) {
                    packetSerializer.writeCollection(entities, PacketSerializer::writeString);
                }

            } else if (mode == Mode.ADD_ENTITIES || mode == Mode.REMOVE_ENTITIES) {
                packetSerializer.writeCollection(entities, PacketSerializer::writeString);
            }

        }

    }

    static class PacketScore_v1_12 extends PacketScore {

        @Override
        public void read(PacketSerializer packetSerializer) {

            entityName = packetSerializer.readString();
            action = Action.values()[packetSerializer.readByte()];

            if (action == Action.UPDATE) {
                objectiveName = packetSerializer.readString();
                value = packetSerializer.readVarInt();
            }

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(entityName);
            packetSerializer.writeByte(action.ordinal());

            if (action == Action.UPDATE) {
                packetSerializer.writeString(objectiveName);
                packetSerializer.writeVarInt(value);
            }

        }

        @Override
        public void setDisplayName(String displayName) {
            throw new UnsupportedOperationException("PacketScore cannot have display name because it is not supported for this version");
        }

        @Override
        public void setNumberFormat(NumberFormat numberFormat) {
            throw new UnsupportedOperationException("PacketScore cannot have NumberFormat because it is not supported for this version");
        }

        @Override
        public String getDisplayName() {
            throw new UnsupportedOperationException("PacketScore cannot have display name because it is not supported for this version");
        }

        @Override
        public NumberFormat getNumberFormat() {
            throw new UnsupportedOperationException("PacketScore cannot have NumberFormat because it is not supported for this version");
        }

    }

}
