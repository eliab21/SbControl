package me.eliab.sbcontrol;

import com.google.common.base.Preconditions;
import me.eliab.sbcontrol.network.versions.Version;

abstract class BoardComponent {

    private static final Version VERSION = SbControl.getVersion();

    final Board board;
    boolean deleted = false;

    BoardComponent(Board board) {
        Preconditions.checkArgument(board != null, "BoardComponent cannot be created with null Board");
        this.board = board;
    }

    /**
     * Gets the associated {@link Board} instance.
     * @return The board associated with this component.
     * @throws IllegalStateException If the board component has been destroyed.
     */
    public Board getBoard() {
        checkState();
        return board;
    }

    public abstract void destroy();

    void checkState() {
        if (deleted) {
            throw new IllegalStateException("Unregistered board component");
        }
    }

    static void checkLengthForVersion(Version maxVersion, String value, int maxLength, String errorMessage) {
        if (VERSION.isLowerOrEqualThan(maxVersion) && value.length() >= maxLength) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    static void checkVersion(Version version, String errorMessage) {
        if (!VERSION.isHigherOrEqualThan(version)) {
            throw new UnsupportedOperationException(errorMessage);
        }
    }

}
