package org.teamsai.saimockbank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.Tag("integration")
class SaiMockBankApplicationTests {

    @Test
    void contextLoads() {
    }

}
