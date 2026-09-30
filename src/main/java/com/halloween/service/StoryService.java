package com.halloween.service;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.entities.Story;
import com.halloween.entities.User;
import com.halloween.repository.StoryRepository;
import com.halloween.repository.UserRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

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

    @Autowired
    private UserRepository userRepository;

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
        // Ownership is resolved from the security context, never from the request
        // body: a client-supplied owner would let anyone claim another account.
        // No session means no owner, which is what marks a story as community.
        story.setOwner(currentUserOrNull().orElse(null));
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
        if (storyDTO.getAuthor() != null) {
            story.setAuthor(storyDTO.getAuthor());
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

        // Encontrar la historia por ID y reemplazar su body sin leer el LOB viejo
        int updated = storyRepository.updateBody(storyId, fileContent.toString());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuento no encontrado");
        }

        var meta = storyRepository.findMetaById(storyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuento no encontrado"));

        return new StoryDTO(storyId, meta.getTitle(), meta.getDescription(), meta.getAudioUrl(), meta.getBackgroundImageUrl(), fileContent.toString(), meta.getAuthor());
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
        // Resolved once per request, not per row: an empty result must not pay
        // for a user lookup.
        final Optional<User> currentUser = currentUserOrNull();
        return storyRepository.findAllTitles().stream()
                .map(view -> {
                    // Objects.equals, not ==: ownerId is a nullable Long and ==
                    // would unbox it, throwing on every community story.
                    final boolean mine = currentUser
                            .map(user -> Objects.equals(user.getId(), view.getOwnerId()))
                            .orElse(false);
                    return new StoryTitleDTO(view.getId(), view.getTitle(), mine);
                })
                .collect(Collectors.toList());
    }

    /**
     * The authenticated account behind the current request, or empty for an
     * anonymous caller. {@link Authentication#isAuthenticated()} is true for the
     * anonymous token too, so it is excluded explicitly.
     */
    private Optional<User> currentUserOrNull() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(authentication.getName());
    }

    // Conversiones entre entidades y DTOs
    private StoryDTO convertToDTO(Story story){
        return new StoryDTO(story.getId(), story.getTitle(), story.getDescription(), story.getAudioUrl(), story.getBackgroundImageUrl(), story.getBody(), story.getAuthor());
    }

    private Story convertToEntity(StoryDTO storyDTO){
        // owner is set by the caller from the security context, never from the DTO.
        return new Story(storyDTO.getId(), storyDTO.getTitle(), storyDTO.getDescription(), storyDTO.getAudioUrl(), storyDTO.getBackgroundImageUrl(), storyDTO.getBody(), storyDTO.getAuthor(), null);
    }
}
