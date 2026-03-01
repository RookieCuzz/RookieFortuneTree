package com.cuzz.rookiefortunetree.bootstrap;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.repository.FortuneTreeAttemptRepository;
import com.cuzz.rookiefortunetree.repository.FortuneTreePlayerRepository;

@Component
public final class FortuneTreeSchemaInitializer {
    private final FortuneTreePlayerRepository playerRepository;
    private final FortuneTreeAttemptRepository attemptRepository;

    @Autowired
    public FortuneTreeSchemaInitializer(FortuneTreePlayerRepository playerRepository,
                                        FortuneTreeAttemptRepository attemptRepository) {
        this.playerRepository = playerRepository;
        this.attemptRepository = attemptRepository;
    }

    public void initialize() {
        playerRepository.createTables();
        attemptRepository.createTables();
    }
}

