package com.lucky.comment.service;

import com.lucky.comment.dto.FindCommentPageListDTO;
import com.lucky.comment.dto.FindCommentReplyPageListDTO;
import com.lucky.comment.dto.FindUserCommentPageDTO;
import com.lucky.comment.dto.PublishCommentDTO;
import com.lucky.comment.entity.Comment;
import com.lucky.comment.mq.message.DeleteCommentMessage;
import com.lucky.comment.mq.message.PublishCommentMessage;
import com.lucky.comment.vo.FindCommentPageListVO;
import com.lucky.comment.vo.UserCommentItemVO;
import com.lucky.common.vo.PageResult;

import java.util.List;

/**
 * 评论：查询、发布、删除、点赞，以及消费端落库和热度。
 */
public interface CommentService {

    PageResult<FindCommentPageListVO> findCommentPageList(FindCommentPageListDTO findCommentPageListDTO);

    PageResult<FindCommentPageListVO> findCommentPageList(FindCommentPageListDTO findCommentPageListDTO,
                                                            Long viewerUserId);

    PageResult<FindCommentPageListVO> findReplyPageList(FindCommentReplyPageListDTO dto, Long viewerUserId);

    /** 用户发表的评论/回贴，按时间倒序 */
    PageResult<UserCommentItemVO> findUserCommentPage(FindUserCommentPageDTO dto, Long viewerUserId);

    /** 生成评论 ID 后发消息。接口马上返回这个 ID，正文由消费端写入数据库。 */
    Long publish(PublishCommentDTO publishCommentDTO);

    /** 校验存在性和归属后投递删除消息。 */
    void delete(Long userId, Long commentId);

    /**
     * 消费者侧真正删除：软删、级联、清点赞、改计数，整条一个事务。
     * 返回需要在事务提交后删掉的 Cassandra 正文。
     */
    List<Comment> handleDelete(DeleteCommentMessage message);

    /** 删 Cassandra 正文。在 {@link #handleDelete} 的事务提交后调用。 */
    void deleteContents(List<Comment> comments);

    void like(Long userId, Long commentId);

    void unlike(Long userId, Long commentId);

    /**
     * 一批写入。返回这次真正新插入的评论。主键冲突的评论不会出现在返回值里。
     */
    List<PublishCommentMessage> saveBatch(List<PublishCommentMessage> messages);

    /** 新评论写入成功后给所属贴吧加热度。失败只记日志。 */
    void addHeat(List<PublishCommentMessage> freshComments);
}
