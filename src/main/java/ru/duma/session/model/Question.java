package ru.duma.session.model;

import org.jetbrains.annotations.NotNull;
import java.nio.file.Path;
import java.util.List;

/**
 * Вопрос повестки с докладчиками и документами.
 * Перенесено из JavaFX-версии без изменений.
 */
public record Question(
        String question,
        List<String> speakers,
        List<Path> documents) {

    public int getNumberQuestion() {
        return Integer.parseInt(this.question.split("\\.")[0]);
    }

    public @NotNull Path getDocumentOrderDay() {
        for (Path document : this.documents) {
            if (document.getFileName().toString().equals("Проект_закона.pdf")) {
                return document;
            }
        }
        return Path.of("#");
    }

    public @NotNull Path getResolution() {
        for (Path document : this.documents) {
            if (document.getFileName().toString().equals("Постановление.pdf")) {
                return document;
            }
        }
        return Path.of("#");
    }
}
