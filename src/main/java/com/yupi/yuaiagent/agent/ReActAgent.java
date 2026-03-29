package com.yupi.yuaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct（Reasoning + Acting）抽象智能体。
 * 核心思想很朴素：先“想”（think），再“做”（act），循环推进直到任务结束。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考阶段：分析当前上下文并决定要不要执行动作。
     *
     * @return true 表示需要执行 act，false 表示本轮不需要动作
     */
    public abstract boolean think();

    /**
     * 执行动作阶段：通常是调用工具、生成结果或推进任务。
     *
     * @return 动作执行结果文本
     */
    public abstract String act();

    /**
     * 单步执行逻辑：先思考，再行动。
     * 这就是 ReAct 的基本循环单元。
     *
     * @return 当前步骤结果
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                return "思考完成 - 无需行动";
            }
            return act();
        } catch (Exception e) {
            // 这里保留堆栈，便于快速定位异常现场。
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

}
