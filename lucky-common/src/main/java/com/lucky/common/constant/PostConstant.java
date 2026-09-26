package com.lucky.common.constant;

/**
 * 帖子领域常量
 */
public class PostConstant {

    /** 单帖最多图片数 */
    public static final int MAX_IMAGE_COUNT = 9;

    /** 列表页正文预览的最大码点数 */
    public static final int PREVIEW_CODE_POINTS = 100;

    /** 预热条数上限，防止配置写错把整张表灌进缓存 */
    public static final int MAX_WARM_UP_LIMIT = 500;
}
