package com.lucky.core.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子表
 * <p>event_status / embed_status / analysis_status 是给 AI 治理链路预留的列。
 * 表只建一次，先占好位避免日后对大表做 ALTER。AI 的「结果」字段
 * (summary / risk_level / evidence 等)不放这里，属于 tb_post_analysis。
 */
@Data
@TableName("tb_post")
public class Post {

    /** 主键(雪花ID) */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属贴吧ID */
    private Long barId;

    /** 作者user_id */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /**
     * 封面图URL，默认取第一张配图；无图为 null。
     * <p>冗余在主表是为了列表分页只扫 tb_post，不再回查 tb_post_image。
     */
    private String coverImage;

    /**
     * 配图总张数，与 tb_post_image 行数同步；无图为 0。
     */
    private Integer imageCount;

    /**
     * 逻辑删除：0未删除，1已删除
     * <p>application.yaml 的 logic-delete-field 已按字段名匹配生效，本注解可省；
     * 加在这里是为了自文档化，并防止有人删掉 yaml 配置后静默退化成物理删除。
     * <p>生效后所有针对 Post 的 wrapper 查询/更新都会自动追加 AND deleted = 0
     */
    @TableLogic
    private Integer deleted;

    /** AI事件投递状态：0待投递，1已投递，2投递失败 */
    private Integer eventStatus;

    /** 向量化状态：0待处理，1处理中，2成功，3失败 */
    private Integer embedStatus;

    /** 内容分析状态：0待处理，1处理中，2成功，3失败 */
    private Integer analysisStatus;

    /** AI处理重试次数 */
    private Integer analysisRetryCount;

    /** AI分析完成时间 */
    private LocalDateTime analysisTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
