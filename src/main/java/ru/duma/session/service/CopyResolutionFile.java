package ru.duma.session.service;




import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;


/**
 * @author epifancev
 * @Date 01.10.2025
 * Класс копирует файлы постановлений в соответствующие папки с документами
 * Проект закона должен содержать строки пз или проект тогда мы его переименуем по человечески проект_закона
 */


public class CopyResolutionFile {

    private static final Pattern TEMP_FILE_PATTERN = Pattern.compile("^~\\$.*");
    private static final Pattern PROJECT_FILE_PATTERN = Pattern.compile(".*(пз|проект|пп).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUDE_PROJECT_PATTERN = Pattern.compile(".*(актуализированная|редакция|проект_закона).*", Pattern.CASE_INSENSITIVE);

    private final WebProcessingListener listener;

    public CopyResolutionFile(WebProcessingListener listener) {
        this.listener = listener;
    }

    public void copyFilesToDirs(Path resolutionsDir, Path sessionDir, String filePatternRegex) throws IOException {
        listener.onLog("Сканирование файлов постановлений в: " + resolutionsDir);
        // 1. Собираем файлы постановлений
        List<Path> resolutionFiles = findResolutionFiles(resolutionsDir, filePatternRegex);
        if (resolutionFiles.isEmpty()) {
            listener.onLog("Не найдено файлов постановлений по шаблону: " + filePatternRegex);
            listener.onLog("Будем сразу формировать index.html");
            return;
        }

        // 2. Собираем целевые папки
        List<Path> documentDirs = findDocumentDirectories(sessionDir);
        Map<String, Path> dirByNumber = buildDirMap(documentDirs);

        // 3. Копируем каждый файл
        for (Path sourceFile : resolutionFiles) {
            String fileNumber = extractLeadingDigits(sourceFile.getFileName().toString());
            if (fileNumber.isEmpty()) {
                throw new IllegalArgumentException("Имя файла не содержит цифр " + sourceFile.getFileName());
            }

            Path targetDir = dirByNumber.get(fileNumber);
            if (targetDir == null) {
                // Создаём новую папку, если не найдена
                targetDir = sessionDir.resolve(fileNumber);
                Files.createDirectories(targetDir);
                dirByNumber.put(fileNumber, targetDir);
                listener.onLog("Создана новая папка для №" + fileNumber + ": " + targetDir);
            }

            String extension = getFileExtension(sourceFile);
            Path targetFile = targetDir.resolve("Постановление." + extension);

            // Копируем и удаляем оригинал
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(sourceFile);
            listener.onLog("✅ Скопировано: " + sourceFile.getFileName() + " → " + targetFile.getFileName());
        }

        // 4. Нормализуем имена файлов и папок
        normalizeFileNames(sessionDir);
        normalizeDirectoryNames(sessionDir);
        renameProjectFiles(sessionDir);
    }

    // --- Вспомогательные методы ---

    private List<Path> findResolutionFiles(Path dir, String patternRegex) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();

                // Пропускаем временные файлы Word
                if (TEMP_FILE_PATTERN.matcher(name).matches()) {
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

    private List<Path> findDocumentDirectories(Path sessionDir) throws IOException {
        List<Path> dirs = new ArrayList<>();
        Files.walkFileTree(sessionDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(sessionDir)) {
                    dirs.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return dirs;
    }

    private Map<String, Path> buildDirMap(List<Path> dirs) {
        Map<String, Path> map = new HashMap<>();
        for (Path dir : dirs) {
            String number = extractLeadingDigits(dir.getFileName().toString());
            if (number != null && !number.isEmpty()) {
                map.put(number, dir);
            }
        }
        return map;
    }

    private String extractLeadingDigits(String text) {
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        return digits.toString();
    }

    private String getFileExtension(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex == -1) ? "" : name.substring(dotIndex + 1).toLowerCase();
    }

    // --- Нормализация имён ---

    private void normalizeFileNames(Path rootDir) throws IOException {
        Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .forEach(this::normalizeFileName);
    }

    private void normalizeFileName(Path file) {
        try {
            String name = file.getFileName().toString();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex == -1) {
                listener.onLog("Файл без расширения: " + file);
                return;
            }

            String baseName = name.substring(0, dotIndex);
            String ext = name.substring(dotIndex + 1).toLowerCase();
            Path target = file.getParent().resolve(baseName + "." + ext);

            if (!file.equals(target)) {
                Files.move(file, target);
                listener.onLog("Переименован файл: " + name + " -> " + target.getFileName());
            }
        } catch (IOException e) {
            listener.onLog("Ошибка при нормализации файла " + file + ": " + e.getMessage());
        }
    }

    private void normalizeDirectoryNames(Path rootDir) throws IOException {
        List<Path> dirs = Files.walk(rootDir)
                .filter(Files::isDirectory)
                .filter(dir -> !dir.equals(rootDir))
                .sorted(Comparator.comparing(Path::toString).reversed()) // сначала вложенные
                .toList();

        for (Path dir : dirs) {
            String dirName = dir.getFileName().toString();
            String newDirName = dirName.split("\\.")[0]; // убираем всё после точки
            if (!newDirName.equals(dirName)) {
                Path target = dir.getParent().resolve(newDirName);
                Files.move(dir, target);
                listener.onLog("Переименована папка: " + dirName + " → " + newDirName);
            }
        }
    }

    private void renameProjectFiles(Path rootDir) throws IOException {
        Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .forEach(this::maybeRenameProjectFile);
    }

    private void maybeRenameProjectFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (EXCLUDE_PROJECT_PATTERN.matcher(name).matches()) {
            return;
        }
        if (PROJECT_FILE_PATTERN.matcher(name).matches()) {
            String ext = getFileExtension(file);
            Path target = file.getParent().resolve("Проект_закона." + ext);
            try {
                if (!Files.exists(target)) {
                    Files.move(file, target);
                    listener.onLog("Переименован ПЗ-файл: " + file.getFileName() + " → Проект_закона." + ext);
                } else {
                    listener.onLog("Пропущен дубликат ПЗ-файла в: " + file.getParent());
                    throw new FileAlreadyExistsException("Обнаружено несколько файлов проекта закона в папке: " + file.getParent());
                }
            } catch (IOException e) {
                listener.onLog("Ошибка переименования ПЗ-файла: " + e.getMessage());
            }
        }
    }
}