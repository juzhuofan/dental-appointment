package com.dental.file.vo;

/** 上传结果。url 是可直接保存到业务表的普通链接，不加密且不包含临时签名。 */
public record FileUploadVO(String url, String objectKey, String originalFilename,
                           long size, String contentType) {
}
