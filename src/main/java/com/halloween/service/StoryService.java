package com.halloween.service;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.entities.Story;
import com.halloween.repository.StoryRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StoryService {

    private static final Logger log = LoggerFactory.getLogger(StoryService.class);

    // DOCX files are ZIP containers and always start with the local file header "PK\x03\x04".
    private static final byte[] DOCX_MAGIC_BYTES = {0x50, 0x4B, 0x03, 0x04};
    private static final int MAX_EXTRACTED_LENGTH = 500_000;

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
        // POST must never merge-overwrite an existing row via a client-supplied id.
        storyDTO.setId(null);
        Story story = convertToEntity(storyDTO);
        story = storyRepository.save(story);
        return convertToDTO(story);
    }

    @Transactional
    public StoryDTO updateStory(Long id, StoryDTO storyDTO){
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuento no encontrado"));
        // Null fields in the PUT body mean "keep the existing value", not "wipe it".
        if (storyDTO.getTitle() != null) {
            story.setTitle(storyDTO.getTitle());
        }
        if (storyDTO.getDescription() != null) {
            story.setDescription(storyDTO.getDescription());
        }
        if (storyDTO.getAudioUrl() != null) {
            story.setAudioUrl(storyDTO.getAudioUrl());
        }
        if (storyDTO.getBackgroundImageUrl() != null) {
            story.setBackgroundImageUrl(storyDTO.getBackgroundImageUrl());
        }
        if (storyDTO.getBody() != null) {
            story.setBody(storyDTO.getBody());
        }

        return convertToDTO(storyRepository.save(story));
    }
    @Transactional
    public StoryDTO uploadBody(MultipartFile file, Long storyId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }
        final String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten archivos .docx");
        }

        final byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        }
        if (!startsWithDocxMagicBytes(fileBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid .docx file");
        }

        // Leer el contenido del archivo Word como String
        StringBuilder fileContent = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                fileContent.append(paragraph.getText()).append("\n");
                // Abort as soon as the extracted text exceeds the cap, before the
                // StringBuilder materializes the whole bomb in memory.
                if (fileContent.length() > MAX_EXTRACTED_LENGTH) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document too large");
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Corrupt or fake docx: POI throws a variety of runtime exceptions.
            log.warn("Rejected corrupt .docx upload '{}': {}", filename, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid .docx file");
        }

        // Encontrar la historia por ID
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuento no encontrado"));

        // Asignar el contenido del archivo como String
        story.setBody(fileContent.toString()); // Ahora es String

        // Guardar la historia actualizada
        storyRepository.save(story);

        return convertToDTO(story);
    }

    private static boolean startsWithDocxMagicBytes(byte[] bytes) {
        if (bytes.length < DOCX_MAGIC_BYTES.length) {
            return false;
        }
        for (int i = 0; i < DOCX_MAGIC_BYTES.length; i++) {
            if (bytes[i] != DOCX_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    //Metodos para StoryTitleDTO
    @Transactional(readOnly = true)
    public List<StoryTitleDTO> getAllStoryTitles(){
        return storyRepository.findAllTitles().stream()
                .map(view -> new StoryTitleDTO(view.getId(), view.getTitle()))
                .collect(Collectors.toList());
    }

    // Conversiones entre entidades y DTOs
    private StoryDTO convertToDTO(Story story){
        return new StoryDTO(story.getId(), story.getTitle(), story.getDescription(), story.getAudioUrl(), story.getBackgroundImageUrl(), story.getBody());
    }

    private Story convertToEntity(StoryDTO storyDTO){
        return new Story(storyDTO.getId(), storyDTO.getTitle(), storyDTO.getDescription(), storyDTO.getAudioUrl(), storyDTO.getBackgroundImageUrl(), storyDTO.getBody());
    }
}
