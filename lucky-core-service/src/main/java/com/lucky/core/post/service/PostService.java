package com.lucky.core.post.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lucky.core.post.dto.CreatePostDTO;
import com.lucky.core.post.dto.PostFeedQueryDTO;
import com.lucky.core.post.dto.PostQueryDTO;
import com.lucky.core.post.dto.UpdatePostDTO;
import com.lucky.core.post.vo.PageVO;
import com.lucky.core.post.vo.PostDetailVO;
import com.lucky.core.post.vo.PostSummaryVO;
import com.lucky.core.post.vo.PostViewVO;

/**
 * 帖子：发帖 / 改帖 / 删帖 / 详情 / 列表
 */
public interface PostService {

    /**
     * 发帖。图片是两步式的第二步——先经 {@code POST /post/image} 预上传拿到直链，
     * 再由 {@code dto.getImages()} 带进来
     *
     * <p>图片地址会逐条反解出 objectName 并<b>按首次出现顺序去重</b>，所以
     * {@code dto.getImages()} 里重复提交同一个地址只会落一行，返回的
     * {@link PostDetailVO#getImages()} 可能比提交的短
     *
     * <p>本方法<b>不会</b>删除图片对象，写库失败也不删：图是上一个请求的产物，与本次事务
     * 没有原子性关系；同一个对象还可能已被多条 {@code tb_post_image} 引用，删了会连带弄坏别的帖子。
     * 代价是「传了图但没发帖」的孤儿文件，留给后续的对账任务回收
     *
     * @param userId 作者(当前登录用户)，不从请求体取
     * @param dto    帖子内容与图片直链列表，图片可为空（纯文字帖）
     * @return 帖子详情，含图片 URL，前端无需再查一次
     * @throws com.lucky.common.exception.BusinessException 贴吧不存在/已停用、图片超数量、图片地址不合法
     */
    PostDetailVO createPost(Long userId, CreatePostDTO dto);

    /**
     * 改帖。只能改自己的帖，不能换吧。配图按 {@code dto.getImages()} 全量替换。
     * 标题或正文变了会重置 AI 状态并在提交后重新送审。
     *
     * @throws com.lucky.common.exception.BusinessException 帖子不存在、不是作者、图片不合法
     */
    PostDetailVO updatePost(Long operatorId, Long postId, UpdatePostDTO dto);

    /**
     * 逻辑删帖。作者、本吧吧务或平台管理员可删。
     * 不删 MinIO 对象（可能被其他帖引用），评论/赞/藏留在库里，详情与列表都不再露出。
     *
     * @throws com.lucky.common.exception.BusinessException 帖子不存在或无权删除
     */
    void deletePost(Long operatorId, Long postId);

    /**
     * 查帖子详情
     *
     * @throws com.lucky.common.exception.BusinessException 帖子不存在或已被逻辑删除
     */
    PostDetailVO getDetail(Long postId);

    /**
     * 详情首屏：帖子（走 {@code post:detail} 缓存）+ 一级评论首页（走评论首页短缓存）。
     * 评论不写入帖子详情缓存。展开回复、继续翻页仍走评论接口。
     */
    PostViewVO getView(Long postId);

    /**
     * 分页查帖子列表
     * <p>作者已被删除时该行的 nickName/icon 为 null，不会让整个列表失败
     *
     * @return 分页结果；调用方取 {@code getRecords()} 作响应体、{@code getTotal()} 作总条数
     */
    IPage<PostSummaryVO> pagePosts(PostQueryDTO query);

    /**
     * 游标分页查帖子列表（<b>推荐入口</b>）
     *
     * <p>与 {@link #pagePosts} 的区别：用 {@code id < lastId} 代替 {@code LIMIT offset, size}，
     * 扫描行数恒定为 {@code size}，与翻到第几页无关。代价是<b>不能跳页</b>，
     * 只能一页页往前翻——对信息流场景完全够用。
     *
     * <p>首屏（{@code lastId == null}）走本机 Caffeine 缓存，新鲜度由
     * {@code post.cache.feed-ttl-seconds} 决定；翻页不走缓存。
     *
     * @return 本页数据与下一页游标；{@code nextCursor} 为 null 或下次返回空数组都表示已到底
     */
    PageVO<PostSummaryVO> feedPosts(PostFeedQueryDTO query);

    /**
     * 预热热点帖子缓存：把最新 limit 条帖子的详情灌进 L1 + L2
     * <p>幂等，可安全重复调用。用批量查询（4 条 SQL 覆盖 N 条帖子）而不是逐帖回源
     *
     * @param limit 预热条数，&lt;= 0 表示不预热
     * @return 实际预热的条数
     */
    int warmUpRecentPosts(int limit);
}
