package com.lucky.core.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 帖子AI分析结果表
 * <p><b>刻意不加 deleted 字段</b>：本表是「一个帖子一条结论」，重跑直接覆盖，不需要逻辑删除。
 * 更要紧的是 mybatis-plus 的 logic-delete-field 按「字段名」全局匹配，
 * 给没有该列的表加同名字段会让它的查询带上 {@code AND deleted=0} 并报 Unknown column
 * （与 {@link com.lucky.core.bar.entity.Bar} 同一类问题，见 post.sql 里 tb_bar 处的警告）。
 *
 * <p>{@code postId} 上有唯一键 {@code uk_post_id}，承担重跑幂等：
 * 同一个帖子重复分析走 ON CONFLICT (post_id) DO UPDATE，不会留下两条互相矛盾的结论。
 */
@Data
@TableName("tb_post_analysis")
public class PostAnalysis {

    /** 主键(雪花ID) */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 帖子ID，唯一键 */
    private Long postId;

    /** 风险等级：无风险/低/中/高 */
    private String riskLevel;

    /** 风险分类，逗号分隔 */
    private String riskCategories;

    /** 置信度 0~1 */
    private BigDecimal confidence;

    /** 原文引用(JSON 数组)，留档用于事后核验 */
    private String evidenceJson;

    /** 判定理由 */
    private String reasoning;

    /** 是否触发冲突(本阶段恒为0) */
    private Integer hasConflict;

    /** 冲突类型(本阶段恒为NULL) */
    private String conflictType;

    /** 回溯复核次数 */
    private Integer retryCount;

    /** 吧务最终决定，自动化评测的对照基准；本期无处置入口，先留空 */
    private String handlerAction;

    /** 模型版本 */
    private String modelVersion;

    /** 端到端耗时(毫秒) */
    private Integer latencyMs;

    /** token 消耗 */
    private Integer tokenCost;

    /** 创建时间 */
    private LocalDateTime createTime;
}
