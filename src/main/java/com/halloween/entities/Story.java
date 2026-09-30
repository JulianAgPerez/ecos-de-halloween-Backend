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

    private String author;

    // Account that submitted the story. Distinct from `author`, which is free
    // text kept only for display credit. Null means no session submitted it,
    // which is what makes a story community content.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}
