package com.halloween.classic;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

// Immutable by design: instances are shared across threads via the classicStories cache.
@Getter
@ToString
@AllArgsConstructor
public final class ClassicStoryDTO {
    private final String slug;
    private final String title;
    private final String author;
    private final String translator;
    private final Integer year;
    private final String license;
    private final String licenseUrl;
    private final String attribution;
    private final String sourceUrl;
    private final String body;
}
