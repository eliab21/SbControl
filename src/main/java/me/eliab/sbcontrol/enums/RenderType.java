package me.eliab.sbcontrol.enums;

/**
 * The render type in which the scores will be displayed
 */
public enum RenderType {

    INTEGER("integer"),
    HEARTS("hearts");

    private final String value;

    RenderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RenderType byValue(String value) {

        if (value == null) {
            throw new IllegalArgumentException("Cannot get RenderType from null value");
        }

        switch (value) {
            case "integer": return INTEGER;
            case "hearts": return HEARTS;
            default: throw new IllegalArgumentException("Invalid RenderType value");
        }

    }

}
