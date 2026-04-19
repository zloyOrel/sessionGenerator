package ru.duma.session.service;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ru.duma.session.model.Question;
import ru.duma.session.utils.Combiner;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формирует HTML-файл сессии.
 * Перенесено из JavaFX-версии без изменений в логике.
 */
public class HtmlExporter {

    private final WebProcessingListener listener;
    private final Combiner combiner;

    private static final int LENGTH_LINE = 150;
    private static final String CHAR_LINE = "*";
    private static final String EXTENSION_FOR_REMOVE = ".pdf";

    public HtmlExporter(WebProcessingListener listener, Combiner combiner) {
        this.combiner = combiner;
        this.listener = listener;
    }

    public void export(Path outFile) throws IOException {
        listener.onLog("Начинаем экспорт в HTML: " + outFile);
        try {
            List<Question> questions = combiner.getQuestions();
            fuckingSort(questions);

            StringBuilder res = new StringBuilder();
            res.append(getHtmlBegin());
            res.append(insertLine(CHAR_LINE, LENGTH_LINE));

            for (Question q : questions) {
                String questionLink = switch (q.getNumberQuestion()) {
                    case 1 -> "#";
                    case 2 -> String.valueOf(q.getResolution());
                    default -> String.valueOf(q.getDocumentOrderDay());
                };

                if (q.getNumberQuestion() == 1) {
                    q.documents().clear();
                } else if (q.documents().size() == 1 &&
                        "Постановление.pdf".equals(q.documents().get(0).getFileName().toString())) {
                    q.documents().clear();
                }
                q.documents().remove(q.getDocumentOrderDay());

                res.append("<article class=\"quest\">\n");
                res.append(String.format("  <h4><a href=\"%s\">%s</a></h4>\n",
                        escapeHtml(questionLink), escapeHtml(q.question())));

                if (!q.documents().isEmpty()) {
                    res.append("<ul>\n");
                    for (Path file : q.documents()) {
                        String fileName = StringUtils.capitalize(
                                removeExtension(file.getFileName().toString(), EXTENSION_FOR_REMOVE));
                        res.append(String.format("    <li><a href=\"%s\">%s</a></li>\n",
                                file.toString(), escapeHtml(fileName)));
                    }
                    res.append("  </ul>\n");
                }

                if (!q.speakers().isEmpty()) {
                    res.append("  <dl>\n");
                    for (String speaker : q.speakers()) {
                        res.append(String.format("    <dt>%s</dt>\n", escapeHtml(speaker)));
                    }
                    res.append("  </dl>\n");
                }

                res.append("</article>\n");
                res.append("<hr class=\"divider\">\n");
            }

            res.append(getHtmlEnd());

            try (PrintStream writer = new PrintStream(Files.newOutputStream(outFile))) {
                writer.println(res);
            }

            listener.onLog("Экспорт в HTML завершён: " + outFile);

        } catch (Exception e) {
            listener.onLog("Критическая ошибка при экспорте HTML: " + e.getMessage());
            throw new IOException("Ошибка генерации HTML-файла", e);
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private static void fuckingSort(final List<Question> questions) {
        Map<String, Integer> words = new HashMap<>();
        words.put("актуализированная", 1);
        words.put("актуал", 1);
        words.put("заключение", 2);
        words.put("закл", 2);
        words.put("приложение", 3);
        words.put("пояснительная", 4);
        words.put("итоги", 4);
        words.put("письмо", 4);
        words.put("постановление", 5);

        Pattern patternForNumbers = Pattern.compile("[+-]?((\\d+\\.?\\d*)|(\\.\\d+))");

        for (Question question : questions) {
            question.documents().sort((o1, o2) -> {
                try {
                    String fileName1 = getFileNameWithoutExt(o1);
                    String fileName2 = getFileNameWithoutExt(o2);

                    if (fileName1.contains("приложение") && fileName2.contains("приложение")) {
                        return compareByNumber(fileName1, fileName2, patternForNumbers);
                    }
                    if (fileName1.contains("актуализированная") && fileName2.contains("актуализированная")) {
                        return compareByNumber(fileName1, fileName2, patternForNumbers);
                    }

                    int index1 = getPriority(fileName1, words);
                    int index2 = getPriority(fileName2, words);
                    return index1 - index2;
                } catch (Exception e) {
                    return 0;
                }
            });
        }
    }

    private static String getFileNameWithoutExt(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(0, lastDot).trim().toLowerCase() : name.trim().toLowerCase();
    }

    private static int getPriority(String fileName, Map<String, Integer> words) {
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            if (fileName.contains(entry.getKey())) return entry.getValue();
        }
        return 0;
    }

    private static int compareByNumber(String f1, String f2, Pattern pattern) {
        return Double.compare(extractNumber(f1, pattern), extractNumber(f2, pattern));
    }

    private static double extractNumber(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group()) : 0;
    }

    private String removeExtension(final String fileName, final String pattern) {
        return fileName.replace(pattern, "");
    }

    private String insertLine(String ch, int length) {
        return "<!-- %s -->\n".formatted(ch.repeat(length));
    }

    private String getHtmlBegin() throws IOException {
        String[] headerParts = this.combiner.getHeaderTitle().trim().split("\\s+");
        String sessionNum = headerParts.length > 0 ? headerParts[0] : "";
        String convocationNum = headerParts.length > 2 ? headerParts[2] : "";
        String fullTitle = sessionNum + " сессия " + convocationNum + " созыва";
        return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>%s</title>
            %s
        </head>
        <body>
            <div class="container">
                <header class="main-header">
                    %s
                    <div class="header-content">
                        <h3>О повестке дня %s сессии<br>
                        Думы Чукотского автономного округа</h3>
                        <p>Дума Чукотского автономного округа постановляет:<br>
                        внести в повестку дня %s сессии Думы Чукотского автономного округа %s созыва следующие вопросы:</p>
                    </div>
                </header>
                <!-- Вопросы повестки -->
        """.formatted(fullTitle, getStyles(), getLogo(), sessionNum, sessionNum,
                this.combiner.getConvocationNumber());
    }

    private static String getStyles() {
        return """
        <style>
            :root {
                --bg-color: #f5f7fa;
                --card-bg: #ffffff;
                --text-primary: #2c3e50;
                --text-secondary: #5a6c7d;
                --accent-color: #3498db;
                --accent-hover: #2980b9;
                --border-radius: 8px;
                --shadow: 0 2px 12px rgba(0,0,0,0.08);
                --transition: all 0.2s ease-in-out;
            }
            
            * { box-sizing: border-box; margin: 0; padding: 0; }
            
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: linear-gradient(135deg, var(--bg-color) 0%, #e8ecf1 100%);
                color: var(--text-primary);
                line-height: 1.6;
                padding: 20px;
            }
            
            .container { max-width: 1200px; margin: 0 auto; }
            
            /* === Объединённый хедер: логотип + заголовок === */
            .main-header {
                background: var(--card-bg);
                border-radius: var(--border-radius);
                box-shadow: var(--shadow);
                margin-bottom: 24px;
                overflow: hidden;
            }
//            .main-header {
//                position: sticky;
//                top: 0;
//                z-index: 1000;
//                background: var(--card-bg);
//                border-radius: var(--border-radius);
//                box-shadow: var(--shadow);
//                margin-bottom: 24px;
//                overflow: hidden;
//                backdrop-filter: blur(8px);
//                -webkit-backdrop-filter: blur(8px);
//                background: rgba(255, 255, 255, 0.95);
//            }
            
            .main-header .logo {
                text-align: center;
                padding: 16px 20px 8px 20px;
//                border-bottom: 1px solid #eee;
                margin: 0;
//                background: rgba(248, 249, 250, 0.5);
            }
            
            .main-header .logo img {
                max-width: 100%;
                height: auto;
                max-height: 100px;
                object-fit: contain;
            }
            
            .main-header .header-content {
                text-align: center;
                padding: 20px 24px 24px 24px;
            }
            
            .main-header h3 {
                color: var(--text-primary);
                margin: 0 0 10px 0;
                font-size: 1.3rem;
                line-height: 1.4;
            }
            
            .main-header p {
                color: var(--text-secondary);
                font-size: 1.05rem;
                margin: 0;
                line-height: 1.5;
            }
            
            /* === Карточка вопроса === */
            .quest {
                background: var(--card-bg);
                border-radius: var(--border-radius);
                padding: 20px 24px;
                margin-bottom: 16px;
                box-shadow: var(--shadow);
                border-left: 4px solid var(--accent-color);
                transition: var(--transition);
            }
            
            .quest:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 20px rgba(0,0,0,0.12);
            }
            
            .quest h4 {
                margin: 0 0 12px 0;
                font-size: 1.15rem;
                color: var(--text-primary);
            }
            
            .quest h4 a {
                color: var(--accent-color);
                text-decoration: none;
                font-weight: 600;
                transition: var(--transition);
            }
            
            .quest h4 a:hover {
                color: var(--accent-hover);
                text-decoration: underline;
            }
            
            /* === Список документов === */
            .quest ul {
                list-style: none;
                padding-left: 0;
                margin: 12px 0;
            }
            
            .quest ul li {
                padding: 6px 0 6px 24px;
                position: relative;
                color: var(--text-secondary);
            }
            
            .quest ul li::before {
                content: "📄";
                position: absolute;
                left: 0;
                opacity: 0.7;
            }
            
            .quest ul li a {
                color: var(--text-primary);
                text-decoration: none;
                transition: var(--transition);
                border-bottom: 1px dashed transparent;
            }
            
            .quest ul li a:hover {
                color: var(--accent-color);
                border-bottom-color: var(--accent-color);
            }
            
            /* === Докладчики — вертикальный список === */
            .quest dl {
                margin: 16px 0 0 0;
                padding-top: 12px;
                border-top: 1px solid #eee;
                display: flex;
                flex-direction: column;
                gap: 4px;
            }
            
            .quest dt {
                font-weight: 600;
                color: var(--text-secondary);
                font-style: italic;
                font-size: 0.95rem;
                margin: 0;
                padding-left: 0;
            }
            
            /* === Разделитель между вопросами === */
            .divider {
                height: 1px;
                background: linear-gradient(90deg, transparent, #ddd, transparent);
                margin: 24px 0;
                border: none;
            }
            
            /* === Адаптивность для мобильных === */
            @media (max-width: 768px) {
                body { padding: 12px; }
                
                .main-header .logo {
                    padding: 12px 16px 6px 16px;
                }
                
                .main-header .logo img {
                    max-height: 80px;
                }
                
                .main-header .header-content {
                    padding: 16px 18px 20px 18px;
                }
                
                .main-header h3 {
                    font-size: 1.15rem;
                }
                
                .main-header p {
                    font-size: 0.95rem;
                }
                
                .quest { padding: 16px 18px; }
                .quest h4 { font-size: 1.05rem; }
            }
            
            /* === Стили для печати === */
            @media print {
                body { 
                    background: #fff; 
                    padding: 0;
                }
                
                .container { max-width: 100%; }
                
                .main-header {
                    box-shadow: none;
                    border: 1px solid #ddd;
                    page-break-inside: avoid;
                }
                
                .quest {
                    box-shadow: none;
                    border: 1px solid #ddd;
                    page-break-inside: avoid;
                }
                
                .quest:hover {
                    transform: none;
                    box-shadow: none;
                }
                
                .divider {
                    background: #ccc;
                }
            }
        </style>
        """;
    }

    private static String getLogo() {
        return """
                        <div class="logo">
                        <img src="data:image/jpeg;base64, iVBORw0KGgoAAAANSUhEUgAAADwAAABNCAYAAAD6ggcWAAAACXBIWXMAABYlAAAWJQFJUiTwAAAH
                        GmlUWHRYTUw6Y29tLmFkb2JlLnhtcAAAAAAAPD94cGFja2V0IGJlZ2luPSLvu78iIGlkPSJXNU0w
                        TXBDZWhpSHpyZVN6TlRjemtjOWQiPz4gPHg6eG1wbWV0YSB4bWxuczp4PSJhZG9iZTpuczptZXRh
                        LyIgeDp4bXB0az0iQWRvYmUgWE1QIENvcmUgNS42LWMxNDIgNzkuMTYwOTI0LCAyMDE3LzA3LzEz
                        LTAxOjA2OjM5ICAgICAgICAiPiA8cmRmOlJERiB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3Jn
                        LzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiPiA8cmRmOkRlc2NyaXB0aW9uIHJkZjphYm91dD0i
                        IiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtbG5zOmRjPSJodHRw
                        Oi8vcHVybC5vcmcvZGMvZWxlbWVudHMvMS4xLyIgeG1sbnM6cGhvdG9zaG9wPSJodHRwOi8vbnMu
                        YWRvYmUuY29tL3Bob3Rvc2hvcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNv
                        bS94YXAvMS4wL21tLyIgeG1sbnM6c3RFdnQ9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9z
                        VHlwZS9SZXNvdXJjZUV2ZW50IyIgeG1wOkNyZWF0b3JUb29sPSJBZG9iZSBQaG90b3Nob3AgMjEu
                        MiAoTWFjaW50b3NoKSIgeG1wOkNyZWF0ZURhdGU9IjIwMTgtMDktMDVUMTI6NDg6NTIrMDM6MDAi
                        IHhtcDpNb2RpZnlEYXRlPSIyMDI0LTA1LTIwVDE2OjQzOjA2KzEyOjAwIiB4bXA6TWV0YWRhdGFE
                        YXRlPSIyMDI0LTA1LTIwVDE2OjQzOjA2KzEyOjAwIiBkYzpmb3JtYXQ9ImltYWdlL3BuZyIgcGhv
                        dG9zaG9wOkNvbG9yTW9kZT0iMyIgcGhvdG9zaG9wOklDQ1Byb2ZpbGU9InNSR0IgSUVDNjE5NjYt
                        Mi4xIiB4bXBNTTpJbnN0YW5jZUlEPSJ4bXAuaWlkOmIxNzk1YjNiLWQ5Y2QtNjQ0OC04N2FiLWMx
                        N2IwNjI1NTNhMiIgeG1wTU06RG9jdW1lbnRJRD0iYWRvYmU6ZG9jaWQ6cGhvdG9zaG9wOjFhMzE2
                        NzZhLTkxZDAtM2E0Yy05MDM5LTc3ZWJlYmQ5MDM0NSIgeG1wTU06T3JpZ2luYWxEb2N1bWVudElE
                        PSJ4bXAuZGlkOjIwYWE3ZjYwLTYxMDItNGNhYS1hNDVkLWU4ZjYxNzc3YzNhMyI+IDx4bXBNTTpI
                        aXN0b3J5PiA8cmRmOlNlcT4gPHJkZjpsaSBzdEV2dDphY3Rpb249ImNyZWF0ZWQiIHN0RXZ0Omlu
                        c3RhbmNlSUQ9InhtcC5paWQ6MjBhYTdmNjAtNjEwMi00Y2FhLWE0NWQtZThmNjE3NzdjM2EzIiBz
                        dEV2dDp3aGVuPSIyMDE4LTA5LTA1VDEyOjQ4OjUyKzAzOjAwIiBzdEV2dDpzb2Z0d2FyZUFnZW50
                        PSJBZG9iZSBQaG90b3Nob3AgMjEuMiAoTWFjaW50b3NoKSIvPiA8cmRmOmxpIHN0RXZ0OmFjdGlv
                        bj0iY29udmVydGVkIiBzdEV2dDpwYXJhbWV0ZXJzPSJmcm9tIGltYWdlL2Vwc2YgdG8gaW1hZ2Uv
                        cG5nIi8+IDxyZGY6bGkgc3RFdnQ6YWN0aW9uPSJzYXZlZCIgc3RFdnQ6aW5zdGFuY2VJRD0ieG1w
                        LmlpZDowMTYyMTc4Yy1mMjY0LTQxMTUtYWIwNS1hOGQ1NjU4NzVmMmUiIHN0RXZ0OndoZW49IjIw
                        MjAtMTEtMjBUMTI6MDQ6NTkrMDM6MDAiIHN0RXZ0OnNvZnR3YXJlQWdlbnQ9IkFkb2JlIFBob3Rv
                        c2hvcCAyMS4yIChNYWNpbnRvc2gpIiBzdEV2dDpjaGFuZ2VkPSIvIi8+IDxyZGY6bGkgc3RFdnQ6
                        YWN0aW9uPSJzYXZlZCIgc3RFdnQ6aW5zdGFuY2VJRD0ieG1wLmlpZDpiMTc5NWIzYi1kOWNkLTY0
                        NDgtODdhYi1jMTdiMDYyNTUzYTIiIHN0RXZ0OndoZW49IjIwMjQtMDUtMjBUMTY6NDM6MDYrMTI6
                        MDAiIHN0RXZ0OnNvZnR3YXJlQWdlbnQ9IkFkb2JlIFBob3Rvc2hvcCBDQyAyMDE4IChXaW5kb3dz
                        KSIgc3RFdnQ6Y2hhbmdlZD0iLyIvPiA8L3JkZjpTZXE+IDwveG1wTU06SGlzdG9yeT4gPC9yZGY6
                        RGVzY3JpcHRpb24+IDwvcmRmOlJERj4gPC94OnhtcG1ldGE+IDw/eHBhY2tldCBlbmQ9InIiPz65
                        AnUwAAAajElEQVR4AeXBB5xdZZ3w8d//Oc85t869d3rNJJNGGolJAAnSBRFYQIVFBeVlfVFYxIK6
                        4i6roiuK7ZW2goXVXRYRDSwKK016MCSEHghkksxkJtPr7e2c51nGrL6ioSni5xO/X33iwrc9GCq6
                        XYEb5LAi7JWsVb4Tr0aqPTpU8ObHp6ItlXAVZYS9kRGLV/YoJAuODkLVdDXkt/iej1hhb2TFIiL4
                        np/WxlpRRhAriBX2VmIFsSIaxV8VjfBXRWP5q6J5owkIgsXya5Y3lAYBhD8LAUEQEay12MBircUY
                        w2+IIyhHISJYa5lhreXPRVtrAcvrRvi1kApRKBTIZTJUjY9CYTEICpRBORpB8Ks+YLFAOBwh5HlE
                        Y3GqVMFYXm9aWcEKrwvlOFjfUC1U2JnrIUSYjuWzmLt8AZF4hOYFLTiupqamhqr1sWIojGYZ6N5F
                        frLA0w8/yeR4hvHMLlpirUgsjnIsxhheL1oQ/hTWWrSrUVVhaHAInyq1rfUccvqRHHzEoaw+/EDS
                        QRplBGMDEvEkiZoU/QO9KBSupylWSiQSScxYwLbnn+Hu+x9h43XrYXgHmhqam5ox2uD7PiLCn0Lz
                        JzAYol6MqdFJJipjtC5u5fiz3snC/Rax/M0rUZ7DYN8AIz2DhMJhiqUS6WiWuroS40NjIAoRRaVS
                        Jjw7Suu8TrSM4R72CRb/7SDeA7/kpz+4gx1btlPr1dHQ3EChXECJ4o+lxQgWy2tiAQURibBl12ZS
                        LbWccObJnPWP5xKNRqkan/7+XYTFIWSqpFyDMiXiXoAnJWqlRNUNsDbAUYpyUKHOE4a2PsdQ/zCb
                        xlr50EGLKSxMsubdJ3LDldfy0A3380zf0yxtW07JFrGBRUR4rTRWQHhNlFZoX7NldDP7v/1ATvrY
                        KRx+9NuYnJ5kZ98gbuDTFnWo+D4Z8ZjQSYq+paJCFH1NdVRTth3M8IKAGtcnPTLNHH+MQjhFS8Jl
                        uq+HgaFRlixawKe+ciEr1qzi37/8fbZueo75jQvwtU9gDMJro8ECwqthsbjaxS/6PDX5OKee/T7O
                        u/RTOFqxvacXLUJ7yFJGsyktPJH2GPcdfDUbwwt8wBis8XEcBytgxMGKglyFzlgbucoYnevXsrOt
                        mTcddChO2KF3Zw+HHnM4R73zWC445SPcduMtrKhfjRt2qVbLiCheieUFVtC8Bq72KGRzjGVGOfVT
                        7+czX/8c2WKW7d391McjNLo+j2UUt4+5DJUVUQ1Ra4gHJdCaIBbGui5GHBCLCgJ0qYQuFQiAgXIU
                        HW1hS8fh7Ox/gsIjj3L04WuY1VLLwPQEelrxxeu/yrxvLuT6f76WVCRFbbKWsl9GEF6O8AKxaF4N
                        C0o7lLJFRjMjHPLhIzn6Qyewa2gXxofOxjqoFvnPAZd105pa1zLbqVLVIQp1TRTDAfHxcVK92whl
                        pnFMBWsVVS9CvqmJ6dktVGL1hNN5ItPTqIYOsqlmbp6a4PHHi5wzNyBpfXrGsygXzvnMR0nn09zy
                        pZsIqRBe3MMEhlcmaF4NBdY3jGVGWXP6IVx85TfY9MjD3LHuTvZfuRLjZ/nJdC3dbhtd4QKhIGCs
                        qQXjOcx+5G5annoYr1jGdxQGFwmHkXIZXczgmgDxA7ItrfQfcDCDyw5C54rUTmSJN9SzreRyyR2/
                        4sxZPguXrqJ3LEsxv40L/uWzjDw3yBNrH6UtPAuEV8GixQqvxNMeo7tGaF7exhd+eAn5/DTzu+YT
                        8TwGnt7AT0P7MNbawRxy5CuGvniKuc/9igPuuoHpeCNDS1cxvs9KCrVNWOUBFisKIUCX8qT6nqP1
                        qadYeutPWHzXWroPO56e/Y8kMTTNPirDtpZl/Ft6lA8XKrREYLjoMzi2i4v+4xLO2fZ/GH9ijNaO
                        VsrVMq9E8wqUUkyPT5NVWT5/5VcIqhVGhydI1URYkHJYq2eTbl5Am1NgPOcTbW1izcBm9vvRv3LT
                        +84ht+qtOBWDzuaIjWdQQRWsQRCM0vhhj6m5KxhZchBOJU3r5kdZ9MBaZj3xAOvffwF+3mWByjBQ
                        M59Lt2X5RFeFhjBMTOaJJ2r46BWf4pOHnEd0IkK0NoYJDC/JguYVWAMTpQlOPO8dLFm+hOnJNAkP
                        lPG5bJsis+hIqg/8iONXzYHVh9Jeo2hpbedn4Q8z3HQYassgMamAVlitsI4CFFVrCUwA+QJePk9Y
                        xnG0x/jSQ7jrgCNYceNVHPfls/nlJ77KeE0jreOD9Koo1wzG+WRnlrKB4f5Rlq5czukXvI/rv3od
                        sxNdvBTLDEHzcgT8TIVYc5zjPv5+tKPwc3liyTC3DRueVy3Mq+aY3Pcwfr7pZr6wbCG47Yx7SZ65
                        8xecfXIdfUuX8EzfGCO5KoWyD9YCglIQ1gpHoGIslcAQUCKe7iUZCrPpfedSbEzyzs+ezdpLrmWy
                        oZlZ6XF6yh5rRyOc2lyiZ6pANp/jiI+cyi0/+DmVTBmdcsGwZwIaAWHPPBXiucI2Tv/43zFn3hwG
                        duygKRmjtyTcO6nocItQ0Uiyliap4fGSZiWgyiWmq4rG+gQr2j2Oa2ujP1NhougzXfJxlFAX0cQ9
                        B0egHFgKVcN4ocLOTJX+dAlZv4179z+dooGjr/gUt59/OdVMiFa3ysZMiDfFK8xKhJkqWzpmdfCu
                        v38313zh2+xTu5QqFV6K5qUI5DIZUrUp9jtyf2IIEhjGJ6a4I5PA02HCQZWheW0c9sOvMDUS48LB
                        Zj40McqJncLXvvY1ZgRBgKOEOckQc5IhXl6UGdlKwKMjObb07+K+I08n/vgW3nL95dz9oQvpeLaX
                        iChuGY9wzsIQzY4QR3jL8Yfx0/93HZl0mmhNDGsNv0vYTWOFPRGEqdwkq487gFVHrGFHbw+tzY30
                        bn2ewVyUSFSRaWijZfMT1PY8wfbPfZi39G3m30YWceuDz3L+oR5LlySZERgDWGYIu1n+kABKCTWe
                        4vBZSQ6fleTtGcM1H/8M3rmn07HfQ6QXvJnk0CA9OWGgJKxojTAwMEztgkb2+5s1PHr9w0TjEfbM
                        osWCFcvvstbiikuBAosOXoqnNIhirHcbOwqWSixJKiiQidQz77//m+63vp33z/ooDd52Hg/O4Eel
                        hdx29wN0tX6RaO1SgsAAlhmWl2aBwFjAMkNE6EoIFyTi7Dj5ZOJXfoP7L7+BGq2JRDTbhyaIjk/y
                        TO8EDXMaWXz0CjZc/yDKOvj4iAi/T/MC4cUcxyE3naFzVhdNq2bx6OOPEfEi1Koq24I6skETfgjq
                        tu+gLE3seNM7SA2fi5YS++tvs//x0LcVBnc8wtxlV+CETiIIDGB5Lay1BIGlRoQVZ55BpCbJuu4e
                        qqkkCU/ozwe0FEeJxGI0JlIkli+job2J3FSOWDKGMYbfp9kTgVK5zLy5s+nap4v+rbuopCcg1kF/
                        fB/epO9FzwlY+JMb2Bqfz7Laqwk7JUoSI/A14UKA1ppYtJ/y5Dtx657CCS3DBAaw7JmAUmAMYPld
                        gbU4wIKTT6J9XR8jhTKxiMeURKnp6KI1rGlqbiY2L0l9VxM7BrqpScUx/D5BI2B5MUFRMWUiiTCL
                        5syns6mV6bE8W/tynFJ/Bmuar4V6yGbBNkeZM1lgkDiiHQRDThS5giUaS1Iqpdn5zKUseNP3UUph
                        TMBuCkTAGkAAiw0GEWpBwoDhRYIAcRzqFnWy87Eeam3AmNVMVKEtqenevhMvFSLRlsRiseyZxvIH
                        rDE4aOItKbZs68aWKgS2iYTbx/6N11JyFHoqxs4pS+xAS3MqynTawVQs1gp+AJmMUBMTqmUol4b4
                        NeF/CZYCSkIopbGACaawlcdQ4WOwVvgDjgIDdRs3E0STaEdwwlFGsyM8O9LLFCHavWaaZ7UiQBAE
                        7IlG+APWWiyWZFOSqqky1D+KowYoV1bzfPRC5i+9GH+nUAog3KCJRw3aNVgDIuB6IBgUmtoUJFLP
                        Y00GKwnAQQRELMXxzyO4hBInY00RVCMoFwLD73Ich0kfdu5cy6qrLuAXZ9/HtlQ7sVwOX1ki4RAV
                        CeNqTV1nI4hgA4NoBZZfs+ymxfIiIoLxDY7jEKmJIgixaAifJBE9RENsPcYCFYvRFkdbfN9SqQCW
                        XzMBmABEwDeAzaLEBwFxwAQBWsURiZMZ/CfczLcIKiESbZfhht9MwG4CKMehJ1Pm5t4Q8xLrWHDg
                        Ds5PnMbddRfQnV1DxcZQjCMiBH6AoxQ67GKMRVvBYpkhzLBorACW37DWIo4iCHwquTKO4xAEATGq
                        PKuTRFyfVRYMgg3A0fyWCLspQDmIKhEUQcePQDl1GFOimr8VHX0XM9zY29DhC4EMM8rZHxBOvhdE
                        gfVRSrErD7f0OBzVAfvGz+fprd9h3+PWsahzHVemf8TQ4KmsDPVijcFxNYVCAb9YwYklsGLB8muW
                        GYJmT6xF4VCpljEmQHSICFUyfjtl6nEsVCMOTuCjHYvwAgsIiIA1YAMfCBNKHE206XJmlCa+QKWw
                        nlTNKcwIKk9ijUW5NehIlXLuLiq5O/Dix2ADBSI8OfgQR7XOZ0l9MyY7m1jikxC9mJHyIp6cOoY1
                        4V6MUYhSBH5ApVBGicJiEYTfp9kD5SgshsnhaQKr8KsVMsVpwnqUO8c+zX6JXxJum6I2mSCVg3Kz
                        gAXEspvC+Glc7+3UtN+MZcbllCYuwak5i90shbGrkSgoz8XkHKwpkRk+l7quJ1EqTtqHudHLWBze
                        RN+uq+joOIa5V3yQDc9s5KaNl6JJkWILZWMxWBzPIZ/JE1gfpR2sNfyGsJtmD6wFJYKnXLLZaQqZ
                        NJFYhHl6O5umj+Hrm2/lHw4+llntGUojLtEVVbLpGIFotFiMMTheDTbYSKZ/EaGo5aZbIzjhr3Pi
                        SR+FKhQyawlCj+AUYgQ5H9Wo0N48VL4TpSwIeBrWjR5JdvuNuIkemutBB1/lgdyZjBSWMCfRT9j3
                        0G6IcqGEchQmE2ARRMBafssyw6LZAxEBpRje0cfs5lbeNH85xXyWvi1Ps1CeZkvhUL62YwMfiR1F
                        fp3P9LFddIY2gQPGQLYSR5QHUsavPI+rIV86kvlz3kLY3UBxtJeJaz5H8nxNaUOIamWS1Ak1xDZ8
                        HVYfi5EQ2aphw0iBX029n/xgH6v3z/HkJe/lgBU/JnnA8dRkQZUrNMcETydYMb+ViYERtm54llAo
                        zO8TZgjaAhbhdxkbUBOtoWf9ToZ7x5hKphnaOUKzUyLhlmlPTTA9sYyrm25iv1uv4tZnL2O+/zBr
                        4tewNHkfSW8S39VYarAqRKFgOOs991DIHcRYt0I1GhLvFjJXpyje5ePWRsn3hXlqn4386rnV4ETJ
                        mTIT+QrzkrVkkv/A90fX87dbrmTolCWonKLiBNQ7VWJBgbyKEYlF6O/vp29zDy21bRhr2BMtVkAs
                        L2JBRzTVcpmNdzzE2888gdldIVpjDunBLM8Uy7Q5uxhcdBDTC+6ha/1DPPzWE9i27TjqareyVH7E
                        W93LqA1NMVaoQwlMTyWBEipqYUxT6fYo3RNQ/GWApsiGqffwXMO+jCZyVOM1hCs+DTUhnOIUw0u6
                        OPy/ytiuD/LZ2IV09E0Q9sbosHmamxsZLjtEY1EKEzmMY8ABLHtg0WD5QxblulSqZQaf6mfBnIUU
                        KjlMLkNnvUV2lijGQtROZdj8rjNY82+fJ33gCnSyhkyhnTuDi1jnnMF50dPodDeRDeIYC5gwKgzB
                        hCVIG1QSTCAUcNkv8nMYiWHr9yXf7KGNRnxDJVxLXd8OFt9xDb/4zBU0ZbMEboWaUp6uWIGCqiOZ
                        ClMqFnnitk1gLMpzCIIqIPyGZYag2SMhCHxaEq08+PP7eGzjJhYdsIRdY9O0xDXL61yerkRpT48x
                        2DWPsYUH8ubvXcwtX/wOHc8MUedO82R5Lo/ljmV+4wYILBZBBGwG9ALBS0FxvSV2hOCJ0Lr/OBsP
                        +TsGG/dl4cQOyk4YFShGFqQ48fMX0LP/0aRbuuh4bhsD0QYWJ3xmN7Uw7lua2xKM9o7wyG0bqI3U
                        E5gAEPZE81Is6LBLPB3n7v+8nUUHLkF5LsUAjk1leG5bllxDK609/Tx68tkcddmnOOzqr3L/ORcQ
                        3mg4peMK3tV6EcVcBO0pBAsCVV/hiKHwoKHuDAgmFOWpCBxd4fRfnMd1iRvJO3VEyxn6l3ey340/
                        xDguG99zHi3dfeTqmgh6tnLoAkU13ILNFvFcjxsvux5VVkSaYlhreCkaLCDsidYa1/V45NaHGf7Y
                        IIlUjOmpHG3xMCtHH+Cu9DIWL5xPcnCCe8/7Eid88QMc9u0vc+8H/4mNfe9g9vAdrG74b8KmCBbE
                        ATTkhz1ix0VwGyp4W4rIHKAJFp35KxY/cisPchbhZSkOuOG7tD/1EHecfzmJsSwmEmZoVz8nqR20
                        xpcyks3S3NbMwM4BHvzpPbjaRWuHqm94KRoRxFr2pFKtUF/fQHdfNzde8WM+9q1Pk81kKbgJTjn6
                        MMYeHWBLxmeeKsCo4uef+x5v/dd/5qRvfoyfnf4lLg3fyhdLh9Lmb6agIxSqDezILmFZ9B5q9SiM
                        wrbGNayb+gAdm7qZ1/QQW+YeizM+zpGXfoVQJsudH7+McFaI5yfolRQrZIK3rl7GRKiBgY1P0NLS
                        zHVfvobR0REWtS2hXC0jIrwUzcsQEXwJmJWcxQ3fvpZDTj6MNYcfyvbtPcRqa/jAWxZzxTMF+qua
                        zmIaxpLc9dFvsWrt1ZxxyQd48ohTuPSQX+C5BVS6BlOO0JOB09ou4RDnGm6fupBf5c6koMCmIFyC
                        /e78T0745U/ZtvII7j3rI8QnciRyE2w3MZqcPO9b3clE1TA1Mcqaow7mnp/fxc+uWcu85AKqtoqI
                        sCfCDIvmFVhj8GIejaVmvn7OxVz8X/XMnjuXwcFhOutinD8f/mWrpbuimedkCW/P8dgp57DzgINZ
                        dfOPaH78EabrWsm1rWRq0VyWNtfxkJzL/SOnkS11Mi/by5zuDSR6HicyOEoQi3P331/E0PyVtGwf
                        QKmAHdUQc0IFzmyvkClAplimo6uT4bFhrvvaD0maFF7cw/d9XonmVfADn/a6WTzy/MP8+xe/yyXX
                        X0ZdbYot23axcE4jn1jg8+NdsDnj0OIZWnf0kkt1cfd5F9G6ZQvNWzbQMvwQc5/9BWItpYqLH4So
                        1RPYAPI1cTKNzXQfdAr9K5YTSQe0d/dQdFxGKpquhObsToOfLzBctrTPagVjueoTl7L90edZ2ryc
                        ol9EEF6JBgsIL0cQiuRZWb8fd//4Tr7/5qs46oS3EXZcxiqWJHDuPLhpl+W+ccETTSozSdtkQKGu
                        nc0nnoXvKsLZCWJjo4TTeay1dCdi5JqbKKYacKpQMzFF+44hio7DoEQwPhybynBwo8WJtDBVNjTW
                        OySSSa688JvcvvYWVjcdQFEVESO8HMtu2vIqGcEPVdmnYTGXnv8Vdj62nQuuugjxHCZGx0nnLCe0
                        KRbE4Z4x2JmDSTdCpBwQ6duF4zgEkRCl2naK9RorQBCgixVqp4aQapWcsUyiUQbmRascVZuj3Q0Y
                        zVSx5REWzl/I2PgoF536ae766R2saFlFWcqIEV6JsJtGeNVsYMG1rK4/gDuvvY2qDrjgG5+lrqGO
                        fLbAaD5PV9TjfbMNeRVmU3+aXdkKkxKjpDSmZKBYwgICWCxKBCNgtEuLG+C5ilU1VZY505Qcl11F
                        QekQjc0pxoZG+cczP86zdz7ForqlBARgeA0EzQuEV88YMCHLnIa5PPyDB/i/97+Xf7zmCyxds5zG
                        uhSbe4aoi2m6EgojQywJBUiinoHsGGlCFK2LBRSWsATEpIqulgl7LvsvmoUfWExVMTYpZCsVmhrq
                        qG2uY/uT2/inEz/J9MAkXc0LwLHgGxDhtdBWWbDCqyWA7/u4rktrQzulwRIfP+EcjjntGP7mnFOZ
                        t3I1cVNgdGyaZ4cyzJ8/n/pYhOp0N52eywyLRRAsggV811Kt5MkUmlBa44TCJNtamJ1I0du9nbVf
                        +jE3ffMGksUUnS1zsNYSBAGI8GpZdtP8EUQEYwyOq4mnalB5l7u+exvrb3+Mt//dsbz/rPeiGyMs
                        XDGXtsZ2xkfGyViPsvWw1jDDWhClEBFwwFcuZQuzGlqp2gpbH3metbddz/pbHuC5R7fQEe0k2hTF
                        GIPF8sewgOYFlj+OMYYZoajLvJrFpIcm+PEXvs/9P7iLFYevpOvNc1m63wpq21O0dDWjHI1YsFhE
                        BBsYcAStNWNDo2QzGdY/uY7/+t5PePy+R8lPZklJLUtal1G2ZYIg4I9nEQva8vqoBGXijUkWmBST
                        u8ZZ9x/38eC19xKri9O+qJ3a2fW0zevAiWpSqVqMNWQn0uSnc6QnM6R3TjG4vZ/JwQlsVaiJ1NDS
                        0grKUjZlsLwuNAJYy+shCAJmJJtqQQQVQCFfZOdDvWx/qBuDQYkCy24iWGswWEKEcSMOTYlmlOtg
                        rCGwPgS8TgQrBm3FIry+rLVgLYFAJBklmoxiLRg/wFqLMQYREBTKUShHYbGICEEQYKzhdWctCGij
                        DGKFPxcTBPyWAkFwHIffsFgCE/DnphCsGDTK8lfBgnEQbZQJiQVhL2fBinF0oOyQWDUbhL2VIIhR
                        BI4Z1sYLnsXIgVj2XgZEoBrxB3Q5Up60yiJWsGLZG4lVWIFivLRDl6PVPt/zUYEi0AF7I8FilEEF
                        co+uhiu3luKlr8emY6FAB+yNjLIY16d1e/MOXTMZ67HK3miVPY29mXCbGNmhvUII5auvGmXeAUTZ
                        O00C51tljTaOAeEp4EDgO8Aa9i6bgNOAbl6g+f+eBt4CnAT8LbAMaAFqgBCgeLEA8AEfsOwmgAe4
                        vD6KQMBuAmjAA4SXVgKGgK3AT4DrgDL/S/NiFrgZuBnwgCYgBoQADTiAAQIgAHwgACy7CeACUaAB
                        WADsCxwFzOHlPQ/cCTwNbAPSQAHwAQEEcAAX8AANeIDLbhWgDGSBEWCSPdC8tAqwiz/NbeyWAM4E
                        LgDaeLEB4GLgB0CJPzPNGyMDXA78BLgdWMFuTwFvA0Z4g2jeWMPAe4BnAQFOA0Z4A2neeM8BDwMe
                        8AxvMM1fxlOAw1+A5i9jPX8h/wMQGeJzdCQl0QAAAABJRU5ErkJggg==" alt="Дума Чукотского Автономного Округа">
                        </div> \n
                """;

    }

    private static String getHtmlEnd() {
        return """
                </div>
            </body>
            </html>
            """;
    }
}
