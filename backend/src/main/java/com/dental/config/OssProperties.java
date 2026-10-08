package com.dental.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

/** OSS 静态配置。凭证不参与日志输出；未启用时允许不填写 Bucket 和凭证。 */
@ConfigurationProperties(prefix = "app.oss")
public class OssProperties {

    private boolean enabled;
    private String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
    private String region = "cn-hangzhou";
    private String bucketName = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String publicBaseUrl = "";
    private String objectPrefix = "dental/uploads";
    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx",
            "xls", "xlsx", "txt", "csv", "zip");

    /** 只校验本地配置，不访问 OSS，也不在异常中输出凭证。 */
    public void validateConfiguration() {
        require(StringUtils.hasText(region), "app.oss.region 不能为空");
        require(StringUtils.hasText(bucketName)
                        && bucketName.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"),
                "app.oss.bucket-name 必须是有效的 Bucket 名称");
        require(StringUtils.hasText(accessKeyId), "app.oss.access-key-id 不能为空");
        require(StringUtils.hasText(accessKeySecret), "app.oss.access-key-secret 不能为空");
        URI endpointUri = parseHttpsUrl(endpoint, "app.oss.endpoint");
        require(endpointUri.getPath().isEmpty() || "/".equals(endpointUri.getPath()),
                "app.oss.endpoint 不能包含对象路径");
        if (StringUtils.hasText(publicBaseUrl)) {
            parseHttpsUrl(publicBaseUrl, "app.oss.public-base-url");
        }
        require(objectPrefix != null && objectPrefix.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*"),
                "app.oss.object-prefix 只能包含字母、数字、下划线、短横线和目录分隔符");
        require(maxFileSize != null && maxFileSize.toBytes() > 0,
                "app.oss.max-file-size 必须大于 0");
        require(allowedExtensions != null && !allowedExtensions.isEmpty()
                        && allowedExtensions.stream().allMatch(value ->
                                value != null && value.matches("[a-z0-9]+")),
                "app.oss.allowed-extensions 必须配置小写文件扩展名");
    }

    /** 返回无签名的访问域名；内网上传地址对应的链接转换为外网地址。 */
    public String resolvePublicBaseUrl() {
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl.replaceAll("/+$", "");
        }
        URI endpointUri = parseHttpsUrl(endpoint, "app.oss.endpoint");
        String host = endpointUri.getHost().replace("-internal.", ".");
        return "https://" + bucketName + "." + host;
    }

    private static URI parseHttpsUrl(String value, String key) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " 必须为合法的 HTTPS 地址");
        }
        require("https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost())
                        && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                        && uri.getPort() == -1,
                key + " 必须为不含认证信息、端口或签名参数的 HTTPS 地址");
        return uri;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getObjectPrefix() {
        return objectPrefix;
    }

    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
