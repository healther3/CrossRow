package com.dyx.crossrow.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GetCurrentTimeTool {
    @Tool(
            name = "getCurrentTime",
            description = "Get the current date and time. Use this when the user asks about time-sensitive information, deadlines, or 'today's' date."
    )
    public String getCurrentTime() {
        try {
            // 1. 获取带有 UTC 偏移量的当前系统时间
            ZonedDateTime now = ZonedDateTime.now();

            // 2. 格式化输出 (例如: 2026-03-28 00:19:51 Saturday +0800)
            // 包含星期几对 Agent 理解“明天”、“周末”非常有帮助
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE Z");
            String formattedTime = now.format(formatter);

            log.info("Agent checked time: {}", formattedTime);

            // 3. 返回给 Agent
            return "Current System Time: " + formattedTime;

        } catch (Exception e) {
            log.error("Failed to fetch system time", e);
            return "Error: Unable to retrieve current time.";
        }
    }
}
