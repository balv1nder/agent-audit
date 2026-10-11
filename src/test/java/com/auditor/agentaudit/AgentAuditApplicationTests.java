package com.auditor.agentaudit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.auditor.agentaudit.support.StubChatModelConfig;

@SpringBootTest
@Import(StubChatModelConfig.class)
class AgentAuditApplicationTests {

    @Test
    void contextLoads() {
    }

}
