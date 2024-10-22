package com.halloween.dtos;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Setter
public class StoryDTO {
    private Long id;
    private String title;
    private String description;
    private String audioUrl;
    private String backgroundImageUrl;
    private String body;
}
