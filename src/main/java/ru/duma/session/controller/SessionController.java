package ru.duma.session.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.duma.session.service.SessionService;
import ru.duma.session.service.WebProcessingListener;

import java.util.List;
import java.util.Map;

/**
 * Веб-контроллер. Заменяет JavaFX MainController.
 * Маршруты:
 *   GET  /                        — главная форма
 *   POST /api/start               — запуск обработки, возвращает jobId
 *   GET  /api/status/{jobId}      — текущий статус и новые строки лога (polling)
 *   GET  /api/log/{jobId}         — полный лог задачи
 */
@Controller
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /** Главная страница с формой */
    @GetMapping("/")
    public String index(Model model) {
        // Дефолтные значения как в JavaFX-версии
        model.addAttribute("resolutionDir", "/srv/Resolutions/");
        model.addAttribute("sessionDir",    "/srv/SessionDay/");
        model.addAttribute("agenda",        "/srv/повестка.docx");
        model.addAttribute("controlWord1",  "следующие вопросы:"); //Начало блока вопросов
        model.addAttribute("controlWord2",  "Председатель Думы") ; //Конец блока вопросов
        model.addAttribute("filesType",     "doc|docx|odt|rtf|xls|xlsx|ppt|pptx|jpg|jpeg");
        model.addAttribute("exportFileName","index.html");
        model.addAttribute("osType",        "Linux");
        return "index";
    }

    /** Запуск обработки — возвращает jobId */
    @PostMapping(value = "/api/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> startProcessing(
            @RequestParam String resolutionDir,
            @RequestParam String sessionDir,
            @RequestParam String filesType,
            @RequestParam String agenda,
            @RequestParam String controlWord1,
            @RequestParam String controlWord2,
            @RequestParam String osType,
            @RequestParam(defaultValue = "index.html") String exportFileName) {

        try {
            String jobId = sessionService.startProcessing(
                    resolutionDir, sessionDir, filesType, agenda,
                    controlWord1, controlWord2, osType, exportFileName
            );
            return ResponseEntity.ok(Map.of("jobId", jobId, "status", "started"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Polling статуса задачи.
     * Возвращает: статус, флаг завершения, новые строки лога начиная с offset.
     */
    @GetMapping(value = "/api/status/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") int offset) {

        if (!sessionService.jobExists(jobId)) {
            return ResponseEntity.notFound().build();
        }

        WebProcessingListener listener = sessionService.getListener(jobId);
        List<String> allLog = listener.getLog();

        // Отдаём только новые строки начиная с offset
        List<String> newLines = allLog.size() > offset
                ? allLog.subList(offset, allLog.size())
                : List.of();

        return ResponseEntity.ok(Map.of(
                "status",    listener.getStatus(),
                "done",      listener.isDone(),
                "failed",    listener.isFailed(),
                "completed", listener.isCompleted(),
                "logOffset", allLog.size(),
                "lines",     newLines,
                "error",     listener.getErrorMessage() != null ? listener.getErrorMessage() : ""
        ));
    }
}
