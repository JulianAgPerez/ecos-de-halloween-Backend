package com.halloween.repository;

import com.halloween.entities.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story,Long> {

    interface StoryTitleView {
        Long getId();

        String getTitle();
    }

    @Query("select s.id as id, s.title as title from Story s")
    List<StoryTitleView> findAllTitles();
}
