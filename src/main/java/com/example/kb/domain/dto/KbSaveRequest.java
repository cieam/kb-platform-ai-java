package com.example.kb.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KbSaveRequest {

    /** 新增时不传，修改时必传 */
    private Long id;

    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "不能超过 128 个字符")
    private String name;

    @Size(max = 512, message = "不能超过 512 个字符")
    private String description;

    private String scope;

    private Integer chunkSize;

    private Integer chunkOverlap;
}
