package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pipeline.learn.AbstractIntegrationTest;
import com.pipeline.learn.TestBrowser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentAndResourcesIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "lamp-river-tiger-47";
    /** A 1x1 lossless WebP. */
    private static final String TINY_WEBP = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @AfterEach
    void cleanUp() throws IOException {
        jdbc.update("delete from account");
        jdbc.update("delete from spring_session");
        Files.deleteIfExists(SECRETS.resolve("fixture-data.json"));
    }

    private TestBrowser student(String login) {
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash, status) values (?, ?, ?, ?, ?, 'ACTIVE')",
                UUID.randomUUID(), UUID.randomUUID(), login, "Анна Тестова", encoder.encode(PASSWORD));
        return new TestBrowser(http).loginAs(login, PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Object open(TestBrowser browser) {
        return map(browser.get("/api/lessons/lesson-01").getBody().get("attempt")).get("id");
    }

    private static byte[] image(String format, int width, int height, boolean alpha) throws IOException {
        BufferedImage img = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            img.setRGB(x, 0, 0xFF336699);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private ResponseEntity<Map<String, Object>> upload(TestBrowser browser, Object attempt, String question, byte[] bytes) {
        return browser.upload("/api/lessons/lesson-01/attachment", "graph.png", bytes,
                Map.of("questionId", question, "attemptId", String.valueOf(attempt)));
    }

    // ---- pictures attached to answers ----

    @Test
    void aPictureIsStoredAsPngAndShownOnlyToItsOwner() throws IOException {
        TestBrowser anna = student("att_anna");
        Object attempt = open(anna);
        var uploaded = upload(anna, attempt, "t01-q4", image("png", 40, 30, true));

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = (String) uploaded.getBody().get("attachmentUrl");
        assertThat(url).startsWith("/api/attachments/");

        ResponseEntity<byte[]> shown = anna.getBytes(url);
        assertThat(shown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shown.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(shown.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(shown.getBody()));
        assertThat(decoded.getWidth()).isEqualTo(40);

        // The lesson now links the picture from the answer, even before the answer is sent.
        var answers = map(map(anna.get("/api/lessons/lesson-01").getBody().get("attempt")).get("answers"));
        assertThat(map(answers.get("t01-q4"))).containsEntry("attachmentUrl", url).containsEntry("submitted", false);

        TestBrowser boris = student("att_boris");
        assertThat(boris.getBytes(url).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new TestBrowser(http).getBytes(url).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAnswerMayBeOnlyAPicture() throws IOException {
        TestBrowser browser = student("att_only");
        Object attempt = open(browser);
        var noPicture = browser.post("/api/lessons/lesson-01/answers", Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "  "));
        assertThat(noPicture.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        upload(browser, attempt, "t01-q4", image("png", 10, 10, false));
        var withPicture = browser.post("/api/lessons/lesson-01/answers", Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "  "));
        assertThat(withPicture.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aNewPictureReplacesTheOldOne() throws IOException {
        TestBrowser browser = student("att_replace");
        Object attempt = open(browser);
        String first = (String) upload(browser, attempt, "t01-q4", image("png", 10, 10, false)).getBody().get("attachmentUrl");
        String second = (String) upload(browser, attempt, "t01-q4", image("png", 20, 20, false)).getBody().get("attachmentUrl");

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from attachment", Integer.class)).isEqualTo(1);
        assertThat(browser.getBytes(first).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(browser.getBytes(second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void jpegAndWebpAreConvertedAndOtherFormatsRefused() throws IOException {
        TestBrowser browser = student("att_formats");
        Object attempt = open(browser);

        for (byte[] ok : new byte[][] {image("jpg", 30, 30, false), Base64.getDecoder().decode(TINY_WEBP)}) {
            var response = upload(browser, attempt, "t01-q4", ok);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            byte[] stored = browser.getBytes((String) response.getBody().get("attachmentUrl")).getBody();
            assertThat(stored).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        }
        var gif = upload(browser, attempt, "t01-q4", image("gif", 10, 10, false));
        assertThat(gif.getBody()).containsEntry("error", "Поддерживаются PNG, JPEG и WebP до 20 мегапикселей.");
        var text = upload(browser, attempt, "t01-q4", "это не картинка".getBytes(StandardCharsets.UTF_8));
        assertThat(text.getBody()).containsEntry("error", "Файл не удалось прочитать как изображение.");
        var empty = upload(browser, attempt, "t01-q4", new byte[0]);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aSmallFileThatExpandsToAHugePictureIsRefused() throws IOException {
        TestBrowser browser = student("att_bomb");
        Object attempt = open(browser);
        byte[] huge = image("png", 6000, 4000, false);
        assertThat(huge.length).as("compresses well, far below the 6 MB limit").isLessThan(2_000_000);

        var response = upload(browser, attempt, "t01-q4", huge);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Поддерживаются PNG, JPEG и WebP до 20 мегапикселей.");
    }

    @Test
    void oversizedUploadsGetAReadableMessage() {
        TestBrowser browser = student("att_big");
        Object attempt = open(browser);
        byte[] big = new byte[6 * 1024 * 1024 + 10];
        var response = upload(browser, attempt, "t01-q4", big);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Выберите изображение PNG, JPEG или WebP размером до 6 МБ.");
    }

    @Test
    void thingsHiddenInsideAPictureDoNotSurvive() throws IOException {
        TestBrowser browser = student("att_polyglot");
        Object attempt = open(browser);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(image("png", 10, 10, false));
        out.write("<script>alert('hidden')</script>".getBytes(StandardCharsets.UTF_8));

        var response = upload(browser, attempt, "t01-q4", out.toByteArray());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] stored = browser.getBytes((String) response.getBody().get("attachmentUrl")).getBody();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
    }

    @Test
    void picturesGoOnlyToOpenQuestionsAndNeedTheCurrentAttempt() throws IOException {
        TestBrowser browser = student("att_rules");
        Object attempt = open(browser);

        var single = upload(browser, attempt, "t01-q1", image("png", 10, 10, false));
        assertThat(single.getBody()).containsEntry("error", "Изображение можно прикрепить к открытому заданию.");
        var unknown = upload(browser, attempt, "t99", image("png", 10, 10, false));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var stale = upload(browser, ((Number) attempt).longValue() + 5, "t01-q4", image("png", 10, 10, false));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("code", "attempt_conflict");

        browser.post("/api/lessons/lesson-01/answers", Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "готово"));
        var late = upload(browser, attempt, "t01-q4", image("png", 10, 10, false));
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(late.getBody()).containsEntry("error", "Ответ уже отправлен. Для нового изображения начните повторное прохождение.");
    }

    // ---- resources and Amplitude logins ----

    @Test
    void theResourceCatalogIsOpenToStudentsButTheLoginsNeedAConfiguredFile() throws IOException {
        TestBrowser browser = student("res_user");
        var list = browser.get("/api/resources");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().get("resources").toString()).contains("fixture-data");
        assertThat(new TestBrowser(http).get("/api/resources").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var missing = browser.get("/api/resources/fixture-data");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(missing.getBody()).containsEntry("error", "Доступ ещё не настроен. Обратитесь к ментору.");

        Files.writeString(SECRETS.resolve("fixture-data.json"),
                "{\"title\":\"Тестовый набор\",\"organization\":\"Org\",\"login\":\"viewer@example.test\",\"password\":\"not-a-real-password\"}");
        var found = browser.get("/api/resources/fixture-data");
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).containsEntry("login", "viewer@example.test");
        assertThat(found.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");

        assertThat(browser.get("/api/resources/unknown").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new TestBrowser(http).get("/api/resources/fixture-data").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theLoginsOfAClosedAccountAreNotServed() throws IOException {
        Files.writeString(SECRETS.resolve("fixture-data.json"), "{\"login\":\"x\"}");
        TestBrowser browser = student("res_closed");
        jdbc.update("update account set status = 'CLOSED' where login = 'res_closed'");
        var response = browser.get("/api/resources/fixture-data");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "session_revoked");
    }

    // ---- lesson pictures ----

    @Test
    void lessonPicturesAreForLoggedInStudentsAndOnlyThoseALessonUses() throws IOException {
        TestBrowser browser = student("asset_user");
        ResponseEntity<byte[]> image = browser.getBytes("/assets/course/fixture-chart.png");

        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(image.getBody()).isEqualTo(Files.readAllBytes(Path.of("src/test/resources/content-fixture/images/fixture-chart.png")));

        assertThat(new TestBrowser(http).getBytes("/assets/course/fixture-chart.png").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.getBytes("/assets/course/not-in-any-lesson.png").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String sneaky : new String[] {"/assets/course/../stages.json", "/assets/course/%2e%2e/stages.json", "/assets/course/..%2fstages.json"}) {
            var response = browser.getBytes(sneaky);
            assertThat(response.getStatusCode().is2xxSuccessful()).as(sneaky).isFalse();
        }
    }
}
