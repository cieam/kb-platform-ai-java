/**
 * 统一异常处理。
 *
 * <p>业务代码只管抛 BizException，由 GlobalExceptionHandler 翻译成统一返回体；未知异常对外只回「系统繁忙」，堆栈只进日志，不泄露给前端。
 */
package com.example.kb.common.exception;
