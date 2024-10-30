package com.halloween.controller;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.service.StoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/stories")
@CrossOrigin(origins = "*", methods= {RequestMethod.GET,RequestMethod.POST})
public class StoryController {
    private final StoryService storyService;
    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping("/upload-body/{storyId}")
    public ResponseEntity<StoryDTO> uploadBody(@RequestParam("file") MultipartFile file, @PathVariable("storyId") Long storyId) throws Exception {
        try {
            StoryDTO updatedStory = storyService.uploadBody(file, storyId);
            return ResponseEntity.ok(updatedStory);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @GetMapping("/all-titles")
    public List<StoryTitleDTO> getAllStoryTitles() {
        return storyService.getAllStoryTitles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoryDTO> getStoryById(@PathVariable Long id) {
        StoryDTO story = storyService.getStoryById(id);
        return ResponseEntity.ok(story);
    }

    @PostMapping
    public ResponseEntity<StoryDTO> createStory(@RequestBody StoryDTO storyDTO) {
        StoryDTO newStory = storyService.createStory(storyDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(newStory);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoryDTO> updateStory(@PathVariable Long id, @RequestBody StoryDTO storyDTO) {
        StoryDTO updatedStory = storyService.updateStory(id, storyDTO);
        return ResponseEntity.ok(updatedStory);
    }
}
