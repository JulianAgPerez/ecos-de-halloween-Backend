package com.halloween.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@org.hibernate.annotations.DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Setter
public class Story {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String audioUrl;
    private String backgroundImageUrl;

    @Lob
    private String body;
}
