package me.eliab.sbcontrol;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapMaker;
import io.netty.channel.Channel;
import me.eliab.sbcontrol.enums.Position;
import me.eliab.sbcontrol.enums.RenderType;
import me.eliab.sbcontrol.network.PacketManager;
import me.eliab.sbcontrol.network.game.*;
import me.eliab.sbcontrol.network.versions.Version;
import me.eliab.sbcontrol.scores.BlankFormat;
import me.eliab.sbcontrol.util.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * The Sidebar class facilitates the management of Minecraft scoreboards for players, supporting up to 15 lines.
 * It provides an intuitive set of methods for creating, updating, and removing sidebars, helping developers to effortlessly
 * present information within a player's sidebar.
 *
 * <p>To get or create a player sidebar use {@link #get(Player)}</p>
 *
 * <p><strong>Note:</strong> It can be safely used asynchronously as everything is at packet level.</p>
 */
public class Sidebar {

    private static final Version VERSION = SbControl.getVersion();
    private static final Map<UUID, Sidebar> SIDEBARS = new MapMaker().weakKeys().makeMap();

    /**
     * Retrieves or creates a sidebar for the specified player.
     *
     * @param player The player for whom to retrieve or create the sidebar.
     * @return The sidebar associated with the player, or a newly created sidebar if none exists for the player.
     * @throws IllegalArgumentException If the given player is null.
     */
    public static Sidebar get(Player player) {

        Sidebar sidebar = SIDEBARS.get(player.getUniqueId());

        if (sidebar == null || sidebar.channelReference.get() == null || !sidebar.channelReference.get().isOpen()) {

            if (VERSION.isHigherOrEqualThan(Version.V1_20_3)) {
                sidebar = new Sidebar_v1_20_3(player);
            } else if (VERSION.isHigherOrEqualThan(Version.V1_13)) {
                sidebar = new Sidebar_v1_13(player);
            } else {
                sidebar = new Sidebar_v1_12(player);
            }

            SIDEBARS.put(player.getUniqueId(), sidebar);

        }

        return sidebar;

    }

    /**
     * Removes the current associated sidebar with the specified player.
     * @param player The player from whom to remove the sidebar.
     */
    public static void remove(Player player) {
        Sidebar sidebar = SIDEBARS.get(player.getUniqueId());
        if (sidebar != null) {
            sidebar.destroy();
        }
    }

    static final PacketManager PACKET_MANAGER = SbControl.getPacketManager();
    static final int MAX_LINES = 15;
    static final String OBJECTIVE_NAME = "{sidebar}";
    static final String LINE_NAME = "{line_%2d}";

    final UUID playerID;
    final WeakReference<Channel> channelReference;
    String title = "";
    final String[] lines = new String[MAX_LINES];
    boolean deleted = false;

    Sidebar(Player player) {

        Preconditions.checkArgument(player != null, "Sidebar cannot have null player");
        playerID = player.getUniqueId();
        channelReference = new WeakReference<>(SbControl.getPacketManager().getPlayerChannel(player));

        sendObjectivePacket(PacketObjective.Mode.CREATE);
        sendDisplayPacket(true);

    }

    /**
     * Removes the sidebar and all its elements from the player. After calling this method
     * the sidebar becomes unusable and calling any of methods will throw {@code IllegalStateException}.
     *
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public synchronized void destroy() {
        checkState();
        removeLines();
        sendObjectivePacket(PacketObjective.Mode.REMOVE);
        deleted = true;
        SIDEBARS.remove(playerID, this);
    }

    /**
     * Sets the title of the sidebar.
     *
     * @param title The display name.
     * @throws IllegalArgumentException If the title value is null.
     *                                  If the title is larger than 32 characters
     *                                  <strong>(only in 1.12 version)</strong>.
     * @throws IllegalStateException    If the sidebar has been deleted.
     */
    public void setTitle(String title) {

        checkState();
        Preconditions.checkArgument(title != null, "Sidebar set title with null value");

        this.title = ChatUtils.setColors(title);
        sendObjectivePacket(PacketObjective.Mode.UPDATE);

    }

    /**
     * Sets the lines for the sidebar in the order they are given from top to bottom.
     * If there were lines already set, it will remove them. If a value is null, it will not display that line.
     *
     * @param lines A varargs array of Strings representing the lines to display.
     * @throws IllegalArgumentException If the array is null.
     *                                  If the array length is higher than 15.
     *                                  If a line value is larger than 32 characters
     *                                  <strong>(only in 1.12 version)</strong>.
     * @throws IllegalStateException    If the sidebar has been deleted.
     */
    public void setLines(String... lines) {
        Preconditions.checkArgument(lines != null, "Sidebar cannot set lines from null array");
        setLines(Arrays.asList(lines));
    }


    /**
     * Sets the lines for the sidebar in the order they are given from top to bottom.
     * If there were lines already set, it will remove them. If a value is null, it will not display that line.
     *
     * @param lines A collection with the values to display.
     * @throws IllegalArgumentException If the collection is null.
     *                                  If the collection size is higher than 15.
     *                                  If a line value is larger than 32 characters
     *                                  <strong>(only in 1.12 version)</strong>.
     * @throws IllegalStateException    If the sidebar has been deleted.
     */
    public void setLines(Collection<String> lines) {

        checkState();
        Preconditions.checkArgument(lines != null, "Sidebar cannot set lines values from null Collection");
        int index = checkIndex(lines.size() - 1);

        removeLines();

        for (String value : lines) {
            if (value != null) {
                setLine(index, value);
            }
            index--;
        }

    }

    /**
     * Sets the value of a line at the specified index on the sidebar.
     *
     * @param index The index at which to set the line.
     *              Must be within the valid range of lines (0 - 14).
     * @param value  The value to be displayed on the specified line.
     * @throws IllegalArgumentException  If the line value is null.
     *                                   If the index is negative.
     *                                   If the line value is larger than 32 characters
     *                                   <strong>(only in 1.12 version)</strong>.
     * @throws IndexOutOfBoundsException If the index is higher than 14.
     * @throws IllegalStateException     If the sidebar has been deleted.
     */
    public void setLine(int index, String value) {

        checkState();
        checkIndex(index);
        Preconditions.checkArgument(value != null, "Sidebar cannot set line with null value");

        LineAction action = (lines[index] != null) ? LineAction.UPDATE : LineAction.CREATE;
        lines[index] = ChatUtils.setColors(value);
        sendLinePacket(index, action);

    }

    /**
     * Removes all the lines of the sidebar.
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public void removeLines() {
        checkState();
        for (int i = 0; i < MAX_LINES; i++) {
            removeLine(i);
        }
    }

    /**
     * Removes the line at the given index of the sidebar.
     * Must be within the valid range of lines (0 - 14).
     *
     * @param index The line index to remove.
     * @throws IllegalArgumentException  If the index is negative.
     * @throws IndexOutOfBoundsException If the index is higher than 14.
     * @throws IllegalStateException     If the sidebar has been deleted.
     */
    public void removeLine(int index) {

        checkState();
        checkIndex(index);

        if (lines[index] != null) {
            sendLinePacket(index, LineAction.REMOVE);
            lines[index] = null;
        }

    }

    /**
     * Displays or removes the sidebar for the associated player.
     *
     * @param flag A boolean value indicating whether to display ({@code true}) or remove ({@code false}) the sidebar.
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public void display(boolean flag) {
        checkState();
        sendDisplayPacket(flag);
    }

    /**
     * Retrieves the associated player to this sidebar.
     *
     * @return The associated player.
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public Player getPlayer() {
        checkState();
        return Bukkit.getPlayer(playerID);
    }

    /**
     * Retrieves the current title of the sidebar.
     *
     * @return The title of the sidebar.
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public String getTitle() {
        checkState();
        return title;
    }

    /**
     * Retrieves the current content of the lines in the sidebar.
     * The returned array represents the content of each line from top to bottom.
     *
     * @return An array containing the content of each line in the sidebar.
     * @throws IllegalStateException If the sidebar has been deleted.
     */
    public String[] getLines() {
        checkState();
        return Arrays.copyOf(lines, MAX_LINES);
    }

    /**
     * Retrieves the value of a specific line in the sidebar.
     * Must be within the valid range of lines (0 - 14).
     *
     * @param index The index of the line whose content is to be retrieved.
     * @return The content of the line at the specified index.
     * @throws IllegalArgumentException  If the index is negative.
     * @throws IndexOutOfBoundsException If the index is higher than 14.
     * @throws IllegalStateException     If the sidebar has been deleted.
     */
    public String getLine(int index) {
        checkState();
        return lines[checkIndex(index)];
    }

    /**
     * Checks whether the sidebar has been deleted.
     * @return {@code true} if the sidebar has been deleted, {@code false} otherwise.
     */
    public boolean isDeleted() {
        return deleted;
    }

    void sendDisplayPacket(boolean flag) {
        PacketDisplayObjective packetDisplayObjective = PACKET_MANAGER.createPacketDisplayObjective();
        packetDisplayObjective.setPosition(Position.SIDEBAR);
        packetDisplayObjective.setScoreName(flag ? OBJECTIVE_NAME : "");
        PACKET_MANAGER.sendPacket(channelReference.get(), packetDisplayObjective);
    }

    String getEntity(int index) {
        return ChatUtils.colorByOrdinal(MAX_LINES - index).toString();
    }

    void sendObjectivePacket(PacketObjective.Mode mode) {}

    void sendLinePacket(int index, LineAction action) {}

    private int checkIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Sidebar cannot have negative lines");
        } else if (index >= MAX_LINES) {
            throw new IndexOutOfBoundsException("Sidebar cannot have more than " + MAX_LINES + " lines");
        }
        return index;
    }

    private void checkState() {
        if (deleted) {
            throw new UnsupportedOperationException("Sidebar has already been deleted");
        }
    }

    private enum LineAction {
        CREATE,
        REMOVE,
        UPDATE
    }

    private static class Sidebar_v1_13 extends Sidebar {

        private Sidebar_v1_13(Player player) {
            super(player);
        }

        private static final PacketTeam.Mode[] TEAM_MODES = PacketTeam.Mode.values();
        private static final PacketScore.Action[] SCORE_ACTIONS = PacketScore.Action.values();

        @Override
        void sendObjectivePacket(PacketObjective.Mode mode) {

            PacketObjective packet = PACKET_MANAGER.createPacketObjective();
            packet.setObjectiveName(OBJECTIVE_NAME);
            packet.setMode(mode);

            if (mode != PacketObjective.Mode.REMOVE) {
                packet.setObjectiveValue(title);
                packet.setType(RenderType.INTEGER);
            }

            PACKET_MANAGER.sendPacket(channelReference.get(), packet);

        }

        @Override
        void sendLinePacket(int index, LineAction action) {

            sendPacketTeam(TEAM_MODES[action.ordinal()], index, lines[index]);

            if (action != LineAction.UPDATE) {
                sendPacketScore(SCORE_ACTIONS[action.ordinal()], index);
            }

        }

        void sendPacketTeam(PacketTeam.Mode mode, int index, String line) {

            PacketTeam packetTeam = PACKET_MANAGER.createPacketTeam();
            packetTeam.setTeamName(java.lang.String.format(LINE_NAME, index));
            packetTeam.setMode(mode);

            if (mode == PacketTeam.Mode.UPDATE || mode == PacketTeam.Mode.CREATE) {
                packetTeam.setTeamPrefix(line);
                if (mode == PacketTeam.Mode.CREATE) {
                    packetTeam.setEntities(Collections.singleton(getEntity(index)));
                }
            }

            PACKET_MANAGER.sendPacket(channelReference.get(), packetTeam);

        }

        void sendPacketScore(PacketScore.Action action, int index) {
            PacketScore packetScore = PACKET_MANAGER.createPacketScore();
            packetScore.setEntityName(getEntity(index));
            packetScore.setAction(action);
            packetScore.setObjectiveName(OBJECTIVE_NAME);
            PACKET_MANAGER.sendPacket(channelReference.get(), packetScore);
        }

    }

    private static class Sidebar_v1_12 extends Sidebar_v1_13 {

        private Sidebar_v1_12(Player player) {
            super(player);
        }

        @Override
        void sendObjectivePacket(PacketObjective.Mode mode) {
            checkLength(title, "Sidebar title cannot be larger than 32 characters");
            super.sendObjectivePacket(mode);
        }

        @Override
        void sendPacketTeam(PacketTeam.Mode mode, int index, String line) {

            PacketTeam packetTeam = PACKET_MANAGER.createPacketTeam();
            packetTeam.setTeamName(java.lang.String.format(LINE_NAME, index));
            packetTeam.setMode(mode);

            if (mode == PacketTeam.Mode.UPDATE || mode == PacketTeam.Mode.CREATE) {

                // in 1.12 if the line value is larger than 16 characters we have to divide it
                // due to team prefix and suffix having a max of 16 character,
                // so we will have to use both to be able to display a max of 32 characters
                checkLength(line, "Sidebar line cannot be larger than 32 characters");

                // if the line value is less or equal than 16 characters, there is no problem, so we just assigned it to the prefix
                if (line.length() <= 16) {
                    packetTeam.setTeamPrefix(line);

                } else {

                    // if the line value is larger than 16 characters we will have to divide it
                    int maxLength = 32;
                    int midPoint = 16;

                    // checks if at the middle point there is a color code to avoid chopping it, and if there is,
                    // moves the middle point by one losing one character having 31 in total now
                    if (line.charAt(midPoint - 1) == ChatColor.COLOR_CHAR) {
                        midPoint--;
                        maxLength--;
                    }

                    // assigns the first characters of the line value to the prefix
                    String prefix = line.substring(0, midPoint);
                    // checks what colors were used at the end of the first part
                    String lastColors = ChatColor.getLastColors(prefix);

                    // check if there were colors found
                    if (!lastColors.isEmpty()) {
                        // if there were colors found we modify the max length since we will have to add the colors to the second part as well
                        maxLength -= lastColors.length();
                    }

                    // assigns the colors that were found and adds the second characters of the line value to the suffix
                    String suffix = lastColors + line.substring(midPoint, Math.min(line.length(), maxLength));

                    // uses the team prefix and suffix to be able to display the full line value
                    packetTeam.setTeamPrefix(prefix);
                    packetTeam.setTeamSuffix(suffix);

                }

                if (mode == PacketTeam.Mode.CREATE) {
                    packetTeam.setEntities( Collections.singletonList(getEntity(index)) );
                }

            }

            PACKET_MANAGER.sendPacket(channelReference.get(), packetTeam);

        }

        @Override
        String getEntity(int index) {
            return super.getEntity(index) + ChatColor.RESET;
        }

        private void checkLength(String value, String message) {
            Preconditions.checkArgument(value.length() <= 32, message);
        }

    }

    private static class Sidebar_v1_20_3 extends Sidebar {

        private Sidebar_v1_20_3(Player player) {
            super(player);
        }

        @Override
        void sendObjectivePacket(PacketObjective.Mode mode) {

            PacketObjective packet = PACKET_MANAGER.createPacketObjective();
            packet.setObjectiveName(OBJECTIVE_NAME);
            packet.setMode(mode);

            if (mode != PacketObjective.Mode.REMOVE) {
                packet.setObjectiveValue(title);
                packet.setType(RenderType.INTEGER);
                packet.setNumberFormat(BlankFormat.getInstance());
            }

            PACKET_MANAGER.sendPacket(channelReference.get(), packet);

        }

        @Override
        void sendLinePacket(int index, LineAction action) {
            if (action == LineAction.UPDATE || action == LineAction.CREATE) {
                sendPacketScore(index, lines[index]);
            } else if (action == LineAction.REMOVE){
                sendPacketResetScore(index);
            }
        }

        void sendPacketScore(int index, String line) {
            PacketScore packetScore = PACKET_MANAGER.createPacketScore();
            packetScore.setEntityName(getEntity(index));
            packetScore.setObjectiveName(OBJECTIVE_NAME);
            packetScore.setDisplayName(line);
            PACKET_MANAGER.sendPacket(channelReference.get(), packetScore);
        }

        void sendPacketResetScore(int index) {
            PacketResetScore packetResetScore = PACKET_MANAGER.createPacketResetScore();
            packetResetScore.setEntityName(getEntity(index));
            packetResetScore.setObjectiveName(OBJECTIVE_NAME);
            PACKET_MANAGER.sendPacket(channelReference.get(), packetResetScore);
        }

    }

}
