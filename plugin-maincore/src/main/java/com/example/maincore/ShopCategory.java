package com.example.maincore;

import org.bukkit.Material;

public enum ShopCategory {
    NECESSITIES("생필품 구매", Material.PORKCHOP),
    DYE("도료 구매", Material.PINK_DYE),
    DYEABLE_BLOCK("염색 가능한 블록 구매", Material.WHITE_WOOL),
    WOOD_STONE("목재/석재 블록 구매", Material.OAK_LOG),
    DECORATION("장식품 구매", Material.SOUL_TORCH);

    private final String label;
    private final Material icon;

    ShopCategory(String label, Material icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }
}
