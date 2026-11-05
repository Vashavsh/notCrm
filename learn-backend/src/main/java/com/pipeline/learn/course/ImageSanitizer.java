package com.pipeline.learn.course;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.pipeline.learn.common.ApiException;

/**
 * Turns an uploaded picture into a plain PNG. The pixels are decoded and drawn again, so metadata and anything
 * hidden in the file (a script in a "picture", a file that is two formats at once) does not survive. The size is
 * read before the picture is decoded, so a small file that expands to a huge image is refused.
 */
@Component
public class ImageSanitizer {

    public static final int MAX_BYTES = 6 * 1024 * 1024;
    static final long MAX_PIXELS = 20_000_000L;
    private static final Set<String> FORMATS = Set.of("png", "jpeg", "webp");

    /** Decoding a 20-megapixel picture takes ~80 MB; never do more than two at a time. */
    private final Semaphore decoding = new Semaphore(2);

    public byte[] toPng(byte[] upload) {
        if (upload == null || upload.length == 0 || upload.length > MAX_BYTES) {
            throw bad("Выберите изображение PNG, JPEG или WebP размером до 6 МБ.");
        }
        decoding.acquireUninterruptibly();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw bad("Файл не удалось прочитать как изображение.");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
                    throw bad("Поддерживаются PNG, JPEG и WebP до 20 мегапикселей.");
                }
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw bad("Поддерживаются PNG, JPEG и WebP до 20 мегапикселей.");
                }
                BufferedImage decoded = reader.read(0);
                boolean alpha = decoded.getColorModel().hasAlpha();
                BufferedImage clean = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                        alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D g = clean.createGraphics();
                try {
                    if (!alpha) {
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, clean.getWidth(), clean.getHeight());
                    }
                    g.drawImage(decoded, 0, 0, null);
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(clean, "png", out)) {
                    throw bad("Файл не удалось прочитать как изображение.");
                }
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw bad("Файл не удалось прочитать как изображение.");
        } finally {
            decoding.release();
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
