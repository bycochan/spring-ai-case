package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    /**
     * 消息角色: user, assistant, system
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
}
