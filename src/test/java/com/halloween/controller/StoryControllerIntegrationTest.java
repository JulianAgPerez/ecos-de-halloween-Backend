package com.halloween.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halloween.entities.Story;
import com.halloween.entities.User;
import com.halloween.repository.StoryRepository;
import com.halloween.repository.UserRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StoryRepository storyRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        storyRepository.deleteAll();

        userRepository.save(User.builder()
                .name("Admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("plainpass"))
                .build());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.com","password":"plainpass"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        token = loginBody.get("access_token").asText();
    }

    @Test
    void getStories_publicWithoutToken_returns200() throws Exception {
        storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));

        mockMvc.perform(get("/api/stories/all-titles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Titulo"));
    }

    @Test
    void getStoryById_publicWithoutToken_returns200() throws Exception {
        Story story = storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));

        mockMvc.perform(get("/api/stories/" + story.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo"));
    }

    @Test
    void createStory_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nueva","description":"Desc"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createStory_withToken_returns201() throws Exception {
        mockMvc.perform(post("/api/stories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nueva","description":"Desc","body":"cuerpo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Nueva"));
    }

    @Test
    void updateStory_withoutToken_returns401() throws Exception {
        Story story = storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));

        mockMvc.perform(put("/api/stories/" + story.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Editada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStory_withToken_returns200() throws Exception {
        Story story = storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));

        mockMvc.perform(put("/api/stories/" + story.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Editada"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Editada"));
    }

    @Test
    void uploadBody_withoutToken_returns401() throws Exception {
        Story story = storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));
        MockMultipartFile file = new MockMultipartFile("file", "story.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocx());

        mockMvc.perform(multipart("/api/stories/upload-body/" + story.getId()).file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadBody_withTokenAndValidDocx_returns200() throws Exception {
        Story story = storyRepository.save(new Story(null, "Titulo", "Desc", null, null, "cuerpo"));
        MockMultipartFile file = new MockMultipartFile("file", "story.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocx());

        mockMvc.perform(multipart("/api/stories/upload-body/" + story.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").isNotEmpty());
    }

    private byte[] validDocx() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Un parrafo de prueba");
            document.write(baos);
        }
        return baos.toByteArray();
    }
}
