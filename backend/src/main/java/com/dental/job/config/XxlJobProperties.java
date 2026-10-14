package com.dental.job.config;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 执行器配置；不覆盖 toString，令牌不出现在日志、错误或接口响应。 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private final Admin admin = new Admin();
    private final Executor executor = new Executor();

    public void validateConfiguration() {
        if (!StringUtils.hasText(admin.addresses)) {
            throw new IllegalArgumentException("启用 XXL-JOB 时必须配置 xxl.job.admin.addresses");
        }
        for (String address : admin.addresses.split(",", -1)) {
            requireRootHttpUrl(address.trim(), "xxl.job.admin.addresses 必须是无 context-path 的调度中心 HTTP(S) 根地址");
        }
        if (admin.timeout < 1 || admin.timeout > 10) {
            throw new IllegalArgumentException("xxl.job.admin.timeout 必须为 1 至 10 秒");
        }
        if (executor.appname == null || !executor.appname.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("xxl.job.executor.appname 必须为 1 至 64 位字母、数字、短横线或下划线");
        }
        if (!StringUtils.hasText(executor.accessToken) || executor.accessToken.length() < 32
                || executor.accessToken.length() > 255 || executor.accessToken.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("xxl.job.executor.access-token 必须使用自行生成的 32 至 255 位非空白令牌");
        }
        if (executor.port < 1 || executor.port > 65535) {
            throw new IllegalArgumentException("xxl.job.executor.port 必须为 1 至 65535");
        }
        if (StringUtils.hasText(executor.address)) {
            requireRootHttpUrl(executor.address.trim(), "xxl.job.executor.address 必须是调度中心可访问的执行器 HTTP(S) 根地址");
        }
        if (!StringUtils.hasText(executor.logPath) || executor.logRetentionDays < 3 || executor.logRetentionDays > 3650) {
            throw new IllegalArgumentException("执行器日志目录不能为空，日志保留时间须为 3 至 3650 天");
        }
    }

    private static void requireRootHttpUrl(String value, String message) {
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (StringUtils.hasText(path) && !"/".equals(path))) {
                throw new IllegalArgumentException(message);
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    public Admin getAdmin() { return admin; }
    public Executor getExecutor() { return executor; }

    public static class Admin {
        private String addresses = "";
        private int timeout = 3;
        public String getAddresses() { return addresses; }
        public void setAddresses(String addresses) { this.addresses = addresses; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int timeout) { this.timeout = timeout; }
    }

    public static class Executor {
        private boolean enabled;
        private String appname = "dental-appointment-executor";
        private String accessToken = "";
        private String ip = "";
        private int port = 9999;
        private String address = "";
        private String logPath = ".local/xxl-job/jobhandler";
        private int logRetentionDays = 30;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppname() { return appname; }
        public void setAppname(String appname) { this.appname = appname; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getLogPath() { return logPath; }
        public void setLogPath(String logPath) { this.logPath = logPath; }
        public int getLogRetentionDays() { return logRetentionDays; }
        public void setLogRetentionDays(int logRetentionDays) { this.logRetentionDays = logRetentionDays; }
    }
}
