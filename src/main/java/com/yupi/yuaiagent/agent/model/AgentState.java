package com.yupi.yuaiagent.agent.model;

/**
 * 智能体执行状态枚举。
 * 可以把它理解成“工作流程进度条”：空闲 -> 运行中 -> 已结束 / 出错。
 * @author 33185
 */
public enum AgentState {

    /**
     * 空闲状态：还没开始执行任务。
     */
    IDLE,

    /**
     * 运行中：正在按步骤执行任务。
     */
    RUNNING,

    /**
     * 已完成：任务正常结束。
     */
    FINISHED,

    /**
     * 出错：执行过程中抛异常。
     */
    ERROR
}
