package com.halloween.service;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.entities.Story;
import com.halloween.repository.StoryRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock private StoryRepository storyRepository;
    @InjectMocks private StoryService storyService;

    private Story story() {
        return new Story(1L, "El susurro", "Un cuento", "audio.mp3", "bg.png", "Habia una vez...");
    }

    @Test
    void getStoryById_returnsDto() {
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story()));

        StoryDTO dto = storyService.getStoryById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("El susurro");
        assertThat(dto.getBody()).isEqualTo("Habia una vez...");
    }

    @Test
    void getStoryById_notFound_throws404() {
        when(storyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storyService.getStoryById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createStory_savesAndReturnsDto() {
        StoryDTO input = new StoryDTO(null, "Nuevo", "Desc", null, null, "cuerpo");
        Story saved = new Story(1L, "Nuevo", "Desc", null, null, "cuerpo");
        when(storyRepository.save(any(Story.class))).thenReturn(saved);

        StoryDTO dto = storyService.createStory(input);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Nuevo");
    }

    @Test
    void updateStory_updatesFields() {
        Story existing = story();
        StoryDTO input = new StoryDTO(1L, "Titulo nuevo", "Desc nueva", "audio2.mp3", "bg2.png", "cuerpo nuevo");
        when(storyRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storyRepository.save(any(Story.class))).thenReturn(existing);

        StoryDTO dto = storyService.updateStory(1L, input);

        assertThat(dto.getTitle()).isEqualTo("Titulo nuevo");
        assertThat(dto.getDescription()).isEqualTo("Desc nueva");
        assertThat(dto.getBody()).isEqualTo("cuerpo nuevo");
    }

    @Test
    void getAllStoryTitles_returnsTitles() {
        when(storyRepository.findAll()).thenReturn(List.of(story()));

        List<StoryTitleDTO> titles = storyService.getAllStoryTitles();

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0).getTitle()).isEqualTo("El susurro");
    }

    @Test
    void uploadBody_withValidDocx_updatesBody() throws Exception {
        when(storyRepository.findById(1L)).thenReturn(Optional.of(story()));
        when(storyRepository.save(any(Story.class))).thenReturn(story());

        StoryDTO dto = storyService.uploadBody(validDocx("Un parrafo de prueba"), 1L);

        assertThat(dto.getBody()).contains("Un parrafo de prueba");
    }

    @Test
    void uploadBody_withEmptyFile_throws400() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "story.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]);

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadBody_withWrongFileType_throws400() {
        MockMultipartFile file = new MockMultipartFile("file", "nota.txt", "text/plain", "hola".getBytes());

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private MockMultipartFile validDocx(String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(content);
            document.write(baos);
        }
        return new MockMultipartFile(
                "file", "story.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                baos.toByteArray());
    }
}
