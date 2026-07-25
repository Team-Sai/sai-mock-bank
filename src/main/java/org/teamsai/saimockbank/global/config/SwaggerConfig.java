package org.teamsai.saimockbank.global.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mock 은행 API")
                        .description("Mock 은행 계좌·이체 API 명세")
                        .version("mvp"));
    }
}