package com.converter.docxjats.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetJatsForArticle6032() throws Exception {
        mockMvc.perform(get("/jats/6032"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<article")))
                .andExpect(content().string(containsString("<journal-meta>")))
                .andExpect(content().string(containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.6032</article-id>")))
                .andExpect(content().string(containsString("<article-title>Cesáreas como expresión biopolítica del nacimiento")))
                .andExpect(content().string(containsString("<body>")));
    }

    @Test
    public void testGetJatsForArticle5939() throws Exception {
        mockMvc.perform(get("/jats/5939"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<article")))
                .andExpect(content().string(containsString("<journal-meta>")))
                .andExpect(content().string(containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.5939</article-id>")))
                .andExpect(content().string(containsString("<article-title>Prácticas de fin de vida y directivas anticipadas de voluntad")))
                .andExpect(content().string(containsString("<body>")));
    }

    @Test
    public void testPostJatsWithPayload() throws Exception {
        mockMvc.perform(post("/service/jats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\": \"6032\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("6032")));
    }

    @Test
    public void testInvalidArticleId() throws Exception {
        mockMvc.perform(get("/jats/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
