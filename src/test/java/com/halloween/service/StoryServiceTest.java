package com.halloween.service;

import com.halloween.dtos.StoryDTO;
import com.halloween.dtos.StoryTitleDTO;
import com.halloween.entities.Story;
import com.halloween.entities.User;
import com.halloween.repository.StoryRepository;
import com.halloween.repository.UserRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock private StoryRepository storyRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private StoryService storyService;

    private static final String OWNER_EMAIL = "owner@example.com";

    private User owner(long id) {
        return User.builder().id(id).name("Propietario").email(OWNER_EMAIL).password("hash").build();
    }

    /** An authenticated caller, as JwtAuthFilter would leave it in the context. */
    private void authenticateAs(String email) {
        var authentication = new UsernamePasswordAuthenticationToken(
                org.springframework.security.core.userdetails.User.withUsername(email)
                        .password("hash").authorities(List.of()).build(),
                null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static StoryRepository.StoryTitleView titleView(Long id, String title, Long ownerId) {
        return new StoryRepository.StoryTitleView() {
            @Override public Long getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public String getDescription() { return null; }
            @Override public Long getOwnerId() { return ownerId; }
        };
    }

    private Story story() {
        return new Story(1L, "El susurro", "Un cuento", "audio.mp3", "bg.png", "Habia una vez...", "Anonimo", null);
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
        StoryDTO input = new StoryDTO(null, "Nuevo", "Desc", null, null, "cuerpo", "Visitante");
        Story saved = new Story(1L, "Nuevo", "Desc", null, null, "cuerpo", "Visitante", null);
        when(storyRepository.save(any(Story.class))).thenReturn(saved);

        StoryDTO dto = storyService.createStory(input);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Nuevo");
    }

    @Test
    void createStory_withAuthor_persistsAuthor() {
        StoryDTO input = new StoryDTO(null, "Nuevo", "Desc", null, null, "cuerpo", "Visitante");
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoryDTO dto = storyService.createStory(input);

        assertThat(dto.getAuthor()).isEqualTo("Visitante");
    }

    @Test
    void createStory_nullsClientSuppliedIdBeforeSave() {
        StoryDTO input = new StoryDTO(7L, "Nuevo", "Desc", null, null, "cuerpo", null);
        when(storyRepository.save(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        storyService.createStory(input);

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void updateStory_updatesFields() {
        Story existing = story();
        StoryDTO input = new StoryDTO(1L, "Titulo nuevo", "Desc nueva", "audio2.mp3", "bg2.png", "cuerpo nuevo", "Autora");
        when(storyRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storyRepository.save(any(Story.class))).thenReturn(existing);

        StoryDTO dto = storyService.updateStory(1L, input);

        assertThat(dto.getTitle()).isEqualTo("Titulo nuevo");
        assertThat(dto.getDescription()).isEqualTo("Desc nueva");
        assertThat(dto.getBody()).isEqualTo("cuerpo nuevo");
        assertThat(dto.getAuthor()).isEqualTo("Autora");
    }

    @Test
    void updateStory_nullFieldsKeepExistingValues() {
        Story existing = story();
        StoryDTO input = new StoryDTO(1L, "Titulo nuevo", null, null, null, null, null);
        when(storyRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storyRepository.save(any(Story.class))).thenReturn(existing);

        StoryDTO dto = storyService.updateStory(1L, input);

        assertThat(dto.getTitle()).isEqualTo("Titulo nuevo");
        assertThat(dto.getDescription()).isEqualTo("Un cuento");
        assertThat(dto.getAudioUrl()).isEqualTo("audio.mp3");
        assertThat(dto.getBackgroundImageUrl()).isEqualTo("bg.png");
        assertThat(dto.getBody()).isEqualTo("Habia una vez...");
        assertThat(dto.getAuthor()).isEqualTo("Anonimo");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createStory_withSession_assignsOwnerFromSecurityContext() {
        authenticateAs(OWNER_EMAIL);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner(7L)));
        when(storyRepository.save(any(Story.class)))
                .thenAnswer(call -> call.getArgument(0));

        storyService.createStory(new StoryDTO(null, "T", "D", null, null, "cuerpo", "Ana"));

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isNotNull();
        assertThat(captor.getValue().getOwner().getId()).isEqualTo(7L);
    }

    @Test
    void createStory_withoutSession_leavesOwnerNullSoStoryIsCommunity() {
        when(storyRepository.save(any(Story.class)))
                .thenAnswer(call -> call.getArgument(0));

        storyService.createStory(new StoryDTO(null, "T", "D", null, null, "cuerpo", "Ana"));

        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isNull();
    }

    /**
     * The anonymous-token guard: isAuthenticated() is true for it, so without the
     * explicit check an anonymous POST would resolve a user named "anonymousUser".
     */
    @Test
    void createStory_withAnonymousToken_leavesOwnerNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        when(storyRepository.save(any(Story.class)))
                .thenAnswer(call -> call.getArgument(0));

        storyService.createStory(new StoryDTO(null, "T", "D", null, null, "cuerpo", "Ana"));

        verify(userRepository, never()).findByEmail(anyString());
        ArgumentCaptor<Story> captor = ArgumentCaptor.forClass(Story.class);
        verify(storyRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isNull();
    }

    @Test
    void getAllStoryTitles_marksOwnedAndCommunityStories() {
        authenticateAs(OWNER_EMAIL);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner(7L)));
        when(storyRepository.findAllTitles()).thenReturn(List.of(
                titleView(1L, "Mio", 7L),
                titleView(2L, "De la comunidad", null)));

        List<StoryTitleDTO> titles = storyService.getAllStoryTitles();

        assertThat(titles).hasSize(2);
        assertThat(titles.get(0).isMine()).isTrue();
        assertThat(titles.get(1).isMine()).isFalse();
    }

    @Test
    void getAllStoryTitles_withoutSession_marksEverythingAsNotMine() {
        when(storyRepository.findAllTitles()).thenReturn(List.of(
                titleView(1L, "Mio de antes", 7L),
                titleView(2L, "De la comunidad", null)));

        List<StoryTitleDTO> titles = storyService.getAllStoryTitles();

        assertThat(titles).allMatch(title -> !title.isMine());
    }

    /**
     * Regression: ownerId is a nullable Long. Comparing it with == unboxes it and
     * threw on every community story while a session was present.
     */
    @Test
    void getAllStoryTitles_withSessionAndNullOwner_doesNotThrow() {
        authenticateAs(OWNER_EMAIL);
        when(userRepository.findByEmail(OWNER_EMAIL)).thenReturn(Optional.of(owner(7L)));
        when(storyRepository.findAllTitles()).thenReturn(List.of(
                titleView(2L, "De la comunidad", null)));

        List<StoryTitleDTO> titles = storyService.getAllStoryTitles();

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0).isMine()).isFalse();
    }

    @Test
    void getAllStoryTitles_usesTitleProjectionWithoutLoadingBodies() {
        StoryRepository.StoryTitleView view = new StoryRepository.StoryTitleView() {
            @Override public Long getId() { return 1L; }
            @Override public String getTitle() { return "El susurro"; }
            @Override public String getDescription() { return "Un cuento"; }
            @Override public Long getOwnerId() { return null; }
        };
        when(storyRepository.findAllTitles()).thenReturn(List.of(view));

        List<StoryTitleDTO> titles = storyService.getAllStoryTitles();

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0).getId()).isEqualTo(1L);
        assertThat(titles.get(0).getTitle()).isEqualTo("El susurro");
        assertThat(titles.get(0).getDescription()).isEqualTo("Un cuento");
        verify(storyRepository, never()).findAll();
    }

    @Test
    void uploadBody_withValidDocx_updatesBody() throws Exception {
        StoryRepository.StoryMetaView meta = new StoryRepository.StoryMetaView() {
            @Override public String getTitle() { return "El susurro"; }
            @Override public String getDescription() { return "Un cuento"; }
            @Override public String getAudioUrl() { return "audio.mp3"; }
            @Override public String getBackgroundImageUrl() { return "bg.png"; }
            @Override public String getAuthor() { return "Anonimo"; }
        };
        when(storyRepository.updateBody(eq(1L), anyString())).thenReturn(1);
        when(storyRepository.findMetaById(1L)).thenReturn(Optional.of(meta));

        StoryDTO dto = storyService.uploadBody(validDocx("Un parrafo de prueba"), 1L);

        assertThat(dto.getTitle()).isEqualTo("El susurro");
        assertThat(dto.getBody()).contains("Un parrafo de prueba");
        assertThat(dto.getAuthor()).isEqualTo("Anonimo");
        verify(storyRepository).updateBody(eq(1L), anyString());
        verify(storyRepository, never()).findById(any());
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

    @Test
    void uploadBody_withNullFilename_throws400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", null,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocxBytes("contenido"));

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadBody_withDocxExtensionButNoMagicBytes_throws400() {
        MockMultipartFile file = new MockMultipartFile("file", "falso.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "esto no es un zip".getBytes());

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadBody_withCorruptDocxBytes_throws400() {
        byte[] corrupt = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        MockMultipartFile file = new MockMultipartFile("file", "roto.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", corrupt);

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadBody_withOversizedText_throws400DocumentTooLarge() throws Exception {
        // Random characters keep the docx from being highly compressible, so POI's
        // zip-bomb detector stays quiet and the extracted-text cap is what rejects it.
        java.util.Random random = new java.util.Random(42);
        String alphabet = "abcdefghijklmnopqrstuvwxyz ";
        StringBuilder oversized = new StringBuilder();
        while (oversized.length() <= 500_000) {
            oversized.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        MockMultipartFile file = new MockMultipartFile("file", "grande.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocxBytes(oversized.toString()));

        assertThatThrownBy(() -> storyService.uploadBody(file, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Document too large");
    }

    private MockMultipartFile validDocx(String content) throws Exception {
        return new MockMultipartFile(
                "file", "story.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                validDocxBytes(content));
    }

    private byte[] validDocxBytes(String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(content);
            document.write(baos);
        }
        return baos.toByteArray();
    }
}
