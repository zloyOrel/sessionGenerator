package ru.duma.session.utils;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author epifancev
 * @Date 01.10.2024
 * Класс парсит файл с вопросами и возвращает мапу вида [ вопрос -> список докладчиков ]
 */

public class ParserQuestions {

    private static final ReaderDoc reader = new ReaderDoc();

    public Map<String, List<String>> getMapQuestionsAndOwners(String docFile, String startWord, String stopWord) throws IOException {
        List<String> allLines = reader.read(docFile);
        List<String> section = extractSection(allLines, startWord, stopWord);

        if (section.isEmpty()) {
            throw new IOException(
                    "Не найден блок вопросов между контрольными словами: \"" +
                            startWord + "\" и \"" + stopWord + "\". " +
                            "Проверьте правильность контрольных слов в настройках."
            );
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        String currentQuestion = null;
        List<String> currentSpeakers = new ArrayList<>();

        for (String line : section) {
            line = line.strip();
            if (line.isEmpty()) continue;

            if (isQuestionLine(line)) {
                if (currentQuestion != null) {
                    result.put(currentQuestion, new ArrayList<>(currentSpeakers));
                    currentSpeakers.clear();
                }
                currentQuestion = normalizeQuestion(line);
            } else if (currentQuestion != null) {
                if (isSpeakerLine(line)) {
                    currentSpeakers.add(line);
                }
            }
        }
        //добавим самый последний вопрос
        if (currentQuestion != null) {
            result.put(currentQuestion, new ArrayList<>(currentSpeakers));
        }

        if (result.isEmpty()) {
            throw new IOException(
                    "В блоке повестки не найдено ни одного вопроса в формате \"1. Текст вопроса\". " +
                            "Убедитесь, что вопросы начинаются с номера, точки и текста."
            );
        }

        return result;
    }

    private boolean isQuestionLine(String line) {
        return line.matches("^\\d+\\.\\s+.+");
    }

    private boolean isSpeakerLine(String line) {
        return line.matches("^[А-ЯЁ].*");
    }

    //Приводим текст вопроса к нормальному виду (1. Текса вопроса)
    private String normalizeQuestion(String line) {
        return line.replaceFirst("^(\\d+)\\.", "$1. ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    //Вернём строки между контрольными словами (там будут наши вопросы и спикеры)
    public List<String> extractSection(List<String> allLines, String startWord, String stopWord) throws IOException {
        int startIdx = 0;
        int stopIdx = 0;
        //Найдём позиции с которых будем добавлять вопросы и спикеров , для дальнейшей обработки
        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i).strip();
            if (startIdx == 0 && line.contains(startWord)) {
                startIdx = i;
            } else if (startIdx != 0 && line.contains(stopWord)) {
                stopIdx = i;
                break;
            }
        }
        //Если слова контрольные не нашли, выбросим экспшен, и остановим программу
        if (startIdx == 0) {
            throw new IOException("Не найдено начальное контрольное слово: \"" + startWord + "\"");
        }
        if (stopIdx == 0) {
            throw new IOException("Не найдено конечное контрольное слово: \"" + stopWord + "\"");
        }

        List<String> section = new ArrayList<>();
        for (int i = startIdx + 1; i < stopIdx; i++) {
            String line = allLines.get(i).strip();
            if (!line.isEmpty()) {
                section.add(line);
            }
        }
        return section;
    }


    //Нужно будет при экспорте
    public String generateHeader(String docFile) throws IOException {
        Map<String, String> data = this.getDataAgenda(docFile);
        String session = data.get("sessionNumber");
        String convocation = data.get("convocationNumber");
        if (session == null || convocation == null) {
            throw new IOException("Не удалось извлечь номер сессии или созыва из повестки");
        }
        return session + " сессия " + convocation + " созыва";
    }


    public Map<String, String> getDataAgenda(String docFile) throws IOException {
        List<String> lines = reader.read(docFile);
        // Шаблон для римских цифр: "XXXVII сессия VII созыва"
        Pattern pattern = Pattern.compile(
                "([IVXLCDM]+)\\s+сессия\\s+([IVXLCDM]+)\\s+созыва",
                Pattern.CASE_INSENSITIVE
        );

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Map<String, String> result = new HashMap<>();
                result.put("sessionNumber", matcher.group(1));      // например, "XXXVII"
                result.put("convocationNumber", matcher.group(2));  // например, "VII"
                return result;
            }
        }
        throw new IOException(
                "Не найдена строка с номером сессии и созыва в формате \"XXXVII сессия VII созыва\". " +
                        "Поддерживаются только римские цифры (I, V, X, L, C, D, M)."
        );
    }
}