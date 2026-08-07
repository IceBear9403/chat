package com.social.common.exception;

import com.social.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。业务代码抛出此异常后，由全局异常处理器统一转换为 Result 返回。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
