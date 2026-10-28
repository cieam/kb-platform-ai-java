package com.example.kb.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatResultVO {

    private Long conversationId;
    private String answer;
    private List<ReferenceVO> references = new ArrayList<>();
}
