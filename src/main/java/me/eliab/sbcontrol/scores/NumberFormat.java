package me.eliab.sbcontrol.scores;

import me.eliab.sbcontrol.enums.NumberFormatType;

/**
 * Represents a strategy for formatting score numbers.
 * Implementations of this interface define how a score number should be formatted.
 */
public interface NumberFormat {

    /**
     * Retrieves the type of this number format.
     * @return The type of the number format.
     */
    NumberFormatType getType();

}
