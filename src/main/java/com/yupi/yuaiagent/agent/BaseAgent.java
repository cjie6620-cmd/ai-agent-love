package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 智能体基类。
 * 这里封装了通用能力：状态管理、步骤循环、消息记忆、同步执行、SSE 流式执行。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    /**
     * 智能体名称（用于日志和识别）。
     */
    private String name;

    /**
     * 系统提示词：定义智能体角色和行为边界。
     */
    private String systemPrompt;

    /**
     * 下一步提示词：每一轮行动前给模型的“任务推进指令”。
     */
    private String nextStepPrompt;

    /**
     * 当前执行状态。
     */
    private AgentState state = AgentState.IDLE;

    /**
     * 当前步骤编号（从 1 开始），用于观察执行进度。
     */
    private int currentStep = 0;

    /**
     * 最大步骤数，防止智能体无限循环。
     */
    private int maxSteps = 10;

    /**
     * 大模型客户端。
     */
    private ChatClient chatClient;

    /**
     * 会话消息列表。
     * 这是智能体自己的“短期记忆”，每轮都会基于它继续推理。
     */
    private List<Message> messageList = new ArrayList<>();

    /**
     * 同步执行智能体任务。
     *
     * @param userPrompt 用户输入
     * @return 全部步骤执行结果（按行拼接）
     */
    public String run(String userPrompt) {
        // 1. 基础校验：避免在错误状态下启动任务。
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        // 2. 切换状态并写入首条用户消息。
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            // 3. 进入步骤循环，直到达到最大步数或任务主动结束。
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);

                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }

            // 4. 兜底终止：超过最大步数自动结束。
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误：" + e.getMessage();
        } finally {
            // 5. 无论成功失败，都执行清理。
            this.cleanup();
        }
    }

    /**
     * 流式执行智能体任务（SSE）。
     * 每完成一步就实时推送一次结果，前端可以边收边展示。
     *
     * @param userPrompt 用户输入
     * @return SSE 发射器
     */
    public SseEmitter runStream(String userPrompt) {
        // 超时时间设置为 5 分钟。
        SseEmitter sseEmitter = new SseEmitter(300000L);

        // 异步执行，避免阻塞 Web 请求线程。
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 基础校验。
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：当前状态不允许启动任务：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：用户提示词不能为空");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }

            // 2. 初始化运行状态。
            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));

            try {
                // 3. 按步骤执行并实时推送。
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);

                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    sseEmitter.send(result);
                }

                // 4. 超步数保护。
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }

                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });

        // 超时回调：连接超时就标记错误并清理。
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });

        // 完成回调：若仍是运行中，补设为已完成。
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return sseEmitter;
    }

    /**
     * 单步执行抽象方法。
     * 子类必须实现自己的“每一步要做什么”。
     *
     * @return 当前步骤执行结果
     */
    public abstract String step();

    /**
     * 资源清理钩子方法。
     * 子类如果有临时资源（文件句柄、连接等）可以覆写这里。
     */
    protected void cleanup() {
        // 默认不做处理。
    }
}
