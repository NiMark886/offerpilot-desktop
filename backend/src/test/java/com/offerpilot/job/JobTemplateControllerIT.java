package com.offerpilot.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobTemplateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsActiveJobTemplatesWithTrackInfoWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/job-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].title").value("Java 后端"))
                .andExpect(jsonPath("$.data[0].trackCode").isNotEmpty())
                .andExpect(jsonPath("$.data[0].questionCount").isNumber());
    }
}
