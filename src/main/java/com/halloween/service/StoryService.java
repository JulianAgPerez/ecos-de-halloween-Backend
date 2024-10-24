package com.halloween.service;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.entities.Story;
import com.halloween.repository.StoryRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StoryService {

    @Autowired
    private StoryRepository storyRepository;

    //Metodos para StoryDTO
    @Transactional(readOnly = true)
    public StoryDTO getStoryById(Long id){
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuento no encontrado"));
        return convertToDTO(story);
    }

    @Transactional
    public StoryDTO createStory(StoryDTO storyDTO){
        Story story = convertToEntity(storyDTO);
        story = storyRepository.save(story);
        return convertToDTO(story);
    }

    @Transactional
    public StoryDTO updateStory(Long id, StoryDTO storyDTO){
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuento no encontrado"));
        story.setTitle(storyDTO.getTitle());
        story.setDescription(storyDTO.getDescription());
        story.setAudioUrl(storyDTO.getAudioUrl());
        story.setBackgroundImageUrl(storyDTO.getBackgroundImageUrl());
        story.setBody(storyDTO.getBody());

        return convertToDTO(storyRepository.save(story));
    }
    @Transactional
    public StoryDTO uploadBody(MultipartFile file, Long storyId) throws IOException {
        // Leer el contenido del archivo Word como String
        StringBuilder fileContent = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                fileContent.append(paragraph.getText()).append("\n");
            }
        }

        // Encontrar la historia por ID
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        // Asignar el contenido del archivo como String
        story.setBody(fileContent.toString()); // Ahora es String

        // Guardar la historia actualizada
        storyRepository.save(story);

        return convertToDTO(story);
    }

    //Metodos para StoryTitleDTO
    @Transactional(readOnly = true)
    public List<StoryTitleDTO> getAllStoryTitles(){
        List<Story> stories = storyRepository.findAll();
        return stories.stream().map(this::convertToTitleDTO).collect(Collectors.toList());
    }

    // Conversiones entre entidades y DTOs
    private StoryDTO convertToDTO(Story story){
        return new StoryDTO(story.getId(), story.getTitle(), story.getDescription(), story.getAudioUrl(), story.getBackgroundImageUrl(), story.getBody());
    }

    private Story convertToEntity(StoryDTO storyDTO){
        return new Story(storyDTO.getId(), storyDTO.getTitle(), storyDTO.getDescription(), storyDTO.getAudioUrl(), storyDTO.getBackgroundImageUrl(), storyDTO.getBody());
    }

    private StoryTitleDTO convertToTitleDTO(Story story){
        return new StoryTitleDTO(story.getId(), story.getTitle());
    }

    private Story converToEntity(StoryTitleDTO storyTitleDTO){
        return new Story(storyTitleDTO.getId(), storyTitleDTO.getTitle(), null,null,null,null);
    }


}
