package com.pipeline.learn.course;

/** An attachment without its image bytes, enough to link to it. */
public record AttachmentRef(Long id, String questionId) {
}
