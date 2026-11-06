package com.tes.springai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /**
     * 附件 fileId 列表（仅用户消息可能有，历史回显用）
     */
    private List<String> fileIds;
}
