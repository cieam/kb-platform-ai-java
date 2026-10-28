package com.example.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kb.common.exception.BizException;
import com.example.kb.common.result.PageResult;
import com.example.kb.common.result.ResultCode;
import com.example.kb.common.util.UserContext;
import com.example.kb.domain.dto.KbSaveRequest;
import com.example.kb.domain.entity.KnowledgeBase;
import com.example.kb.domain.vo.KbVO;
import com.example.kb.mapper.KnowledgeBaseMapper;
import com.example.kb.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public KbVO create(KbSaveRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setOwnerId(UserContext.getUserId());
        if (request.getScope() != null && !request.getScope().isBlank()) {
            kb.setScope(request.getScope());
        }
        if (request.getChunkSize() != null) {
            kb.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            kb.setChunkOverlap(request.getChunkOverlap());
        }
        kb.setDocCount(0);
        knowledgeBaseMapper.insert(kb);
        return KbVO.from(knowledgeBaseMapper.selectById(kb.getId()));
    }

    @Override
    public KbVO update(KbSaveRequest request) {
        KnowledgeBase kb = getOwned(request.getId());
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        if (request.getScope() != null && !request.getScope().isBlank()) {
            kb.setScope(request.getScope());
        }
        if (request.getChunkSize() != null) {
            kb.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            kb.setChunkOverlap(request.getChunkOverlap());
        }
        knowledgeBaseMapper.updateById(kb);
        return KbVO.from(knowledgeBaseMapper.selectById(kb.getId()));
    }

    @Override
    public void delete(Long id) {
        getOwned(id);
        // 逻辑删除：数据保留，向量库里对应的切片需要另外清理（见 README 的待办）
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    public KnowledgeBase getOwned(Long id) {
        KnowledgeBase kb = getById(id);
        if (!kb.getOwnerId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return kb;
    }

    @Override
    public KnowledgeBase getById(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "知识库 ID 不能为空");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BizException(ResultCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    @Override
    public PageResult<KbVO> page(long page, long size) {
        Page<KnowledgeBase> query = new Page<>(page, size);
        Page<KnowledgeBase> result = knowledgeBaseMapper.selectPage(query,
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getOwnerId, UserContext.getUserId())
                        .orderByDesc(KnowledgeBase::getId));
        List<KbVO> list = result.getRecords().stream().map(KbVO::from).collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public void increaseDocCount(Long kbId, int delta) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            return;
        }
        int current = kb.getDocCount() == null ? 0 : kb.getDocCount();
        kb.setDocCount(Math.max(0, current + delta));
        knowledgeBaseMapper.updateById(kb);
    }
}
