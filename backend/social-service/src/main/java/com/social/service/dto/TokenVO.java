package com.social.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌返回体。accessExpiresIn 为 access token 剩余有效期（秒）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {

    private String accessToken;
    private String refreshToken;
    private long accessExpiresIn;
}
