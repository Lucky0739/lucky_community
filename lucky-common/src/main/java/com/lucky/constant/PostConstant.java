package com.lucky.constant;

/**
 * 帖子领域常量（公共模块，各服务 import 后直接使用）
 * <p>放在公共模块而不是 {@code PostServiceImpl} 里：这些值是发布校验、列表预览、缓存预热共用的口径，
 * 转载/搜索等旁路服务早晚要按同一套限制裁剪，散在实现类里会各写一份
 */
public class PostConstant {

    /** 单帖最多图片数，与 application.yaml 的 multipart.max-request-size 联动 */
    public static final int MAX_IMAGE_COUNT = 9;

    /** 列表页正文预览的最大码点数 */
    public static final int PREVIEW_CODE_POINTS = 100;

    /** 预热条数上限，防止配置写错把整张表灌进缓存 */
    public static final int MAX_WARM_UP_LIMIT = 500;
}
