package com.yupi.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具调用型智能体。
 * 这是一个可直接使用的 ReAct 实现：
 * 1. think 阶段判断是否需要调用工具；
 * 2. act 阶段执行工具并处理返回结果。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    /**
     * 可用工具列表。
     */
    private final ToolCallback[] availableTools;

    /**
     * think 阶段得到的模型响应（里面可能包含工具调用计划）。
     */
    private ChatResponse toolCallChatResponse;

    /**
     * Spring AI 工具执行管理器，负责把工具调用真正落地执行。
     */
    private final ToolCallingManager toolCallingManager;

    /**
     * 聊天参数配置。
     * 这里显式关闭内部工具执行，改为我们自己控制调用节奏。
     */
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 思考阶段：
     * 1. 组装上下文；
     * 2. 调模型判断是否要调用工具；
     * 3. 记录工具调用计划。
     *
     * @return true 需要进入 act 执行工具，false 表示无需调用工具
     */
    @Override
    public boolean think() {
        // 如果配置了“下一步提示词”，每轮都追加一条用户消息引导模型继续推进任务。
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            // 保存本轮响应，供 act 阶段读取工具调用信息。
            this.toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            log.info("{} 的思考结果：{}", getName(), assistantMessage.getText());
            log.info("{} 本轮选择了 {} 个工具", getName(), toolCallList.size());

            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            if (StrUtil.isNotBlank(toolCallInfo)) {
                log.info(toolCallInfo);
            }

            // 如果没有工具调用，直接把助手消息放进上下文，后续流程可直接收尾。
            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }

            // 如果有工具调用，工具执行后会自动补全上下文，这里不重复写入。
            return true;
        } catch (Exception e) {
            log.error("{} 在思考阶段出现问题：{}", getName(), e.getMessage(), e);
            getMessageList().add(new AssistantMessage("处理时遇到错误：" + e.getMessage()));
            return false;
        }
    }

    /**
     * 行动阶段：真正执行工具调用并处理返回结果。
     *
     * @return 工具执行结果文本
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        // 同步最新会话历史（包含助手工具调用消息 + 工具返回消息）。
        setMessageList(toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 如果调用了终止工具，直接把状态置为完成。
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }
}
