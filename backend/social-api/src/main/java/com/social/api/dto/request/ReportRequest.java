package com.social.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 举报动态请求（阶段七合规）。
 */
@Data
public class ReportRequest {

    @Size(max = 200, message = "举报理由最长 200 字")
    private String reason;
}
