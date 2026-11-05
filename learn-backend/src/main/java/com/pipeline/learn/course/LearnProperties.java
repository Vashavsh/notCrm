package com.pipeline.learn.course;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the course material lives. Both directories are outside the repository (it is public):
 * {@code contentDir} holds {@code lessons/lesson-NN.json}, {@code stages.json}, {@code resources.json}
 * and {@code images/}; {@code secretsDir} holds one {@code <resource id>.json} with the shared logins
 * for each external resource (Amplitude datasets).
 */
@ConfigurationProperties(prefix = "learn")
public record LearnProperties(String contentDir, String secretsDir) {
}
