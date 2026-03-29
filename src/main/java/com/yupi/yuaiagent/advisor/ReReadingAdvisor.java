package com.yupi.yuaiagent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * ReReading Advisor（二次阅读增强）。
 * 它会把用户问题再强调一遍，让模型“重新看一次问题”，常用于提升模型聚焦度。
 */
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 在真正调用模型前，改写 Prompt。
     *
     * @param chatClientRequest 原始请求
     * @return 包含增强后用户消息的新请求
     */
    private ChatClientRequest before(ChatClientRequest chatClientRequest) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // 把原始问题放入上下文，方便后续调试或其他 Advisor 读取。
        chatClientRequest.context().put("re2_input_query", userText);

        // 在原问题后追加“再读一遍问题”的提示，让模型更聚焦用户真实意图。
        String newUserText = userText + "\nRead the question again: " + userText;

        Prompt newPrompt = chatClientRequest.prompt().augmentUserMessage(newUserText);
        return new ChatClientRequest(newPrompt, chatClientRequest.context());
    }

    /**
     * 同步调用链路。
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        return chain.nextCall(this.before(chatClientRequest));
    }

    /**
     * 流式调用链路。
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        return chain.nextStream(this.before(chatClientRequest));
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}