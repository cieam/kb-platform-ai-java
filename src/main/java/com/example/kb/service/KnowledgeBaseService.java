package com.example.kb.service;

import com.example.kb.common.result.PageResult;
import com.example.kb.domain.dto.KbSaveRequest;
import com.example.kb.domain.entity.KnowledgeBase;
import com.example.kb.domain.vo.KbVO;

public interface KnowledgeBaseService {

    KbVO create(KbSaveRequest request);

    KbVO update(KbSaveRequest request);

    void delete(Long id);

    /** 取出来并校验归属，越权直接抛 403 */
    KnowledgeBase getOwned(Long id);

    /** 不做权限校验，只给内部（异步线程里没有 UserContext）用 */
    KnowledgeBase getById(Long id);

    PageResult<KbVO> page(long page, long size);

    void increaseDocCount(Long kbId, int delta);
}
