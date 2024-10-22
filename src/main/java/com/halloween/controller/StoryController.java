package com.halloween.controller;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.service.StoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
@CrossOrigin("*")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping("/all-titles")
    public List<StoryTitleDTO> getAllStoryTitles(){
        return storyService.getAllStoryTitles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoryDTO> getStoryById(@PathVariable Long id){
        StoryDTO story = storyService.getStoryById(id);
        return ResponseEntity.ok(story);
    }

    @PostMapping
    public ResponseEntity<StoryDTO> createStory(@RequestBody StoryDTO storyDTO){
        StoryDTO newStory = storyService.createStory(storyDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newStory);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoryDTO> updateStory(@PathVariable Long id ,@RequestBody StoryDTO storyDTO){
        StoryDTO updatedStory = storyService.updateStory(id, storyDTO);
        return ResponseEntity.ok(updatedStory);
    }
}
