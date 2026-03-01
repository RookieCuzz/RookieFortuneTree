package com.cuzz.rookiefortunetree.config;

import java.util.List;

public record MenuIconDefinition(
        String iconId,
        String material,
        String name,
        List<String> lore
) {
}
