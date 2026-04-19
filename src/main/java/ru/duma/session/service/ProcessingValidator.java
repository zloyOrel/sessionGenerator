package ru.duma.session.service;

import ru.duma.session.model.ProcessingContext;
import ru.duma.session.model.ValidationResult;
import ru.duma.session.utils.ParserFolders;
import ru.duma.session.utils.ParserQuestions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * @author epifancev
 * @Date 21.10.2025
 * Класс для валидации, будем делать перед всеми процессами, если упадем останавливаем все процессы
 */


public class ProcessingValidator {

    public ValidationResult validate(ProcessingContext context) {
        try {
            //Проверка файлов и папок
            ValidationResult fileCheck = validateFilesAndDirs(context);
            if (!fileCheck.isValid()) return fileCheck;

            //Проверка повестки (контрольные слова, формат вопросов)
            ValidationResult agendaCheck = validateAgenda(context);
            if (!agendaCheck.isValid()) return agendaCheck;

            //Проверка структуры папок сессии
            ValidationResult folderCheck = validateSessionFolders(context);
            if (!folderCheck.isValid()) return folderCheck;

            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.error("Ошибка при валидации: " + e.getMessage());
        }
    }

    private ValidationResult validateFilesAndDirs(ProcessingContext context) {
        // Повестка существует?
        if (!Files.exists(Path.of(context.agenda()))) {
            return ValidationResult.error("Файл повестки не найден: " + context.agenda());
        }
        // Директория постановлений существует?
        if (!Files.exists(context.resolutionDir()) || !Files.isDirectory(context.resolutionDir())) {
            return ValidationResult.error("Директория постановлений не существует: " + context.resolutionDir());
        }
        // Директория сессии существует?
        if (!Files.exists(context.sessionDir()) || !Files.isDirectory(context.sessionDir())) {
            return ValidationResult.error("Директория сессии не существует: " + context.sessionDir());
        }
        return ValidationResult.success();
    }

    private ValidationResult validateAgenda(ProcessingContext context) {
        try {
            ParserQuestions parser = new ParserQuestions();
            // Проверим, что блок вопросов существует, парсер вернёт мапу если она пуская , метод парсера выкинет эксепшн
            parser.getMapQuestionsAndOwners(
                    context.agenda(),
                    context.controlWord1(),
                    context.controlWord2()
            );
            return ValidationResult.success();
        } catch (IOException e) {
            return ValidationResult.error("Ошибка в файле повестки: " + e.getMessage());
        }
    }

    private ValidationResult validateSessionFolders(ProcessingContext context) {
        try {
            // Используем ParserFolders для валидации структуры
            ParserFolders parser = new ParserFolders(context.sessionDir().toString());
            parser.getFilesQuestionMap(); // выбросит IOException при недопустимых папках
            return ValidationResult.success();
        } catch (IOException e) {
            return ValidationResult.error("Ошибка в структуре папок сессии: " + e.getMessage());
        }
    }
}