package com.lucky.common.constant;

/**
 * 评论领域常量
 */
public class CommentConstant {

    /** 评论正文最大字符数 */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** 无父评论 / 无被回复用户 等占位值 */
    public static final long NONE = 0L;

    /** 未置顶 */
    public static final int NOT_TOP = 0;

    /** 置顶 */
    public static final int TOP = 1;

    /** 有正文 */
    public static final int CONTENT_NOT_EMPTY = 0;

    /** 无正文（纯图片评） */
    public static final int CONTENT_EMPTY = 1;

    /**
     * 详情首屏 / 评论首页缓存的固定页大小
     */
    public static final int FIRST_PAGE_SIZE = 10;
}
