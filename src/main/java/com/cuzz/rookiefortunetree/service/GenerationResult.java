package com.cuzz.rookiefortunetree.service;

import com.cuzz.rookiefortunetree.model.Bubble;

import java.util.List;

public record GenerationResult(
        int deposit,
        int rewardTotal,
        List<Bubble> bubbles
) {
}

