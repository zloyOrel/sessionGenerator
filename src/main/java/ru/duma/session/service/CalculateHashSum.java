package ru.duma.session.service;


import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author epifancev
 * @Date 01.10.2025
 * Класс для вычисления хэш суммы для всех документов, после копирования будем проверять на корректность
 */

public class CalculateHashSum {
    private final WebProcessingListener listener;

    public CalculateHashSum(WebProcessingListener listener) {
        this.listener = listener;
    }

    public void createFilesWithHashSum(String sessionDir, String osType, String checksumFilePath, String htmlFilePath) throws IOException {
        listener.onLog("Создаём файл контрольных сумм: " + checksumFilePath);

        String newLine = osType.equals("Linux") ? "\n" : "\r\n";
        Charset charset = osType.equals("Linux") ? StandardCharsets.UTF_8 : Charset.forName("cp866");

        List<Path> files = getSourceFiles(sessionDir);
        deleteExistingChecksumFiles(files);

        try (PrintStream writer = new PrintStream(new FileOutputStream(checksumFilePath), true, charset)) {
            for (Path file : files) {
                String hash = getHashSum(file);
                String relativePath;
                if (file.toString().equals(htmlFilePath)) {
                    relativePath = "*" + file.getFileName();
                } else {
                    // Относительный путь: папка/файл
                    relativePath = "*" + file.getParent().getFileName() + "/" + file.getFileName();
                }
                writer.printf("%s %s%s", hash, relativePath, newLine);
            }
        } catch (IOException e) {
            listener.onLog("Ошибка записи файла контрольных сумм: " + e.getMessage());
            throw new IOException("Не удалось записать файл контрольных сумм: " + checksumFilePath, e);
        }

        listener.onLog("Файл контрольных сумм создан: " + checksumFilePath);
    }

    private List<Path> getSourceFiles(String rootDir) throws IOException {
        return Files.walk(Path.of(rootDir))
                .filter(Files::isRegularFile)
                .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                .collect(Collectors.toList());
    }

    private void deleteExistingChecksumFiles(List<Path> files) throws IOException {
        for (Path file : files) {
            if (file.getFileName().toString().endsWith(".sha256")) {
                Files.delete(file);
                listener.onLog("Удалён старый файл контрольной суммы: " + file.getFileName());
            }
        }
    }

    private String getHashSum(Path file) throws IOException {
        ByteSource byteSource = com.google.common.io.Files.asByteSource(file.toFile());
        HashCode hc = byteSource.hash(Hashing.sha256());
        return hc.toString();
    }

    private static String getHashSum1(Path file) throws IOException {
        ByteSource byteSource = com.google.common.io.Files.asByteSource(file.toFile());
        HashCode hc = byteSource.hash(Hashing.sha256());
        return hc.toString();
    }

    public static void main(String[] args) throws IOException {
        Files.walk(Path.of("/home/epifancev/"))
                .filter(Files::isRegularFile)
                .filter(f -> f.getFileName().toString().endsWith(".docx"))
                .forEach(System.out::println);
    }
}
