package com.lucky.core.service;

import com.lucky.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 文件存储服务：基于 MinIO，头像/图片等对象的上传
 * <p>返回的 URL 是拼接的直链，前提是桶为「公开读」；生产更推荐返回对象名 + 预签名 URL
 */
@Slf4j
@Service
public class FileService {

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Resource
    private MinioClient minioClient;

    /** 启动时确保桶存在，不存在则创建 */
    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 桶初始化失败", e);
        }
    }

    /**
     * 上传图片到指定目录，返回可访问 URL
     *
     * @param file 图片文件
     * @param dir  对象目录，如 avatar
     */
    public String uploadImage(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        // 用 UUID 做对象名，保留原扩展名，避免重名覆盖
        String original = file.getOriginalFilename();
        String suffix = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf(".")) : "";
        String objectName = dir + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException("文件上传失败");
        }
        return endpoint + "/" + bucket + "/" + objectName;
    }
}
