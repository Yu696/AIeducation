package com.aijiaoxue.dto;

import lombok.Data;

/**
 * CSV 导入结果（US-05 数据导入）。
 */
@Data
public class ImportResult {

    /** 解析到的数据行数（不含表头） */
    private int total;
    /** 成功新增条数 */
    private int inserted;
    /** 成功更新条数 */
    private int updated;
    /** 跳过条数（编号重复且选择跳过，或必填缺失） */
    private int skipped;
    /** 明细消息，最多保留 50 条，便于定位问题 */
    private java.util.List<String> messages = new java.util.ArrayList<>();

    public void addMessage(String msg) {
        if (messages.size() < 50) {
            messages.add(msg);
        }
    }
}
