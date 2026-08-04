package com.halloween.classic;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Setter
public class ClassicStoryDTO {
    private String slug;
    private String title;
    private String author;
    private String translator;
    private Integer year;
    private String license;
    private String licenseUrl;
    private String attribution;
    private String sourceUrl;
    private String body;
}
