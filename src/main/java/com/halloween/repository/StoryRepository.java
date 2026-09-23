package com.halloween.repository;

import com.halloween.entities.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<Story,Long> {

    interface StoryTitleView {
        Long getId();

        String getTitle();
    }

    @Query("select s.id as id, s.title as title from Story s")
    List<StoryTitleView> findAllTitles();

    interface StoryMetaView {
        String getTitle();
        String getDescription();
        String getAudioUrl();
        String getBackgroundImageUrl();
    }

    @Query("select s.title as title, s.description as description, s.audioUrl as audioUrl, s.backgroundImageUrl as backgroundImageUrl from Story s where s.id = :id")
    Optional<StoryMetaView> findMetaById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Story s SET s.body = :body WHERE s.id = :id")
    int updateBody(@Param("id") Long id, @Param("body") String body);
}
