package com.lucky.community.ai;

/**
 * tb_post 的三个 AI 管道状态列取值。与 {@code sql/post.sql} 的列注释一一对应。
 *
 * <p>三列互相独立：{@code embed_status} 与 {@code analysis_status} 各自成一条进度线，
 * 分析成功但向量写入降级，就是 {@code ANALYSIS_SUCCESS} + {@code EMBED_FAILED}。
 */
public final class PostAiStatus {

    private PostAiStatus() {
    }

    /** 帖子变更事件是否已投递给 AI 服务 */
    public static final int EVENT_PENDING = 0;
    public static final int EVENT_DELIVERED = 1;
    public static final int EVENT_FAILED = 2;

    /** 写入向量库的进度 */
    public static final int EMBED_PENDING = 0;
    public static final int EMBED_PROCESSING = 1;
    public static final int EMBED_SUCCESS = 2;
    public static final int EMBED_FAILED = 3;

    /** AI 内容分析（风险结论）的进度 */
    public static final int ANALYSIS_PENDING = 0;
    public static final int ANALYSIS_PROCESSING = 1;
    public static final int ANALYSIS_SUCCESS = 2;
    public static final int ANALYSIS_FAILED = 3;
}
