package me.eliab.sbcontrol.network.versions;

import me.eliab.sbcontrol.enums.CollisionRule;
import me.eliab.sbcontrol.enums.FriendlyFlags;
import me.eliab.sbcontrol.enums.NameTagVisibility;
import me.eliab.sbcontrol.enums.RenderType;
import me.eliab.sbcontrol.network.PacketSerializer;
import me.eliab.sbcontrol.network.game.PacketObjective;
import me.eliab.sbcontrol.network.game.PacketTeam;
import org.bukkit.ChatColor;

class PacketManager_v1_13 extends PacketManager_v1_12 {

    PacketManager_v1_13(Version version) throws ReflectiveOperationException {
        super(version);
    }

    @Override
    public PacketObjective createPacketObjective() {
        return new PacketObjective_v1_13();
    }

    @Override
    public PacketTeam createPacketTeam() {
        return new PacketTeam_v1_13();
    }

    static class PacketObjective_v1_13 extends PacketObjective_v1_12 {

        @Override
        public void read(PacketSerializer packetSerializer) {

            objectiveName = packetSerializer.readString();
            mode = Mode.values()[packetSerializer.readByte()];

            if (mode != Mode.REMOVE) {
                objectiveValue = packetSerializer.readComponent();
                type = packetSerializer.readEnum(RenderType.class);
            }

        }

        @Override
        public void write(PacketSerializer packetSerializer) {

            packetSerializer.writeString(objectiveName);
            packetSerializer.writeByte(mode.ordinal());

            if (mode != Mode.REMOVE) {
                packetSerializer.writeComponent(objectiveValue);
                packetSerializer.writeEnum(type);
            }

        }

    }

    static class PacketTeam_v1_13 extends PacketTeam {

        @Override
        public void read(PacketSerializer packetSerializer) {

            teamName = packetSerializer.readString();
            mode = Mode.values()[packetSerializer.readByte()];

            if (mode == Mode.CREATE || mode == Mode.UPDATE) {

                teamDisplayName = packetSerializer.readComponent();
                friendlyFlags = FriendlyFlags.values()[packetSerializer.readByte()];
                nameTagVisibility = NameTagVisibility.byValue(packetSerializer.readString());
                collisionRule = CollisionRule.byValue(packetSerializer.readString());
                teamColor = packetSerializer.readEnum(ChatColor.class);
                teamPrefix = packetSerializer.readComponent();
                teamSuffix = packetSerializer.readComponent();

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

                packetSerializer.writeComponent(teamDisplayName);
                packetSerializer.writeByte(friendlyFlags.ordinal());
                packetSerializer.writeString(nameTagVisibility.getValue());
                packetSerializer.writeString(collisionRule.getValue());
                packetSerializer.writeEnum(teamColor);
                packetSerializer.writeComponent(teamPrefix);
                packetSerializer.writeComponent(teamSuffix);

                if (mode == Mode.CREATE) {
                    packetSerializer.writeCollection(entities, PacketSerializer::writeString);
                }

            } else if (mode == Mode.ADD_ENTITIES || mode == Mode.REMOVE_ENTITIES) {
                packetSerializer.writeCollection(entities, PacketSerializer::writeString);
            }

        }

    }

}
