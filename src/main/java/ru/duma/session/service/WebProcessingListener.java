package ru.duma.session.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Веб-версия ProcessingStatusListener.
 * Вместо обновления JavaFX-компонентов — накапливает лог в памяти.
 * Контроллер читает лог через SSE (Server-Sent Events).
 */
public class WebProcessingListener {

    private final List<String> log = new CopyOnWriteArrayList<>();
    private volatile String status = "Ожидание...";
    private volatile boolean completed = false;
    private volatile boolean failed = false;
    private volatile String errorMessage = null;

    public void onLog(String message) {
        log.add(message);
    }

    public void onStatusUpdate(String message, String color) {
        status = message;
        log.add("[СТАТУС] " + message);
    }

    public void onProgressComplete() {
        completed = true;
    }

    public void onProgressIndeterminate() {
        log.add("Запуск обработки...");
    }

    public void onError(String message, Throwable throwable) {
        failed = true;
        errorMessage = message + (throwable != null ? ": " + throwable.getMessage() : "");
        log.add("[ОШИБКА] " + errorMessage);
    }

    // Геттеры для контроллера
    public List<String> getLog() { return log; }
    public String getStatus() { return status; }
    public boolean isCompleted() { return completed; }
    public boolean isFailed() { return failed; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isDone() { return completed || failed; }
}
