package com.dental.job.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认不创建执行器；只有显式启用且配置完整时才启动独立通讯端口。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfig {

    @Bean
    @ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        properties.validateConfiguration();
        XxlJobProperties.Executor settings = properties.getExecutor();
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        executor.setTimeout(properties.getAdmin().getTimeout());
        executor.setEnabled(true);
        executor.setAppname(settings.getAppname());
        executor.setAccessToken(settings.getAccessToken());
        executor.setIp(settings.getIp());
        executor.setPort(settings.getPort());
        executor.setAddress(settings.getAddress());
        executor.setLogPath(settings.getLogPath());
        executor.setLogRetentionDays(settings.getLogRetentionDays());
        executor.setGlueEnabled(false);
        return executor;
    }
}
