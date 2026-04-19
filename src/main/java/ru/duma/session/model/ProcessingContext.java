package ru.duma.session.model;

import java.nio.file.Path;

/**
 * DTO с входными параметрами для формирования сессии.
 * Перенесено из JavaFX-версии без изменений.
 */
public record ProcessingContext(
        Path resolutionDir,
        Path sessionDir,
        String patternFiles,
        String agenda,
        String documentsDir,
        String controlWord1,
        String controlWord2,
        String osType,
        String exportFileName,
        boolean debugMode
) {}
