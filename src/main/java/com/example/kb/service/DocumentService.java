package com.example.kb.service;

import com.example.kb.common.result.PageResult;
import com.example.kb.domain.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    /** 上传并落库，立即返回；解析和向量化在后台线程做 */
    DocumentVO upload(Long kbId, MultipartFile file);

    /** 真正的解析流水线，供异步线程调用 */
    void process(Long docId);

    /** 重新解析：解析失败（换模型、改切分参数、补 OCR）后不用重新上传 */
    void reparse(Long id);

    DocumentVO get(Long id);

    PageResult<DocumentVO> page(Long kbId, long page, long size);

    void delete(Long id);
}
