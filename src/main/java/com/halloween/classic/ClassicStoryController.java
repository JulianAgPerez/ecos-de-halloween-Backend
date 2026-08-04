package com.halloween.classic;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classics")
public class ClassicStoryController {

    private final ClassicStoryService classicStoryService;

    public ClassicStoryController(ClassicStoryService classicStoryService) {
        this.classicStoryService = classicStoryService;
    }

    @GetMapping
    public List<ClassicStoryDTO> getAll() {
        return classicStoryService.getAll();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ClassicStoryDTO> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(classicStoryService.getStory(slug));
    }
}
