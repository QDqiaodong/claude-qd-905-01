package com.glass.works.common;

/**
 * 并发冲突异常：两名检验员几乎同时提交相反结论、或状态已被他人推进时抛出。
 * 统一异常处理会转成 HTTP 409 + 中文冲突说明，未生效的一方据此明确感知冲突。
 */
public class ConflictException extends BizException {

    public ConflictException(String message) {
        super(message);
    }
}
