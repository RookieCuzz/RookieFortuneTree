package com.cuzz.rookiefortunetree.menu.icon;

import java.util.List;

public record MenuIconDefinition(
        String iconId,
        String material,
        String name,
        List<String> lore
) {
}

