package com.pipeline.learn.course;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.pipeline.learn.common.ApiException;

/**
 * The pictures inside lessons ({@code /assets/course/...}). Only logged-in students get them, and only the files some
 * lesson actually refers to, so the folder cannot be used to read anything else.
 */
@RestController
public class AssetController {

    private static final String PREFIX = "/assets/course";

    private final CourseContent content;

    public AssetController(CourseContent content) {
        this.content = content;
    }

    @GetMapping(PREFIX + "/{*path}")
    public ResponseEntity<Resource> image(@PathVariable String path) {
        Path root = content.imagesDir();
        Path file = root.resolve(path.startsWith("/") ? path.substring(1) : path).normalize();
        if (!content.isReferencedAsset(PREFIX + path) || !file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Материал не найден.");
        }
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
