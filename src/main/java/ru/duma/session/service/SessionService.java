package ru.duma.session.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.duma.session.model.ProcessingContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет запущенными задачами обработки.
 * Каждый запуск получает уникальный jobId,
 * по которому фронтенд опрашивает статус через /api/status/{jobId}.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ProcessingOrchestrator orchestrator;

    // jobId -> listener с логом и статусом
    private final Map<String, WebProcessingListener> jobs = new ConcurrentHashMap<>();

    /**
     * Запускает обработку и возвращает jobId.
     */
    public String startProcessing(
            String resolutionDir,
            String sessionDir,
            String filesType,
            String agenda,
            String controlWord1,
            String controlWord2,
            String osType,
            String exportFileName) {

        String jobId = UUID.randomUUID().toString();
        WebProcessingListener listener = new WebProcessingListener();
        jobs.put(jobId, listener);

        ProcessingContext context = new ProcessingContext(
                Path.of(resolutionDir),
                Path.of(sessionDir),
                String.format(".*.(%s)$", filesType),
                agenda,
                sessionDir,
                controlWord1,
                controlWord2,
                osType,
                exportFileName,
                false
        );

        listener.onProgressIndeterminate();
        listener.onStatusUpdate("Обработка файлов, ждите...", "#FFA500");

        CompletableFuture<Void> future = orchestrator.process(context, listener);
        future.exceptionally(throwable -> {
            listener.onError("Процесс прерван из-за ошибки", throwable.getCause());
            return null;
        });

        return jobId;
    }

    public WebProcessingListener getListener(String jobId) {
        return jobs.get(jobId);
    }

    public boolean jobExists(String jobId) {
        return jobs.containsKey(jobId);
    }
}
