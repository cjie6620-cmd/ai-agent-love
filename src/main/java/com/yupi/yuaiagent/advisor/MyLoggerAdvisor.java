package com.yupi.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 自定义日志 Advisor。
 * 作用很直接：请求发给模型前记一条日志，模型返回后再记一条日志，方便排查提示词和回答内容。
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * 返回 Advisor 名称，Spring AI 会用它做标识。
     *
     * @return 当前类名
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Advisor 执行顺序，数字越小越先执行。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 在请求发送给模型之前记录请求体。
     *
     * @param request 原始请求
     * @return 原样返回请求，不改业务数据
     */
    private ChatClientRequest before(ChatClientRequest request) {
        log.info("AI Request: {}", request.prompt());
        return request;
    }

    /**
     * 在请求完成后记录最终回答。
     *
     * @param chatClientResponse 模型响应
     */
    private void observeAfter(ChatClientResponse chatClientResponse) {
        log.info("AI Response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());
    }

    /**
     * 同步调用链路：前置记录日志 -> 继续执行 -> 后置记录日志。
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
        observeAfter(chatClientResponse);
        return chatClientResponse;
    }

    /**
     * 流式调用链路：
     * 1. 先记录请求；
     * 2. 执行流式响应；
     * 3. 聚合完整消息后统一记录最终回答。
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest = before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = chain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator()).aggregateChatClientResponse(chatClientResponseFlux, this::observeAfter);
    }
}
