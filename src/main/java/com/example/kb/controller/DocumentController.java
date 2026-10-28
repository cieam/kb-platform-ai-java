package com.example.kb.controller;

import com.example.kb.common.result.PageResult;
import com.example.kb.common.result.R;
import com.example.kb.domain.vo.DocumentVO;
import com.example.kb.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "03 文档")
@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "上传文档，立即返回，后台异步解析")
    @PostMapping("/upload")
    public R<DocumentVO> upload(@RequestParam Long kbId,
                               @RequestParam("file") MultipartFile file) {
        return R.ok(documentService.upload(kbId, file));
    }

    @Operation(summary = "文档列表（分页）")
    @GetMapping("/page")
    public R<PageResult<DocumentVO>> page(@RequestParam Long kbId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return R.ok(documentService.page(kbId, page, size));
    }

    @Operation(summary = "文档详情，status 为 SUCCESS 时才算解析完成")
    @GetMapping("/{id}")
    public R<DocumentVO> detail(@PathVariable Long id) {
        return R.ok(documentService.get(id));
    }

    @Operation(summary = "重新解析（解析失败后不用重新上传）")
    @PostMapping("/{id}/reparse")
    public R<Void> reparse(@PathVariable Long id) {
        documentService.reparse(id);
        return R.ok();
    }

    @Operation(summary = "删除文档，同时清理向量")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return R.ok();
    }
}
