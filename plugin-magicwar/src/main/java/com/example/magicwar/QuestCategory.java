package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;

/** The bracket in front of a quest title. Each tier reads in its own colour so the list is
 * scannable at a glance, both in the upgrade screen and on the sidebar. */
public enum QuestCategory {

    COMMON("공통", NamedTextColor.WHITE),
    SECOND("2차전직", NamedTextColor.AQUA),
    THIRD("3차전직", NamedTextColor.LIGHT_PURPLE);

    private final String label;
    private final NamedTextColor color;

    QuestCategory(String label, NamedTextColor color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    /** "[공통]" etc. */
    public String tag() {
        return "[" + label + "]";
    }
}
