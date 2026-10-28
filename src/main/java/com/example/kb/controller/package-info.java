/**
 * HTTP 接口层。
 *
 * <p>只做参数绑定、校验注解、调用 Service、包装 R.ok()。唯一稍复杂的逻辑是 SSE 流式接口的事件推送。
 */
package com.example.kb.controller;
