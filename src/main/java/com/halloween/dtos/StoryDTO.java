package com.halloween.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Setter
public class StoryDTO {
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String title;

    // Column defaults to varchar(255); cap matches the database.
    @Size(max = 255)
    private String description;

    @Size(max = 255)
    private String audioUrl;

    @Size(max = 255)
    private String backgroundImageUrl;

    @Size(max = 500_000)
    private String body;
}
