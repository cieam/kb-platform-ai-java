package com.example.kb.controller;

import com.example.kb.common.result.PageResult;
import com.example.kb.common.result.R;
import com.example.kb.domain.dto.KbSaveRequest;
import com.example.kb.domain.entity.KnowledgeBase;
import com.example.kb.domain.vo.KbVO;
import com.example.kb.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02 知识库")
@RestController
@RequestMapping("/api/kb")
public class KbController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KbController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "新建知识库")
    @PostMapping
    public R<KbVO> create(@RequestBody @Valid KbSaveRequest request) {
        return R.ok(knowledgeBaseService.create(request));
    }

    @Operation(summary = "修改知识库")
    @PutMapping
    public R<KbVO> update(@RequestBody @Valid KbSaveRequest request) {
        return R.ok(knowledgeBaseService.update(request));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return R.ok();
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public R<KnowledgeBase> detail(@PathVariable Long id) {
        return R.ok(knowledgeBaseService.getOwned(id));
    }

    @Operation(summary = "我的知识库（分页）")
    @GetMapping("/page")
    public R<PageResult<KbVO>> page(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        return R.ok(knowledgeBaseService.page(page, size));
    }
}
