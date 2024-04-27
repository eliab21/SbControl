package me.eliab.sbcontrol.scores;

import me.eliab.sbcontrol.enums.NumberFormatType;

/**
 * A number format type that will not show the score.
 */
public final class BlankFormat implements NumberFormat {

    private static final BlankFormat INSTANCE = new BlankFormat();

    public static BlankFormat getInstance() {
        return INSTANCE;
    }

    private BlankFormat() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public NumberFormatType getType() {
        return NumberFormatType.BLANK;
    }

}
