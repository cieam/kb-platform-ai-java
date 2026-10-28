package com.example.kb.domain.vo;

import com.example.kb.domain.entity.KbDocument;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {

    private Long id;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private Long parseCostMs;
    private LocalDateTime createTime;

    public static DocumentVO from(KbDocument doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setParseCostMs(doc.getParseCostMs());
        vo.setCreateTime(doc.getCreateTime());
        return vo;
    }
}
