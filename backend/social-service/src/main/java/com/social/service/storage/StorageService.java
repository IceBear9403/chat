package com.social.service.storage;

/**
 * 文件存储抽象。
 *
 * <p>本地实现 {@link LocalStorageService}（开发/内测用，返回 /uploads/ 路径）；
 * 阶段六生产环境新增 OSS 实现（阿里云 OSS + STS 直传），业务层无需改动。
 *
 * <p>keep 与 spring-web 解耦：上传方法只接收字节，不依赖 MultipartFile。
 */
public interface StorageService {

    String BUCKET_AVATAR = "avatar";
    String BUCKET_MOMENT = "moment";
    String BUCKET_CHAT = "chat";

    /**
     * 保存文件。
     *
     * @param data            文件字节
     * @param originalFilename 原始文件名（用于取扩展名）
     * @param bucket          分类目录：avatar / moment / chat
     * @return 可访问的相对/绝对 URL
     */
    String upload(byte[] data, String originalFilename, String bucket);
}
