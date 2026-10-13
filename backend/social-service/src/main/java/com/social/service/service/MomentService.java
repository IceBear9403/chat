package com.social.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.service.dto.FeedPageVO;
import com.social.service.dto.LikeVO;
import com.social.service.dto.MomentAuthorVO;
import com.social.service.dto.MomentCommentVO;
import com.social.service.dto.MomentDetailVO;
import com.social.service.dto.MomentMediaVO;
import com.social.service.dto.MomentVO;
import com.social.service.config.SensitiveWordFilter;
import com.social.service.entity.Moment;
import com.social.service.entity.MomentComment;
import com.social.service.entity.MomentLike;
import com.social.service.entity.MomentMedia;
import com.social.service.entity.MomentRepost;
import com.social.service.entity.UserProfile;
import com.social.service.entity.UserRelation;
import com.social.service.mapper.MomentCommentMapper;
import com.social.service.mapper.MomentLikeMapper;
import com.social.service.mapper.MomentMapper;
import com.social.service.mapper.MomentMediaMapper;
import com.social.service.mapper.MomentRepostMapper;
import com.social.service.mapper.UserProfileMapper;
import com.social.service.mapper.UserRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 朋友圈服务：发布、信息流、点赞、评论、转发、删除、详情。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentService {

    /** 信息流类型：关注流 / 推荐流 */
    public static final String FEED_FOLLOWING = "following";
    public static final String FEED_RECOMMEND = "recommend";

    private static final int MAX_MEDIA = 9;
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 30;

    private final MomentMapper momentMapper;
    private final MomentMediaMapper momentMediaMapper;
    private final MomentLikeMapper momentLikeMapper;
    private final MomentCommentMapper momentCommentMapper;
    private final MomentRepostMapper momentRepostMapper;
    private final UserRelationMapper userRelationMapper;
    private final UserProfileMapper userProfileMapper;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final ReportService reportService;

    // ==================== 发布 ====================

    @Transactional
    public MomentVO publish(Long userId, String content, Integer type, Integer visibility,
                            List<MomentMediaVO> mediaList, Long repostFrom) {
        int momentType = (type == null) ? Moment.TYPE_TEXT : type;
        int vis = (visibility == null) ? Moment.VISIBILITY_PUBLIC : visibility;
        if (vis < Moment.VISIBILITY_PUBLIC || vis > Moment.VISIBILITY_PRIVATE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "可见范围参数不正确");
        }

        if (momentType == Moment.TYPE_REPOST) {
            return repostInternal(userId, content, vis, repostFrom);
        }

        // 纯文字必须有内容
        String text = content == null ? "" : content.trim();
        if (momentType == Moment.TYPE_TEXT && text.isEmpty()) {
            throw new BusinessException(ErrorCode.MOMENT_CONTENT_EMPTY);
        }
        if (momentType == Moment.TYPE_IMAGE_TEXT && (mediaList == null || mediaList.isEmpty()) && text.isEmpty()) {
            throw new BusinessException(ErrorCode.MOMENT_CONTENT_EMPTY, "图文动态至少包含文字或一张图片");
        }
        if (mediaList != null && mediaList.size() > MAX_MEDIA) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最多上传 " + MAX_MEDIA + " 张图片");
        }

        Moment moment = new Moment();
        moment.setUserId(userId);
        moment.setContent(text);
        moment.setType(momentType);
        moment.setVisibility(vis);
        // 阶段七合规：敏感词命中 → 待审核（先发后审，仅作者可见，管理端放行/删除）
        moment.setStatus(sensitiveWordFilter.contains(text)
                ? Moment.STATUS_PENDING : Moment.STATUS_PUBLISHED);
        moment.setLikeCount(0);
        moment.setCommentCount(0);
        moment.setRepostCount(0);
        momentMapper.insert(moment);

        if (mediaList != null && !mediaList.isEmpty()) {
            int sort = 0;
            for (MomentMediaVO m : mediaList) {
                if (m.getUrl() == null || m.getUrl().isBlank()) {
                    continue;
                }
                MomentMedia media = new MomentMedia();
                media.setMomentId(moment.getId());
                media.setMediaType(m.getMediaType() == null ? MomentMedia.TYPE_IMAGE : m.getMediaType());
                media.setUrl(m.getUrl());
                media.setThumbnailUrl(m.getThumbnailUrl());
                media.setWidth(m.getWidth());
                media.setHeight(m.getHeight());
                media.setSort(sort++);
                momentMediaMapper.insert(media);
            }
        }
        return buildMomentVO(moment, userId, profilesOf(List.of(userId)), mediaOf(List.of(moment.getId())),
                Collections.emptySet(), Collections.emptyMap());
    }

    // ==================== 信息流 ====================

    public FeedPageVO feed(Long viewerId, String feedType, LocalDateTime cursorTime, Long cursorId, int size) {
        int pageSize = Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);

        List<Long> friendIds = friendIdsOf(viewerId);

        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Moment::getStatus, Moment.STATUS_PUBLISHED);

        // 可见范围过滤：公开 或 仅好友(作者是好友或自己) 或 私密(仅自己)
        wrapper.and(w -> {
            w.eq(Moment::getVisibility, Moment.VISIBILITY_PUBLIC);
            w.or().nested(w2 -> w2.eq(Moment::getVisibility, Moment.VISIBILITY_FRIENDS)
                    .eq(Moment::getUserId, viewerId));
            if (!friendIds.isEmpty()) {
                w.or().nested(w2 -> w2.eq(Moment::getVisibility, Moment.VISIBILITY_FRIENDS)
                        .in(Moment::getUserId, friendIds));
            }
            w.or().nested(w2 -> w2.eq(Moment::getVisibility, Moment.VISIBILITY_PRIVATE)
                    .eq(Moment::getUserId, viewerId));
        });

        if (FEED_FOLLOWING.equals(feedType)) {
            List<Long> followingIds = followingIdsOf(viewerId);
            List<Long> allowed = new ArrayList<>(followingIds);
            if (!allowed.contains(viewerId)) {
                allowed.add(viewerId);
            }
            wrapper.in(Moment::getUserId, allowed);
        }

        applyCursor(wrapper, cursorTime, cursorId, pageSize + 1);

        List<Moment> moments = momentMapper.selectList(wrapper);
        boolean hasMore = moments.size() > pageSize;
        List<Moment> page = hasMore ? moments.subList(0, pageSize) : moments;

        FeedPageVO vo = new FeedPageVO();
        vo.setRecords(assembleMoments(page, viewerId));
        vo.setHasMore(hasMore);
        if (hasMore && !page.isEmpty()) {
            Moment last = page.get(page.size() - 1);
            vo.setNextCursorTime(last.getCreatedAt());
            vo.setNextCursorId(last.getId());
        }
        return vo;
    }

    /** 我发布的动态（个人主页用）。含待审核/未通过，作者可看到自己的审核状态。 */
    public FeedPageVO myMoments(Long viewerId, LocalDateTime cursorTime, Long cursorId, int size) {
        int pageSize = Math.min(Math.max(size <= 0 ? DEFAULT_PAGE_SIZE : size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<Moment> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Moment::getStatus, Moment.STATUS_PUBLISHED, Moment.STATUS_PENDING, Moment.STATUS_REJECTED)
                .eq(Moment::getUserId, viewerId);
        applyCursor(wrapper, cursorTime, cursorId, pageSize + 1);
        List<Moment> moments = momentMapper.selectList(wrapper);
        boolean hasMore = moments.size() > pageSize;
        List<Moment> page = hasMore ? moments.subList(0, pageSize) : moments;

        FeedPageVO vo = new FeedPageVO();
        vo.setRecords(assembleMoments(page, viewerId));
        vo.setHasMore(hasMore);
        if (hasMore && !page.isEmpty()) {
            Moment last = page.get(page.size() - 1);
            vo.setNextCursorTime(last.getCreatedAt());
            vo.setNextCursorId(last.getId());
        }
        return vo;
    }

    // ==================== 详情 / 评论 ====================

    public MomentDetailVO detail(Long viewerId, Long momentId) {
        Moment moment = getViewableMoment(viewerId, momentId);
        MomentVO momentVO = buildMomentVO(moment, viewerId,
                profilesOf(List.of(moment.getUserId())),
                mediaOf(List.of(moment.getId())),
                likedIdsOf(viewerId, List.of(moment.getId())),
                originCacheOf(List.of(moment)));

        List<MomentComment> comments = momentCommentMapper.selectList(
                new LambdaQueryWrapper<MomentComment>()
                        .eq(MomentComment::getMomentId, momentId)
                        .eq(MomentComment::getStatus, 0)
                        .orderByAsc(MomentComment::getId));
        MomentDetailVO vo = new MomentDetailVO();
        vo.setMoment(momentVO);
        vo.setComments(assembleComments(comments));
        vo.setLikePreview(recentLikers(momentId, 5));
        return vo;
    }

    public List<MomentCommentVO> commentList(Long momentId) {
        List<MomentComment> comments = momentCommentMapper.selectList(
                new LambdaQueryWrapper<MomentComment>()
                        .eq(MomentComment::getMomentId, momentId)
                        .eq(MomentComment::getStatus, 0)
                        .orderByAsc(MomentComment::getId));
        return assembleComments(comments);
    }

    @Transactional
    public MomentCommentVO comment(Long viewerId, Long momentId, String content, Long parentId, Long replyTo) {
        getViewableMoment(viewerId, momentId);
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论内容不能为空");
        }
        if (content.trim().length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论最长 " + MAX_COMMENT_LENGTH + " 字");
        }
        // 阶段七合规：评论命中敏感词直接拦截
        if (sensitiveWordFilter.contains(content)) {
            throw new BusinessException(ErrorCode.MOMENT_AUDIT_FAILED, "评论包含敏感词，请修改后重试");
        }

        Long replyToUserId = replyTo;
        if (parentId != null) {
            MomentComment parent = momentCommentMapper.selectById(parentId);
            if (parent == null || !parent.getMomentId().equals(momentId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "被回复的评论不存在");
            }
            replyToUserId = (replyTo != null) ? replyTo : parent.getUserId();
        }

        MomentComment comment = new MomentComment();
        comment.setMomentId(momentId);
        comment.setUserId(viewerId);
        comment.setContent(content.trim());
        comment.setParentId(parentId);
        comment.setReplyTo(replyToUserId);
        comment.setStatus(0);
        momentCommentMapper.insert(comment);

        // 计数自增（不依赖当前读取值，避免并发覆盖）
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                .eq(Moment::getId, momentId)
                .setSql("comment_count = comment_count + 1"));

        return assembleComment(comment, profilesOf(
                List.of(viewerId, replyToUserId != null ? replyToUserId : viewerId)));
    }

    // ==================== 点赞 ====================

    @Transactional
    public LikeVO toggleLike(Long viewerId, Long momentId) {
        getViewableMoment(viewerId, momentId);

        Long count = momentLikeMapper.selectCount(new LambdaQueryWrapper<MomentLike>()
                .eq(MomentLike::getMomentId, momentId)
                .eq(MomentLike::getUserId, viewerId));
        if (count != null && count > 0) {
            // 取消点赞
            momentLikeMapper.delete(new LambdaQueryWrapper<MomentLike>()
                    .eq(MomentLike::getMomentId, momentId)
                    .eq(MomentLike::getUserId, viewerId));
            momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                    .eq(Moment::getId, momentId)
                    .setSql("like_count = like_count - 1"));
        } else {
            MomentLike like = new MomentLike();
            like.setMomentId(momentId);
            like.setUserId(viewerId);
            try {
                momentLikeMapper.insert(like);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发重复点赞：已存在则忽略
            }
            momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                    .eq(Moment::getId, momentId)
                    .setSql("like_count = like_count + 1"));
        }
        Moment moment = momentMapper.selectById(momentId);
        long likeCount = moment == null ? 0 : moment.getLikeCount();
        boolean liked = momentLikeMapper.selectCount(new LambdaQueryWrapper<MomentLike>()
                .eq(MomentLike::getMomentId, momentId)
                .eq(MomentLike::getUserId, viewerId)) > 0;
        return new LikeVO(liked, likeCount);
    }

    // ==================== 转发 ====================

    @Transactional
    public MomentVO repost(Long userId, Long momentId, String content, Integer visibility) {
        return repostInternal(userId, content, visibility, momentId);
    }

    private MomentVO repostInternal(Long userId, String content, int visibility, Long repostFrom) {
        if (repostFrom == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少被转发动态");
        }
        Moment original = getViewableMoment(userId, repostFrom);

        Long existing = momentRepostMapper.selectCount(new LambdaQueryWrapper<MomentRepost>()
                .eq(MomentRepost::getMomentId, repostFrom)
                .eq(MomentRepost::getUserId, userId));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "你已经转发过该动态");
        }

        MomentRepost repost = new MomentRepost();
        repost.setMomentId(repostFrom);
        repost.setUserId(userId);
        repost.setContent(content == null ? null : content.trim());
        momentRepostMapper.insert(repost);

        String repostText = content == null ? null : content.trim();
        Moment moment = new Moment();
        moment.setUserId(userId);
        moment.setContent(repostText);
        moment.setType(Moment.TYPE_REPOST);
        moment.setVisibility(visibility);
        moment.setRepostFrom(repostFrom);
        // 阶段七合规：转发语同样走敏感词审核
        moment.setStatus(sensitiveWordFilter.contains(repostText)
                ? Moment.STATUS_PENDING : Moment.STATUS_PUBLISHED);
        moment.setLikeCount(0);
        moment.setCommentCount(0);
        moment.setRepostCount(0);
        momentMapper.insert(moment);

        momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                .eq(Moment::getId, repostFrom)
                .setSql("repost_count = repost_count + 1"));

        Moment newMoment = momentMapper.selectById(moment.getId());
        return buildMomentVO(newMoment, userId, profilesOf(List.of(userId)),
                mediaOf(List.of(newMoment.getId())), Collections.emptySet(),
                originCacheOf(List.of(newMoment)));
    }

    // ==================== 删除 / 举报 ====================

    @Transactional
    public void delete(Long userId, Long momentId) {
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() == Moment.STATUS_DELETED) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_EXIST);
        }
        if (!moment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己发布的动态");
        }
        moment.setStatus(Moment.STATUS_DELETED);
        momentMapper.updateById(moment);
    }

    /** 举报动态（阶段七合规：落库受理，管理端处理）。 */
    public void report(Long userId, Long momentId, String reason) {
        getViewableMoment(userId, momentId);
        reportService.report(userId, momentId, reason);
    }

    // ==================== 装配 ====================

    private List<MomentVO> assembleMoments(List<Moment> moments, Long viewerId) {
        if (moments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> momentIds = moments.stream().map(Moment::getId).collect(Collectors.toList());
        Map<Long, List<MomentMedia>> mediaMap = mediaOf(momentIds);
        Set<Long> likedIds = likedIdsOf(viewerId, momentIds);
        Map<Long, Moment> originCache = originCacheOf(moments);
        // 作者 + 转发来源作者的资料
        Map<Long, UserProfile> profileMap = profilesOf(collectUserIds(moments, originCache));

        return moments.stream()
                .map(m -> buildMomentVO(m, viewerId, profileMap, mediaMap, likedIds, originCache))
                .collect(Collectors.toList());
    }

    private Set<Long> collectUserIds(List<Moment> moments, Map<Long, Moment> originCache) {
        Set<Long> ids = new HashSet<>();
        for (Moment m : moments) {
            ids.add(m.getUserId());
            if (m.getRepostFrom() != null && originCache.containsKey(m.getRepostFrom())) {
                ids.add(originCache.get(m.getRepostFrom()).getUserId());
            }
        }
        return ids;
    }

    private MomentVO buildMomentVO(Moment moment, Long viewerId, Map<Long, UserProfile> profileMap,
                                   Map<Long, List<MomentMedia>> mediaMap, Set<Long> likedIds,
                                   Map<Long, Moment> originCache) {
        MomentVO vo = new MomentVO();
        vo.setId(moment.getId());
        vo.setAuthor(authorOf(moment.getUserId(), profileMap.get(moment.getUserId())));
        vo.setType(moment.getType());
        vo.setContent(moment.getContent());
        vo.setVisibility(moment.getVisibility());
        vo.setStatus(moment.getStatus());
        vo.setLikeCount(moment.getLikeCount());
        vo.setCommentCount(moment.getCommentCount());
        vo.setRepostCount(moment.getRepostCount());
        vo.setLiked(viewerId != null && likedIds.contains(moment.getId()));
        vo.setCreatedAt(moment.getCreatedAt());

        List<MomentMedia> media = mediaMap.getOrDefault(moment.getId(), Collections.emptyList());
        vo.setMedia(media.stream().map(this::toMediaVO).collect(Collectors.toList()));

        if (moment.getType() == Moment.TYPE_REPOST && moment.getRepostFrom() != null) {
            Moment origin = originCache.get(moment.getRepostFrom());
            if (origin != null) {
                vo.setRepostFrom(buildOriginVO(origin, profileMap, mediaMap));
            }
        }
        return vo;
    }

    private MomentVO buildOriginVO(Moment origin, Map<Long, UserProfile> profileMap, Map<Long, List<MomentMedia>> mediaMap) {
        MomentVO vo = new MomentVO();
        vo.setId(origin.getId());
        vo.setAuthor(authorOf(origin.getUserId(), profileMap.get(origin.getUserId())));
        vo.setType(origin.getType());
        vo.setContent(origin.getContent());
        vo.setCreatedAt(origin.getCreatedAt());
        vo.setLikeCount(origin.getLikeCount());
        vo.setCommentCount(origin.getCommentCount());
        vo.setRepostCount(origin.getRepostCount());
        vo.setLiked(false);
        vo.setMedia(mediaMap.getOrDefault(origin.getId(), Collections.emptyList())
                .stream().map(this::toMediaVO).collect(Collectors.toList()));
        return vo;
    }

    private List<MomentCommentVO> assembleComments(List<MomentComment> comments) {
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        for (MomentComment c : comments) {
            userIds.add(c.getUserId());
            if (c.getReplyTo() != null) {
                userIds.add(c.getReplyTo());
            }
        }
        Map<Long, UserProfile> profileMap = profilesOf(userIds);
        return comments.stream().map(c -> assembleComment(c, profileMap)).collect(Collectors.toList());
    }

    private MomentCommentVO assembleComment(MomentComment c, Map<Long, UserProfile> profileMap) {
        MomentCommentVO vo = new MomentCommentVO();
        vo.setId(c.getId());
        vo.setMomentId(c.getMomentId());
        vo.setAuthor(authorOf(c.getUserId(), profileMap.get(c.getUserId())));
        vo.setContent(c.getContent());
        vo.setParentId(c.getParentId());
        if (c.getReplyTo() != null) {
            UserProfile target = profileMap.get(c.getReplyTo());
            vo.setReplyToName(target == null ? null : target.getNickname());
        }
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    private List<MomentAuthorVO> recentLikers(Long momentId, int limit) {
        List<MomentLike> likes = momentLikeMapper.selectList(
                new LambdaQueryWrapper<MomentLike>()
                        .eq(MomentLike::getMomentId, momentId)
                        .orderByDesc(MomentLike::getId)
                        .last("LIMIT " + limit));
        if (likes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserProfile> profileMap = profilesOf(likes.stream()
                .map(MomentLike::getUserId).collect(Collectors.toSet()));
        return likes.stream()
                .map(l -> authorOf(l.getUserId(), profileMap.get(l.getUserId())))
                .collect(Collectors.toList());
    }

    private MomentAuthorVO authorOf(Long userId, UserProfile profile) {
        MomentAuthorVO vo = new MomentAuthorVO();
        vo.setUserId(userId);
        if (profile != null) {
            vo.setNickname(profile.getNickname());
            vo.setAvatar(profile.getAvatar());
        }
        return vo;
    }

    private MomentMediaVO toMediaVO(MomentMedia media) {
        MomentMediaVO vo = new MomentMediaVO();
        vo.setId(media.getId());
        vo.setMediaType(media.getMediaType());
        vo.setUrl(media.getUrl());
        vo.setThumbnailUrl(media.getThumbnailUrl());
        vo.setWidth(media.getWidth());
        vo.setHeight(media.getHeight());
        vo.setSort(media.getSort());
        return vo;
    }

    // ==================== 数据访问辅助 ====================

    private Map<Long, List<MomentMedia>> mediaOf(List<Long> momentIds) {
        if (momentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MomentMedia> list = momentMediaMapper.selectList(
                new LambdaQueryWrapper<MomentMedia>()
                        .in(MomentMedia::getMomentId, momentIds)
                        .orderByAsc(MomentMedia::getSort));
        return list.stream().collect(Collectors.groupingBy(
                MomentMedia::getMomentId, LinkedHashMap::new, Collectors.toList()));
    }

    private Set<Long> likedIdsOf(Long viewerId, List<Long> momentIds) {
        if (viewerId == null || momentIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<MomentLike> likes = momentLikeMapper.selectList(
                new LambdaQueryWrapper<MomentLike>()
                        .eq(MomentLike::getUserId, viewerId)
                        .in(MomentLike::getMomentId, momentIds));
        return likes.stream().map(MomentLike::getMomentId).collect(Collectors.toSet());
    }

    /** 提取转发来源动态（已发布）及其媒体。 */
    private Map<Long, Moment> originCacheOf(List<Moment> moments) {
        List<Long> originIds = moments.stream()
                .filter(m -> m.getType() == Moment.TYPE_REPOST && m.getRepostFrom() != null)
                .map(Moment::getRepostFrom)
                .distinct()
                .collect(Collectors.toList());
        if (originIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Moment> origins = momentMapper.selectList(new LambdaQueryWrapper<Moment>()
                .in(Moment::getId, originIds)
                .eq(Moment::getStatus, Moment.STATUS_PUBLISHED));
        return origins.stream().collect(Collectors.toMap(Moment::getId, Function.identity()));
    }

    private Map<Long, UserProfile> profilesOf(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userProfileMapper.selectList(
                        new LambdaQueryWrapper<UserProfile>().in(UserProfile::getUserId, userIds))
                .stream().collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
    }

    private List<Long> followingIdsOf(Long userId) {
        return userRelationMapper.selectList(new LambdaQueryWrapper<UserRelation>()
                        .eq(UserRelation::getUserId, userId)
                        .eq(UserRelation::getRelationType, 2)
                        .eq(UserRelation::getStatus, 1))
                .stream().map(UserRelation::getTargetUserId).collect(Collectors.toList());
    }

    private List<Long> friendIdsOf(Long userId) {
        return userRelationMapper.selectList(new LambdaQueryWrapper<UserRelation>()
                        .eq(UserRelation::getRelationType, 1)
                        .eq(UserRelation::getStatus, 1)
                        .and(w -> w.eq(UserRelation::getUserId, userId)
                                .or()
                                .eq(UserRelation::getTargetUserId, userId)))
                .stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getUserId(), r.getTargetUserId()))
                .filter(id -> !id.equals(userId))
                .distinct()
                .collect(Collectors.toList());
    }

    private Moment getViewableMoment(Long viewerId, Long momentId) {
        Moment moment = momentMapper.selectById(momentId);
        if (moment == null || moment.getStatus() == Moment.STATUS_DELETED) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_EXIST);
        }
        boolean isOwner = viewerId != null && viewerId.equals(moment.getUserId());
        // 待审核/未通过的动态仅作者可见（"仅自己可见"），他人视为不存在
        if (moment.getStatus() != Moment.STATUS_PUBLISHED && !isOwner) {
            throw new BusinessException(ErrorCode.MOMENT_NOT_EXIST);
        }
        if (!canView(moment, viewerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该动态");
        }
        return moment;
    }

    private boolean canView(Moment moment, Long viewerId) {
        if (viewerId.equals(moment.getUserId())) {
            return true;
        }
        if (moment.getVisibility() == Moment.VISIBILITY_PUBLIC) {
            return true;
        }
        if (moment.getVisibility() == Moment.VISIBILITY_FRIENDS) {
            return isFriend(viewerId, moment.getUserId());
        }
        return false; // 私密仅作者可见
    }

    private boolean isFriend(Long a, Long b) {
        Long count = userRelationMapper.selectCount(new LambdaQueryWrapper<UserRelation>()
                .eq(UserRelation::getRelationType, 1)
                .eq(UserRelation::getStatus, 1)
                .and(w -> w.nested(w2 -> w2.eq(UserRelation::getUserId, a)
                                .eq(UserRelation::getTargetUserId, b))
                        .or()
                        .nested(w2 -> w2.eq(UserRelation::getUserId, b)
                                .eq(UserRelation::getTargetUserId, a))));
        return count != null && count > 0;
    }

    private void applyCursor(LambdaQueryWrapper<Moment> wrapper, LocalDateTime cursorTime,
                             Long cursorId, int limit) {
        if (cursorTime != null && cursorId != null) {
            wrapper.and(w -> w.lt(Moment::getCreatedAt, cursorTime)
                    .or()
                    .nested(w2 -> w2.eq(Moment::getCreatedAt, cursorTime)
                            .lt(Moment::getId, cursorId)));
        }
        wrapper.orderByDesc(Moment::getCreatedAt).orderByDesc(Moment::getId);
        wrapper.last("LIMIT " + limit);
    }
}
