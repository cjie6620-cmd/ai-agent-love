package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 任务终止工具。
 * 智能体在任务完成或无法继续时可主动调用它，显式结束执行链路。
 */
public class TerminateTool {

    /**
     * 终止当前任务。
     *
     * @return 终止提示文本
     */
    @Tool(description = "Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task. When you have finished all the tasks, call this tool to end the work.")
    public String doTerminate() {
        return "任务结束";
    }
}