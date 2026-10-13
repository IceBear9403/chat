package com.social.api.controller;

import com.social.api.dto.request.CommentRequest;
import com.social.api.dto.request.PublishMomentRequest;
import com.social.api.dto.request.ReportRequest;
import com.social.api.dto.request.RepostRequest;
import com.social.common.context.UserContext;
import com.social.common.enums.ErrorCode;
import com.social.common.exception.BusinessException;
import com.social.common.model.Result;
import com.social.service.dto.FeedPageVO;
import com.social.service.dto.LikeVO;
import com.social.service.dto.MomentCommentVO;
import com.social.service.dto.MomentDetailVO;
import com.social.service.dto.MomentVO;
import com.social.service.service.MomentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 朋友圈接口。需要登录。
 */
@Tag(name = "朋友圈", description = "发布 / 信息流 / 点赞 / 评论 / 转发")
@RestController
@RequestMapping("/api/moment")
@RequiredArgsConstructor
public class MomentController {

    private static final DateTimeFormatter SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MomentService momentService;

    @Operation(summary = "发布动态")
    @PostMapping("/publish")
    public Result<MomentVO> publish(@Valid @RequestBody PublishMomentRequest request) {
        return Result.ok(momentService.publish(UserContext.getUserId(), request.getContent(),
                request.getType(), request.getVisibility(), request.getMedia(), request.getRepostFrom()));
    }

    @Operation(summary = "信息流", description = "type=following 关注流 / recommend 推荐流，游标分页")
    @GetMapping("/feed")
    public Result<FeedPageVO> feed(@RequestParam(defaultValue = "following") String type,
                                   @RequestParam(required = false) String cursorTime,
                                   @RequestParam(required = false) Long cursorId,
                                   @RequestParam(defaultValue = "10") int size) {
        return Result.ok(momentService.feed(UserContext.getUserId(), type,
                parseCursorTime(cursorTime), cursorId, size));
    }

    @Operation(summary = "我的动态")
    @GetMapping("/my")
    public Result<FeedPageVO> my(@RequestParam(required = false) String cursorTime,
                                 @RequestParam(required = false) Long cursorId,
                                 @RequestParam(defaultValue = "10") int size) {
        return Result.ok(momentService.myMoments(UserContext.getUserId(),
                parseCursorTime(cursorTime), cursorId, size));
    }

    @Operation(summary = "动态详情（含评论与点赞预览）")
    @GetMapping("/{id}")
    public Result<MomentDetailVO> detail(@PathVariable Long id) {
        return Result.ok(momentService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "点赞 / 取消点赞（切换）")
    @PostMapping("/{id}/like")
    public Result<LikeVO> like(@PathVariable Long id) {
        return Result.ok(momentService.toggleLike(UserContext.getUserId(), id));
    }

    @Operation(summary = "发表评论（支持楼中楼）")
    @PostMapping("/{id}/comment")
    public Result<MomentCommentVO> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return Result.ok(momentService.comment(UserContext.getUserId(), id,
                request.getContent(), request.getParentId(), request.getReplyTo()));
    }

    @Operation(summary = "评论列表")
    @GetMapping("/{id}/comments")
    public Result<List<MomentCommentVO>> comments(@PathVariable Long id) {
        return Result.ok(momentService.commentList(id));
    }

    @Operation(summary = "转发动态")
    @PostMapping("/{id}/repost")
    public Result<MomentVO> repost(@PathVariable Long id, @Valid @RequestBody RepostRequest request) {
        return Result.ok(momentService.repost(UserContext.getUserId(), id,
                request.getContent(), request.getVisibility()));
    }

    @Operation(summary = "删除自己的动态")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        momentService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "举报动态")
    @PostMapping("/{id}/report")
    public Result<Void> report(@PathVariable Long id, @RequestBody(required = false) ReportRequest request) {
        momentService.report(UserContext.getUserId(), id,
                request == null ? null : request.getReason());
        return Result.ok();
    }

    /** 兼容后端返回的 "yyyy-MM-dd HH:mm:ss" 与 ISO 两种游标时间格式。 */
    private LocalDateTime parseCursorTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, SPACE_FORMAT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "游标时间格式不正确");
            }
        }
    }
}
