package com.lucky.common.file;

import com.lucky.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文件存储服务
 */
@Slf4j
public class FileService {

    /** 头像对象目录 */
    public static final String DIR_AVATAR = "avatar";

    /** 帖子图片对象目录 */
    public static final String DIR_POST = "post";

    /** 评论配图对象目录 */
    public static final String DIR_COMMENT = "comment";

    /** 贴吧头像对象目录 */
    public static final String DIR_BAR = "bar";

    /** 嗅探魔数需要读取的字节数，WebP 的 "WEBP" 标记落在第 8~11 字节 */
    private static final int MAGIC_LENGTH = 12;

    /** 桶策略里开放公开读的前缀。刻意不写 bucket/*，避免将来存进同一个桶的私有文件被静默公开 */
    private static final List<String> PUBLIC_READ_PREFIXES = List.of(DIR_AVATAR, DIR_POST, DIR_COMMENT, DIR_BAR);

    /** objectName 里 UUID 部分的形状：{@code UUID.toString()} 去掉横线后正好是 32 位小写十六进制 */
    private static final Pattern OBJECT_UUID = Pattern.compile("[0-9a-f]{32}");

    /** 上面这段 UUID 的字符数，用于反解时定位扩展名那个点的位置 */
    private static final int OBJECT_UUID_LENGTH = 32;

    @Value("${minio.bucket}")
    private String bucket;

    /** MinIO 对外地址。尾部斜杠已由 {@link #setEndpoint} 剥掉 */
    private String endpoint;

    private final MinioClient minioClient;

    public FileService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 用 setter 注入而不是字段注入：直链前缀是「生成」与「反解」共用的口径，
     */
    @Value("${minio.endpoint}")
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint.replaceAll("/+$", "");
    }

    /**
     * 上传成功后的结果
     */
    private record UploadedImage(String url, String objectName) {
    }

    /**
     * 允许的图片类型
     */
    private enum ImageType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png"),
        GIF("image/gif", ".gif"),
        WEBP("image/webp", ".webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    /**
     * 启动时确保桶存在并设置公开读策略
     */
    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            applyPublicReadPolicy();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 桶初始化失败", e);
        }
    }

    /**
     * 把 avatar/、post/、comment/、bar/ 设为公开读
     */
    private void applyPublicReadPolicy() throws Exception {
        String resourceList = PUBLIC_READ_PREFIXES.stream()
                .map(prefix -> "\"arn:aws:s3:::%s/%s/*\"".formatted(bucket, prefix))
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();
        String policy = """
                {"Version":"2012-10-17","Statement":[
                  {"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                   "Resource":[%s]}]}
                """.formatted(resourceList);
        // 注意 minio-java 8.x 里这个方法是 config(...) 不是 policy(...)
        minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
    }

    /**
     * 上传单张图片，返回可访问 URL
     */
    public String uploadImage(MultipartFile file, String dir) {
        return doUpload(file, dir).url();
    }

    /**
     * objectName → 对外直链。反函数是 {@link #resolveObjectName(String, String)}
     */
    public String toPublicUrl(String objectName) {
        return publicBaseUrl() + objectName;
    }

    /**
     * 把本服务生成的帖子图片直链反解成 objectName，反解失败返回 {@code null}
     *
     * @see #resolveObjectName(String, String)
     */
    public String resolvePostObjectName(String url) {
        return resolveObjectName(url, DIR_POST);
    }

    /**
     * 把本服务生成的评论配图直链反解成 objectName，反解失败返回 {@code null}
     *
     * @see #resolveObjectName(String, String)
     */
    public String resolveCommentObjectName(String url) {
        return resolveObjectName(url, DIR_COMMENT);
    }

    /**
     * 把本服务生成的贴吧头像直链反解成 objectName，反解失败返回 {@code null}
     */
    public String resolveBarObjectName(String url) {
        return resolveObjectName(url, DIR_BAR);
    }

    /**
     * 把本服务生成的直链反解成指定目录下的 objectName，反解失败返回 {@code null}
     */
    public String resolveObjectName(String url, String dir) {
        if (url == null || url.isBlank() || dir == null || dir.isBlank()) {
            return null;
        }
        // 前缀逐字节比对：既挡外链注入，也挡协议/主机被手改过大小写的串
        String prefix = publicBaseUrl();
        if (!url.startsWith(prefix)) {
            return null;
        }
        String dirPrefix = dir + "/";
        String objectName = url.substring(prefix.length());
        if (!objectName.startsWith(dirPrefix)) {
            return null;
        }
        String fileName = objectName.substring(dirPrefix.length());
        int dot = fileName.lastIndexOf('.');
        if (dot != OBJECT_UUID_LENGTH) {
            return null;
        }
        if (!OBJECT_UUID.matcher(fileName.substring(0, dot)).matches()) {
            return null;
        }
        return imageTypeByExtension(fileName.substring(dot)) == null ? null : objectName;
    }

    /**
     * 直链前缀。<b>生成与反解必须共用本方法</b>——两处各写一遍
     */
    private String publicBaseUrl() {
        return endpoint + "/" + bucket + "/";
    }

    /**
     * 尽力删除对象：只记日志
     */
    public void deleteQuietly(List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        for (String objectName : objectNames) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            } catch (Exception e) {
                log.error("清理 MinIO 对象失败，可能产生孤儿文件，需人工处理，objectName={}", objectName, e);
            }
        }
    }

    private UploadedImage doUpload(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        ImageType type = sniff(readHead(file));
        if (type == null) {
            throw new BusinessException("仅支持 JPG/PNG/GIF/WebP 格式的图片");
        }
        String objectName = dir + "/" + UUID.randomUUID().toString().replace("-", "") + type.extension;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(type.contentType)
                    .build());
        } catch (Exception e) {
            log.error("文件上传失败，objectName={}", objectName, e);
            throw new BusinessException("图片上传失败");
        }
        return new UploadedImage(toPublicUrl(objectName), objectName);
    }

    /** 读取文件头用于魔数嗅探；每次调用都开新流，不影响后续上传时的读取 */
    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(MAGIC_LENGTH);
        } catch (IOException e) {
            throw new BusinessException("图片读取失败");
        }
    }

    /**
     * 按扩展名认类型，认不出返回 null
     */
    private ImageType imageTypeByExtension(String extension) {
        for (ImageType type : ImageType.values()) {
            if (type.extension.equals(extension)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 按魔数识别图片类型，识别不出返回 null
     */
    private ImageType sniff(byte[] head) {
        if (head.length >= 3
                && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return ImageType.PNG;
        }
        if (head.length >= 6
                && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return ImageType.GIF;
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }
}
