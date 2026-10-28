package com.example.kb.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求体。
 *
 * <p>限制单次最多 100 个，避免有人一次传几万个 ID 把 IN 查询撑爆。
 */
@Data
public class BatchIdsRequest {

    @NotEmpty(message = "不能为空")
    @Size(max = 100, message = "单次最多 100 条")
    private List<Long> ids;
}
