package com.lucky.core.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子图片表(一帖多图)
 * <p>删帖 / 改配图走逻辑删除：行留下，{@code object_name} 仍可用于孤儿对账。
 * 查图路径由 {@code @TableLogic} 自动追加 {@code AND deleted=0}。
 */
@Data
@TableName("tb_post_image")
public class PostImage {

    /** 主键(雪花ID) */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 帖子ID */
    private Long postId;

    /** 图片访问URL(直链) */
    private String url;

    /**
     * MinIO对象名，形如 {@code post/<32位hex>.<扩展名>}
     * <p>图片改为预上传后，发帖失败不再补偿删除（图可能已被别的帖子引用），所以这个字段
     * 唯一的用途是<b>将来的孤儿对账</b>：拿 {@code listObjects("post/")} 与
     * {@code select object_name from tb_post_image} 做差集，差出来的就是可以回收的孤儿
     * <p>注意同一个 objectName 可能出现在<b>多行</b>里（用户重试发帖、跨帖复用），
     * 所以任何「删帖即删图」的写法都是错的，必须先做引用计数
     */
    private String objectName;

    /** 展示顺序，从0开始 */
    private Integer sortNo;

    /**
     * 逻辑删除：0未删除，1已删除
     * <p>application.yaml 的 logic-delete-field 已按字段名匹配生效；
     * 加注解是为了自文档化，并防止有人删掉 yaml 后静默退化成物理删除。
     */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;
}
