package ru.duma.session.service;

import org.springframework.stereotype.Service;
import ru.duma.session.model.ProcessingContext;
import ru.duma.session.model.ValidationResult;
import ru.duma.session.utils.Combiner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Оркестратор обработки — запускает все шаги по цепочке через CompletableFuture.
 * Логика идентична JavaFX-версии, только listener заменён на WebProcessingListener.
 */
@Service
public class ProcessingOrchestrator {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CompletableFuture<Void> process(ProcessingContext context, WebProcessingListener listener) {
        return CompletableFuture
                .runAsync(() -> validate(context, listener), executorService)
                .thenRunAsync(() -> copyFiles(context, listener), executorService)
                .thenRunAsync(() -> convertToPdf(context, listener), executorService)
                .thenRunAsync(() -> hashAndExport(context, listener), executorService)
                .thenRun(listener::onProgressComplete)
                .thenRun(() -> listener.onStatusUpdate("Процесс завершён", "#008000"));
    }

    private void validate(ProcessingContext context, WebProcessingListener listener) {
        try {
            ProcessingValidator validator = new ProcessingValidator();
            ValidationResult result = validator.validate(context);
            if (!result.isValid()) {
                listener.onLog("Валидация не пройдена: " + result.getErrorMessage());
                throw new IllegalArgumentException(result.getErrorMessage());
            }
            listener.onLog("Валидация пройдена. Запускаем обработку...");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void copyFiles(ProcessingContext context, WebProcessingListener listener) {
        try {
            listener.onLog("=== Начинаем копирование файлов постановлений ===");
            CopyResolutionFile copier = new CopyResolutionFile(listener);
            copier.copyFilesToDirs(context.resolutionDir(), context.sessionDir(), context.patternFiles());
            listener.onLog("=== Файлы постановлений скопированы ===");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void convertToPdf(ProcessingContext context, WebProcessingListener listener) {
        try {
            listener.onLog("=== Начинаем конвертацию файлов в PDF ===");
            ConvertToPdf converter = new ConvertToPdf(listener, context.osType());
            converter.convert(context.sessionDir(), context.patternFiles());
            listener.onLog("=== Файлы сконвертированы ===");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void hashAndExport(ProcessingContext context, WebProcessingListener listener) {
        try {
            listener.onLog("=== Начинаем формирование HTML-файла ===");
            Combiner combiner = new Combiner(
                    context.agenda(),
                    context.documentsDir(),
                    context.controlWord1(),
                    context.controlWord2()
            );
            HtmlExporter exporter = new HtmlExporter(listener, combiner);
            Path htmlFile = context.sessionDir().resolve(context.exportFileName());
            exporter.export(htmlFile);
            listener.onLog("Выгрузили в: " + htmlFile);
            listener.onLog("=== HTML файл сформирован ===");

            listener.onLog("=== Начинаем создание файла контрольных сумм ===");
            CalculateHashSum hasher = new CalculateHashSum(listener);
            String checksumFile = context.sessionDir().resolve("checksum.sha256").toString();
            hasher.createFilesWithHashSum(
                    context.sessionDir().toString(),
                    context.osType(),
                    checksumFile,
                    htmlFile.toString()
            );
            listener.onLog("Выгрузили в: " + checksumFile);
            listener.onLog("=== Файл с хеш-суммами сформирован ===");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
