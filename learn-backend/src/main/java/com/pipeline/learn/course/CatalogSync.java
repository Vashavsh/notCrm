package com.pipeline.learn.course;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pipeline.learn.course.CourseContent.Lesson;
import com.pipeline.learn.course.CourseContent.Module;

/**
 * Copies the lesson list of the course into {@code learn.lesson_catalog} at every startup, so the CRM (which reads the
 * progress view) can show lesson and block titles without access to the content files. The table is replaced in one
 * transaction, so a reader sees the old list or the new one, never half of each.
 */
@Component
public class CatalogSync implements ApplicationRunner {

    private final CourseContent content;
    private final JdbcTemplate jdbc;

    public CatalogSync(CourseContent content, JdbcTemplate jdbc) {
        this.content = content;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync();
    }

    @Transactional
    public void sync() {
        List<Object[]> rows = new ArrayList<>();
        for (Lesson lesson : content.lessons()) {
            Module module = CourseContent.MODULES.stream().filter(m -> m.id().equals(lesson.moduleId())).findFirst().orElseThrow();
            int moduleOrder = CourseContent.MODULES.indexOf(module) + 1;
            rows.add(new Object[] {lesson.id(), module.id(), module.title(), moduleOrder, lesson.order(), lesson.title()});
        }
        jdbc.update("delete from lesson_catalog");
        jdbc.batchUpdate("insert into lesson_catalog (lesson_id, module_id, module_title, module_order, lesson_order, title) values (?, ?, ?, ?, ?, ?)", rows);
    }
}
