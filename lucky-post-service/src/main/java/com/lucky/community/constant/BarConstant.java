package com.lucky.community.constant;

/**
 * 贴吧列表分页。每页条数固定，不采用请求里的 pageSize，避免首页缓存按条数被打散。
 */
public final class BarConstant {

    private BarConstant() {
    }

    public static final int PAGE_SIZE = 10;
}
