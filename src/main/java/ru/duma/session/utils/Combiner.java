package ru.duma.session.utils;

import org.jetbrains.annotations.NotNull;
import ru.duma.session.model.Question;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author epifancev
 * @Date 01.10.2024
 * Класс на вход получает два объекта с вопросами и папками к этим вопросам , объединяет их сравниваю по ключу
 *  за ключ пока взял номер вопроса и номер папки (1.)
 *  На выходе получаем лист объектов Questions
 */


public class Combiner {

    private final ParserQuestions parserQuestions;
    private final ParserFolders parserFolders;
    private final String docFile;
    private final String rootDir;
    private final String startWord;
    private final String stopWord;

    //  Будем использовать в рабочей версии
    public Combiner(String docFile, String rootDir, String startWord, String stopWord) {
        this(docFile, rootDir, startWord, stopWord, new ParserQuestions(), new ParserFolders(rootDir));
    }
//    public Combiner(ProcessingContext context) {
//        this.docFile = context.agenda();
//        this.rootDir = context.documentsDir();
//        this.startWord = context.controlWord1();
//        this.stopWord = context.controlWord2();
//        new ParserQuestions();
//        new ParserFolders(context.documentsDir());
//    }

    // Тестовый конструктор, потом может для тестов понадобится...
    public Combiner(
            String docFile,
            String rootDir,
            String startWord,
            String stopWord,
            ParserQuestions parserQuestions,
            ParserFolders parserFolders) {
        this.docFile = Objects.requireNonNull(docFile, "docFile не может быть null");
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir не может быть null");
        this.startWord = Objects.requireNonNull(startWord, "startWord не может быть null");
        this.stopWord = Objects.requireNonNull(stopWord, "stopWord не может быть null");
        this.parserQuestions = Objects.requireNonNull(parserQuestions, "parserQuestions не может быть null");
        this.parserFolders = Objects.requireNonNull(parserFolders, "parserFolders не может быть null");
    }

    private Map<String, String> agendaData;
    private Map<String, String> getAgendaData() throws IOException {
        if (agendaData == null) {
            agendaData = parserQuestions.getDataAgenda(docFile);
        }
        return agendaData;
    }

    public String getSessionNumber() throws IOException {
        return getAgendaData().get("sessionNumber");
    }

    public String getConvocationNumber() throws IOException {
        return getAgendaData().get("convocationNumber");
    }

    public String getHeaderTitle() throws IOException {
        return parserQuestions.generateHeader(docFile);
    }

    public List<Question> getQuestions() throws IOException {
        // 1. Получаем вопросы из повестки
        Map<String, List<String>> questionsMap = parserQuestions.getMapQuestionsAndOwners(
                docFile, startWord, stopWord
        );
//        questionsMap.forEach((k,v ) -> System.out.println(k + ":" + v));
//        System.out.println("-".repeat(150));

        // 2. Получаем файлы из папок (с валидацией!)
        Map<Path, List<Path>> filesMap = parserFolders.getFilesQuestionMap();

//        filesMap.forEach((k,v) -> System.out.println(k + ":" + v))
//        System.out.println("-".repeat(150));

        // 3. Строим мапу: номер вопроса -> список файлов
        Map<String, List<Path>> questionFiles = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Path>> entry : filesMap.entrySet()) {
            Path dir = entry.getKey();
            String dirName = dir.getFileName().toString();
            String questionNumber = extractLeadingDigits(dirName);
            questionFiles.put(questionNumber, entry.getValue());
        }

//        questionFiles.forEach((k,v) -> System.out.println(k + " : " + v));
//        System.out.println("-".repeat(150));
        // 4. Сопоставляем
        List<Question> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : questionsMap.entrySet()) {
            String fullQuestionText = entry.getKey();
            List<String> speakers = entry.getValue();
            // Извлекаем и валидируем номер вопроса из текста
            String questionNumber = checkFullQuestionText(fullQuestionText);
            //Найдем файлы в мапе файлов по номеру
            //(т.к ключи в мапе вопросов и мапе файлов к вопросам совпадают) это обязательное условие
            List<Path> documents = questionFiles.getOrDefault(questionNumber, Collections.emptyList())
                    .stream()
                    .map(this::buildRelativePath)
                    .collect(Collectors.toList());
            result.add(new Question(fullQuestionText, speakers, documents));
        }
        return result;
    }

    private @NotNull String checkFullQuestionText(@NotNull String fullQuestionText) throws IOException {
        int dotIndex = fullQuestionText.indexOf('.');
        if (dotIndex <= 0) {
            throw new IOException("Номер вопроса отсутствует: \"" + fullQuestionText + "\"");
        }
        String questionNumber = fullQuestionText.substring(0, dotIndex).strip();
        if (questionNumber.isEmpty() || !questionNumber.matches("\\d+")) {
            throw new IOException("Номер вопроса должен быть целым числом: \"" + fullQuestionText + "\"");
        }
        return questionNumber;
    }

    private @NotNull String extractLeadingDigits(@NotNull String text) {
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

    public Path buildRelativePath(@NotNull Path absolutePath) {
        Path parentDir = absolutePath.getParent();
        if (parentDir == null) {
            return absolutePath.getFileName();
        }
        String dirName = parentDir.getFileName().toString();
        String fileName = absolutePath.getFileName().toString();
        return Path.of("./" + dirName + "/" + fileName);
    }
}