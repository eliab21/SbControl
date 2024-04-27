package me.eliab.sbcontrol.network.versions;

import me.eliab.sbcontrol.enums.Position;
import me.eliab.sbcontrol.network.PacketSerializer;
import me.eliab.sbcontrol.network.game.PacketDisplayObjective;

class PacketManager_v1_20_2 extends PacketManager_v1_13 {

    PacketManager_v1_20_2(Version version) throws ReflectiveOperationException {
        super(version);
    }

    @Override
    public PacketDisplayObjective createPacketDisplayObjective() {
        return new PacketDisplayObjective_1_20_2();
    }

    static class PacketDisplayObjective_1_20_2 extends PacketDisplayObjective {

        @Override
        public void read(PacketSerializer packetSerializer) {
            position = packetSerializer.readEnum(Position.class);
            scoreName = packetSerializer.readString();
        }

        @Override
        public void write(PacketSerializer packetSerializer) {
            packetSerializer.writeEnum(position);
            packetSerializer.writeString(scoreName);
        }

    }

}
