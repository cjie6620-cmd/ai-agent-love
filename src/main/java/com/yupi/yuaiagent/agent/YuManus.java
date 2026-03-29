package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * YuManus 超级智能体。
 * 这是项目里开箱可用的“多步任务执行器”：能思考、能调用工具、能流式回传过程。
 */
@Component
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("yuManus");

        String SYSTEM_PROMPT = String.join("\n",
                "You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.",
                "You have various tools at your disposal that you can call upon to efficiently complete complex requests."
        );
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = String.join("\n",
                "Based on user needs, proactively select the most appropriate tool or combination of tools.",
                "For complex tasks, you can break down the problem and use different tools step by step to solve it.",
                "After using each tool, clearly explain the execution results and suggest the next steps.",
                "If you want to stop the interaction at any point, use the `terminate` tool/function call."
        );
        this.setNextStepPrompt(NEXT_STEP_PROMPT);

        // 给更复杂任务留更高步数上限。
        this.setMaxSteps(20);

        // 初始化模型客户端并挂上日志 Advisor，方便观察每轮思考。
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}