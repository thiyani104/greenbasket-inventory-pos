package model;

import java.awt.Color;

/** Category helpers. Categories are plain strings on products; this gives each one a stable chart colour. */
public final class Category {

    private static final Color[] PALETTE = {
        new Color(0x1E8E5A), new Color(0x2F7DD1), new Color(0xD98A0B), new Color(0x8E5BD0),
        new Color(0xD9641F), new Color(0x17A2A2), new Color(0xD64545), new Color(0x6B8E23),
        new Color(0xC2559B), new Color(0x5B6B8E)
    };

    private Category() {}

    public static Color colorFor(String name) {
        int h = name == null ? 0 : name.trim().toLowerCase().hashCode();
        return PALETTE[Math.floorMod(h, PALETTE.length)];
    }
}
