package com.social.api.controller;

import com.social.common.context.UserContext;
import com.social.common.model.Result;
import com.social.service.dto.AuditMomentVO;
import com.social.service.dto.PageVO;
import com.social.service.dto.ReportAdminVO;
import com.social.service.dto.UserAdminVO;
import com.social.service.dto.UserLogVO;
import com.social.service.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口（阶段七：用户封禁、内容审核、举报处理、操作日志）。
 * 管理员身份由 AdminService.requireAdmin 校验（is_admin=1），
 * 需走 /api/** 拦截器（除 /api/auth/** 外），从 UserContext 取当前用户。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 用户列表（关键字：手机号 / 昵称，状态筛选）。 */
    @GetMapping("/users")
    public Result<PageVO<UserAdminVO>> listUsers(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listUsers(UserContext.getUserId(), keyword, status, page, size));
    }

    /** 封禁（status=1）/ 解封（status=0）。 */
    @PutMapping("/users/{userId}/status")
    public Result<Void> setUserStatus(@PathVariable Long userId, @RequestParam Integer status) {
        adminService.setUserStatus(UserContext.getUserId(), userId, status);
        return Result.ok();
    }

    /** 管理员修改任意用户昵称（不受昵称修改间隔限制）。 */
    @PutMapping("/users/{userId}/nickname")
    public Result<Void> setUserNickname(@PathVariable Long userId, @RequestParam String nickname) {
        adminService.setUserNickname(UserContext.getUserId(), userId, nickname);
        return Result.ok();
    }

    /** 内容审核队列（待审核动态）。 */
    @GetMapping("/audits")
    public Result<PageVO<AuditMomentVO>> listAudits(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listAudits(UserContext.getUserId(), page, size));
    }

    /** 审核动态：approve / reject。 */
    @PostMapping("/audits/{momentId}/{action}")
    public Result<Void> auditMoment(@PathVariable Long momentId, @PathVariable String action) {
        adminService.auditMoment(UserContext.getUserId(), momentId, action);
        return Result.ok();
    }

    /** 举报队列（未处理）。 */
    @GetMapping("/reports")
    public Result<PageVO<ReportAdminVO>> listReports(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listReports(UserContext.getUserId(), page, size));
    }

    /** 处理举报：delete（删除动态）/ ignore（忽略）。 */
    @PostMapping("/reports/{reportId}/{action}")
    public Result<Void> handleReport(@PathVariable Long reportId, @PathVariable String action) {
        adminService.handleReport(UserContext.getUserId(), reportId, action);
        return Result.ok();
    }

    /** 操作日志。 */
    @GetMapping("/logs")
    public Result<PageVO<UserLogVO>> listLogs(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listLogs(UserContext.getUserId(), page, size));
    }
}
