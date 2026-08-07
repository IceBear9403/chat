package com.social.api.controller;

import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.model.Result;
import com.social.service.dto.FileUploadVO;
import com.social.service.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传接口（本地存储实现，生产切 OSS）。
 */
@Tag(name = "文件", description = "上传图片 / 语音 / 视频")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    @Operation(summary = "上传文件", description = "multipart/form-data，字段名 file；返回可访问 url")
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "bucket", defaultValue = StorageService.BUCKET_MOMENT) String bucket) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件为空");
        }
        try {
            String url = storageService.upload(file.getBytes(), file.getOriginalFilename(), bucket);
            return Result.ok(new FileUploadVO(url, file.getOriginalFilename(), file.getSize()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }
}
