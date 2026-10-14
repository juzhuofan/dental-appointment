package com.dental.job.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 不启动执行器、不访问调度中心或数据库。 */
class XxlJobConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(XxlJobConfig.class);

    @Test
    void defaultDisabledDoesNotStartExecutor() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(XxlJobSpringExecutor.class);
            assertFalse(context.getBean(XxlJobProperties.class).getExecutor().isEnabled());
        });
    }

    @Test
    void explicitlyDisabledAllowsMissingCredentials() {
        runner.withPropertyValues("xxl.job.executor.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void incompleteEnabledConfigurationFailsBeforeExecutorConstruction() {
        try (MockedConstruction<XxlJobSpringExecutor> construction = mockConstruction(XxlJobSpringExecutor.class)) {
            runner.withPropertyValues("xxl.job.executor.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
            assertThat(construction.constructed()).isEmpty();
        }
    }

    @Test
    void completeConfigurationBindsOnlySupportedApiAndDisablesGlue() {
        try (MockedConstruction<XxlJobSpringExecutor> construction = mockConstruction(XxlJobSpringExecutor.class)) {
            runner.withPropertyValues("xxl.job.executor.enabled=true", "xxl.job.admin.addresses=http://127.0.0.1:18080",
                    "xxl.job.executor.access-token=" + "x".repeat(40), "xxl.job.executor.port=19999",
                    "xxl.job.executor.address=http://host.docker.internal:19999/", "xxl.job.executor.log-path=.local/test-job-logs")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(XxlJobSpringExecutor.class);
                        XxlJobSpringExecutor executor = context.getBean(XxlJobSpringExecutor.class);
                        verify(executor).setAdminAddresses("http://127.0.0.1:18080");
                        verify(executor).setTimeout(3);
                        verify(executor).setEnabled(true);
                        verify(executor).setAppname("dental-appointment-executor");
                        verify(executor).setAccessToken("x".repeat(40));
                        verify(executor).setPort(19999);
                        verify(executor).setAddress("http://host.docker.internal:19999/");
                        verify(executor).setLogPath(".local/test-job-logs");
                        verify(executor).setLogRetentionDays(30);
                        verify(executor).setGlueEnabled(false);
                    });
            assertEquals(1, construction.constructed().size());
        }
    }

    @Test
    void rejectsLegacyContextPathAndCredentialBearingUrlsWithoutEchoingValues() {
        for (String address : new String[] {"http://localhost:18080/xxl-job-admin", "http://name:secret@localhost:18080", "http://localhost:18080?token=secret"}) {
            XxlJobProperties properties = validProperties();
            properties.getAdmin().setAddresses(address);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, properties::validateConfiguration);
            assertThat(failure.getMessage()).doesNotContain("secret");
        }
    }

    @Test
    void rejectsWeakTokenAndInvalidPort() {
        XxlJobProperties properties = validProperties();
        properties.getExecutor().setAccessToken("default_token");
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);
        properties.getExecutor().setAccessToken("x".repeat(40));
        properties.getExecutor().setPort(0);
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);
    }

    private static XxlJobProperties validProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setAddresses("http://localhost:18080");
        properties.getExecutor().setAccessToken("x".repeat(40));
        return properties;
    }
}
