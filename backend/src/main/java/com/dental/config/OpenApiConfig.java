package com.dental.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dentalOpenApi() {
        return new OpenAPI().info(new Info().title("口腔诊所预约 API").version("v1"));
    }
}
