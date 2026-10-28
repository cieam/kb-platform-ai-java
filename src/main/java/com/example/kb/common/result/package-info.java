/**
 * 统一返回体与错误码。
 *
 * <p>全站所有接口都返回 R&lt;T&gt;，前端只判断 code，不解析 msg。分页统一用 PageResult，避免每个接口各写一套。
 */
package com.example.kb.common.result;
