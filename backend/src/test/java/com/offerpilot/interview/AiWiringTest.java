package com.offerpilot.interview;

import com.offerpilot.interview.service.InterviewBlueprintGenerator;
import com.offerpilot.interview.service.SpringAiBlueprintGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.ai.enabled=true",
        "spring.ai.openai.api-key=demo-key"
})
@ActiveProfiles("test")
class AiWiringTest {

    @Autowired
    private InterviewBlueprintGenerator blueprintGenerator;

    @Test
    void usesSpringAiGeneratorWhenAiIsEnabled() {
        assertThat(blueprintGenerator).isInstanceOf(SpringAiBlueprintGenerator.class);
    }
}
