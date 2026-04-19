package ru.duma.session.service;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * @author epifancev
 * @Date 01.10.2025
 */

public class ConvertToPdf {

    private final WebProcessingListener listener;
    private final String osType; // "Linux" или "Windows"

    public ConvertToPdf(WebProcessingListener listener, String osType) {
        this.listener = listener;
        this.osType = osType;
    }

    public void convert(Path sessionDir, String filePatternRegex) throws IOException {
        listener.onLog("Сканирование файлов для конвертации в PDF...");
        List<Path> sourceFiles = findSourceFiles(sessionDir, filePatternRegex);
        if (sourceFiles.isEmpty()) {
            listener.onLog("Нет файлов для конвертации по шаблону: " + filePatternRegex);
            listener.onLog("Будем сразу формировать сессию...");
            return;
        }

        for (Path sourceFile : sourceFiles) {
            convertSingleFile(sourceFile);
        }
    }

    private List<Path> findSourceFiles(Path sessionDir, String patternRegex) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(sessionDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                // Удаляем временные файлы Word
                if (name.startsWith("~$")) {
                    try {
                        Files.delete(file);
                        listener.onLog("Удалён временный файл: " + name);
                    } catch (IOException e) {
                        listener.onLog("Не удалось удалить временный файл: " + name);
                    }
                    return FileVisitResult.CONTINUE;
                }
                // Проверяем шаблон
                if (file.toString().toLowerCase().matches(patternRegex)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
    private void convertSingleFile(Path sourceFile) throws IOException {
        Path outputFile = sourceFile.getParent().resolve(
                sourceFile.getFileName().toString().replaceFirst("\\.[^.]+$", ".pdf")
        );
        listener.onLog("Конвертируем: " + sourceFile.getFileName());
        String libreOfficeBin = osType.equals("Windows") ? "soffice.exe": "lowriter";
        // Подготавливаем аргументы для LibreOffice
        List<String> command = Arrays.asList(
                libreOfficeBin,
                "--convert-to", "pdf",
                "--outdir", sourceFile.getParent().toAbsolutePath().toString(),
                sourceFile.toAbsolutePath().toString()
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(sourceFile.getParent().toFile()); // не обязательно, но можно
        pb.redirectErrorStream(false); // оставляем stderr отдельно для детального лога

        try {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutReader = readStream(process.getInputStream(), stdout);
            Thread stderrReader = readStream(process.getErrorStream(), stderr);

            // Ожидаем завершения с таймаутом (10 минут)
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);

            // Ждём завершения чтения потоков
            stdoutReader.join();
            stderrReader.join();

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Таймаут конвертации: " + sourceFile.getFileName());
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = stderr.toString().trim();
                throw new IOException(
                        "Конвертация завершилась с кодом " + exitCode +
                                (errorMsg.isEmpty() ? "" : ": " + errorMsg)
                );
            }
            // Проверяем, что PDF создан
            if (!Files.exists(outputFile)) {
                throw new IOException("PDF-файл не создан: " + outputFile);
            }

            // Удаляем исходный файл только после успешной конвертации
            Files.delete(sourceFile);
            listener.onLog("Успешно сконвертировано: " + sourceFile.getFileName());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Конвертация прервана", e);
        }
    }

//    private void convertSingleFile_old(Path sourceFile) throws IOException {
//        Path outputFile = sourceFile.getParent().resolve(
//                sourceFile.getFileName().toString().replaceFirst("\\.[^.]+$", ".pdf")
//        );
//        listener.onLog("Конвертируем: " + sourceFile.getFileName());
//        // Формируем команду
//        String shell = osType.equals("Linux") ? "bash" : "cmd";
//        String shellOption = osType.equals("Linux") ? "-c" : "/C";
//        String command = String.format(
//                "lowriter --convert-to pdf --outdir \"%s\" \"%s\"",
//                sourceFile.getParent().toAbsolutePath(),
//                sourceFile.toAbsolutePath()
//        );
//
//        try {
//            Process process = Runtime.getRuntime().exec(new String[]{shell, shellOption, command});
//            ProcessBuilder pb = new ProcessBuilder( );
//            // Читаем вывод и ошибки
//            StringBuilder stdout = new StringBuilder();
//            StringBuilder stderr = new StringBuilder();
//
//            Thread stdoutReader = readStream(process.getInputStream(), stdout);
//            Thread stderrReader = readStream(process.getErrorStream(), stderr);
//
//            // Ждём завершения с таймаутом (10 минут)
//            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
//            stdoutReader.join();
//            stderrReader.join();
//
//            if (!finished) {
//                process.destroyForcibly();
//                throw new IOException("Таймаут конвертации: " + sourceFile.getFileName());
//            }
//
//            int exitCode = process.exitValue();
//            if (exitCode != 0) {
//                String errorMsg = stderr.toString().trim();
//                throw new IOException(
//                        "Конвертация завершилась с кодом " + exitCode +
//                                (errorMsg.isEmpty() ? "" : ": " + errorMsg)
//                );
//            }
//            // Проверяем, что PDF создан
//            if (!Files.exists(outputFile)) {
//                throw new IOException("PDF-файл не создан: " + outputFile);
//            }
//            // Удаляем исходный файл ТОЛЬКО после успешной конвертации
//            Files.delete(sourceFile);
//            listener.onLog("✅ Успешно сконвертировано: " + sourceFile.getFileName());
//
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new IOException("Конвертация прервана", e);
//        }
//    }

    private Thread readStream(InputStream stream, StringBuilder output) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                // Пока хз как обработать
            }
        });
        thread.start();
        return thread;
    }

    public static void main(String[] args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("subl");
        pb.start();
    }
}