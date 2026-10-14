package com.dental.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AiConfig.class);

    @Test
    void disabledAiStartsWithoutCredentials() {
        runner.withPropertyValues("app.ai.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("dentalAiChatClient");
        });
    }

    @Test
    void enabledAiCreatesClientWithoutCallingProviderOnStartup() {
        runner.withPropertyValues("app.ai.enabled=true", "app.ai.api-key=test-key").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("dentalAiChatClient");
        });
    }

    @Test
    void rejectsMissingKeyAndRedirectToUntrustedEndpoint() {
        runner.withPropertyValues("app.ai.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.ai.enabled=true", "app.ai.api-key=test-key",
                "app.ai.base-url=https://other.example").run(context -> assertThat(context).hasFailed());
    }
}
