package ru.duma.session.utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;


/**
 * @author epifancev
 * @Date 01.10.2024
 * Класс парсит  папку в документами в возвращает мапу вида [ папке -> список файлов ]
 */

public class ParserFolders {

    private final String rootDir;

    public ParserFolders(String rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir не может быть null");
    }

    public Map<Path, List<Path>> getFilesQuestionMap() throws IOException {
        Path root = Path.of(rootDir);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IOException("Корневая директория не существует: " + rootDir);
        }

        //ВАЛИДАЦИЯ: все дочерние папки должны начинаться с цифры
        List<Path> allDirs = Files.list(root)
                .filter(Files::isDirectory)
                .toList();

        for (Path dir : allDirs) {
            String name = dir.getFileName().toString();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) {
                throw new IOException(
                        "Недопустимая папка в директории сессии: \"" + name + "\". " +
                                "Все папки должны называться как \"1\", \"2\", \"123.Какой-то проект хз\" и т.д. " +
                                "Удалите или переименуйте папку \"" + name + "\"."
                );
            }
        }

        // Собираем PDF-файлы из всех валидных папок
        Map<Path, List<Path>> result = new LinkedHashMap<>();
        for (Path dir : allDirs) {
            List<Path> files = collectPdfFiles(dir);
            if (!files.isEmpty()) {
                result.put(dir, files);
            }
        }

        return result;
    }

    private List<Path> collectPdfFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
}