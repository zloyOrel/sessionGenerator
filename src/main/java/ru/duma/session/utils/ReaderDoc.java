package ru.duma.session.utils;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;



/**
 * @author epifancev
 * @Date 01.10.2024
 */
public class ReaderDoc {
    //После долгого геморроя, решил удалять при парсинге (doc docx) файлов всё управляющие символы (\u0001 и тд)
    private static final String CONTROL_CHAR = "\\p{C}";

    public List<String> read(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Путь к файлу не может быть null или пустым");
        }

        Path file = Path.of(filePath);
        if (!Files.exists(file)) {
            throw new IOException("Файл не найден: " + file);
        }

        String fileName = file.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".doc")) {
            return readDoc(file);
        } else if (fileName.endsWith(".docx")) {
            return readDocx(file);
        } else {
            throw new IOException("Неподдерживаемый формат файла: " + fileName + ". Поддерживаются только .doc и .docx");
        }
    }

    private List<String> readDoc(Path file) throws IOException {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(file));
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            List<String> result = new ArrayList<>(paragraphs.length);
            for (String para : paragraphs) {
                para = para.replaceAll(CONTROL_CHAR, "");
                if (!para.isBlank()) {
                    result.add(para.strip());
                }
            }
            return result;
        }
    }

    private List<String> readDocx(Path file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            List<String> result = new ArrayList<>(paragraphs.size());
            for (XWPFParagraph para : paragraphs) {
                String text = para.getText();
                text = text.replaceAll(CONTROL_CHAR,"");
                if (!text.isBlank()) {
                    result.add(text.trim());
                }
            }
            return result;
        }
    }
}
