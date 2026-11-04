package dev.dlc.client.module;

import java.util.List;

public final class ModuleCategories {
    public static final List<String> ALL =
            List.of("Combat", "Movement", "Player", "Visuals", "UI", "Misc");

    private ModuleCategories() {}

    public static boolean contains(String category) {
        return category != null && ALL.contains(category.strip());
    }
}
