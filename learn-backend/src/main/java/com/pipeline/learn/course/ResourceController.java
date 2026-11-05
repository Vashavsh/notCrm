package com.pipeline.learn.course;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.learn.common.ApiException;

/**
 * External resources of the course (the Instachat datasets in Amplitude). The catalog is public to every student;
 * the shared logins live in {@code <secrets dir>/<resource id>.json}, outside the repository and the content.
 */
@RestController
public class ResourceController {

    private final CourseContent content;
    private final ObjectMapper json;

    public ResourceController(CourseContent content, ObjectMapper json) {
        this.content = content;
        this.json = json;
    }

    @GetMapping("/api/resources")
    public Map<String, Object> list() {
        return Map.of("resources", content.resources());
    }

    @GetMapping("/api/resources/{id}")
    public ResponseEntity<Map<String, Object>> details(@PathVariable String id) {
        boolean known = content.resources().stream().anyMatch(r -> id.equals(r.get("id")));
        if (!known) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Доступ пока не открыт.");
        }
        Path dir = content.secretsDir();
        Path file = dir == null ? null : dir.resolve(id + ".json").normalize();
        if (file == null || !file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw notConfigured();
        }
        try {
            Map<String, Object> secret = json.readValue(file.toFile(), new TypeReference<>() {
            });
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(secret);
        } catch (IOException | RuntimeException e) {
            throw notConfigured();
        }
    }

    private static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Доступ ещё не настроен. Обратитесь к ментору.");
    }
}
