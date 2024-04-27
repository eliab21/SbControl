package me.eliab.sbcontrol.scores;

import com.google.common.base.Preconditions;
import me.eliab.sbcontrol.enums.NumberFormatType;
import org.bukkit.ChatColor;

/**
 * A number format type that will provide a style to use when formatting the score number.
 * This is similar to a Chat, but only the styling fields are present.
 *
 * <p>
 * To create a new StyleFormat use {@link StyledFormat.Builder}
 * </p>
 */
public final class StyledFormat implements NumberFormat {

    private final String color;
    private final boolean obfuscated;
    private final boolean bold;
    private final boolean strikethrough;
    private final boolean underlined;
    private final boolean italic;

    private StyledFormat(String color, boolean obfuscated, boolean bold, boolean strikethrough, boolean underlined, boolean italic) {
        this.color = color;
        this.obfuscated = obfuscated;
        this.bold = bold;
        this.strikethrough = strikethrough;
        this.underlined = underlined;
        this.italic = italic;
    }

    public String getColor() {
        return color;
    }

    public Boolean isObfuscated() {
        return obfuscated;
    }

    public Boolean isBold() {
        return bold;
    }

    public Boolean isItalic() {
        return italic;
    }

    public Boolean isUnderlined() {
        return underlined;
    }

    public Boolean isStrikethrough() {
        return strikethrough;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NumberFormatType getType() {
        return NumberFormatType.STYLED;
    }

    /**
     * StyledFormat builder
     */
    public static class Builder {

        private String color = null;
        private boolean bold = false;
        private boolean italic = false;
        private boolean underlined = false;
        private boolean strikethrough = false;
        private boolean obfuscated = false;

        public Builder setColor(ChatColor color) {
            Preconditions.checkArgument(color != null, "StyledFormat cannot have null color");
            this.color = color.name().toLowerCase();
            return this;
        }

        public Builder setColor(String color) {
            Preconditions.checkArgument(color != null, "StyledFormat cannot have null string color");
            net.md_5.bungee.api.ChatColor.of(color);
            this.color = color;
            return this;
        }

        public Builder setObfuscated(boolean obfuscated) {
            this.obfuscated = obfuscated;
            return this;
        }

        public Builder setBold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder setStrikethrough(boolean strikethrough) {
            this.strikethrough = strikethrough;
            return this;
        }

        public Builder setUnderlined(boolean underlined) {
            this.underlined = underlined;
            return this;
        }

        public Builder setItalic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public StyledFormat create() {
            return new StyledFormat(color, obfuscated, bold, strikethrough, underlined, italic);
        }

    }

}
