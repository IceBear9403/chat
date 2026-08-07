package com.social.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传返回。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadVO {

    private String url;
    private String originalName;
    private long size;
}
