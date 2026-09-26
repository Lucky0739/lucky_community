package com.lucky.common.constant;

/**
 * 评论领域常量
 */
public class CommentConstant {

    /** 评论正文最大字符数 */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** 一级评论 */
    public static final int LEVEL_ROOT = 1;

    /** 二级评论（回复） */
    public static final int LEVEL_REPLY = 2;

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
     * <p>与 {@code OffsetPageQueryDTO} 默认 pageSize 对齐：{@code GET /post/{id}/view}
     * 和默认参数的 {@code GET /comment/page} 才能命中同一份首页缓存。
     */
    public static final int FIRST_PAGE_SIZE = 10;
}
