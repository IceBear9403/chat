package com.social.service.storage;

import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.service.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地文件存储实现。
 *
 * <p>文件保存到 {app.storage.local-path}/{bucket}/{yyyy/MM/dd}/{uuid}.{ext}，
 * 返回 {@code /uploads/{bucket}/...} 路径，由 WebConfig 映射为静态资源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final AppProperties properties;

    @Override
    public String upload(byte[] data, String originalFilename, String bucket) {
        if (data == null || data.length == 0) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件内容为空");
        }
        long maxBytes = properties.getStorage().getMaxFileSizeMb() * 1024 * 1024;
        if (data.length > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED,
                    "文件大小超出限制（最大 " + properties.getStorage().getMaxFileSizeMb() + "MB）");
        }

        String ext = extension(originalFilename);
        if (!isAllowedExt(ext)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "不支持的文件类型");
        }

        String relativePath = bucket + "/" + LocalDate.now().format(DAY)
                + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = Paths.get(properties.getStorage().getLocalPath(), relativePath);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            log.error("本地文件保存失败: {}", relativePath, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
        return "/uploads/" + relativePath;
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }

    private boolean isAllowedExt(String ext) {
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp3", ".m4a", ".mp4", ".mov", ".aac" -> true;
            default -> false;
        };
    }
}
