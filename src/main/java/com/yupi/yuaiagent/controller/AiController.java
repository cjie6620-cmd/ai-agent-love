package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * AI 相关接口控制器。
 * 这个类主要做“路由分发”：接收 HTTP 请求，调用 LoveApp / YuManus，并把结果返回给前端。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    /**
     * 恋爱助手应用核心服务。
     */
    @Resource
    private LoveApp loveApp;

    /**
     * 项目中注册好的全部工具集合，给智能体按需调用。
     */
    @Resource
    private ToolCallback[] allTools;

    /**
     * 对话模型（这里注入的是 DashScope 对应的 ChatModel）。
     */
    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步对话接口。
     * 适合“发一次问一句”的普通请求。
     *
     * @param message 用户问题
     * @param chatId 会话 id，用于多轮上下文记忆
     * @return AI 完整回答
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * 基于 Flux 的流式对话接口（返回 text/event-stream）。
     * 适合前端边收边渲染打字机效果。
     *
     * @param message 用户问题
     * @param chatId 会话 id
     * @return 按片段输出的文本流
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * 基于 ServerSentEvent 包装的流式接口。
     * 相比直接返回字符串流，这种方式更标准，便于扩展事件字段。
     *
     * @param message 用户问题
     * @param chatId 会话 id
     * @return SSE 事件流
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 基于传统 SseEmitter 的流式接口。
     * 这里手动订阅 Flux，并把每个分片推送给前端，方便和传统 Spring MVC SSE 方式兼容。
     *
     * @param message 用户问题
     * @param chatId 会话 id
     * @return SseEmitter 对象
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        // 超时时间设置为 3 分钟，避免连接长期占用。
        SseEmitter sseEmitter = new SseEmitter(180000L);

        // 订阅流式结果并逐条推送给前端。
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 调用 YuManus 智能体（支持多步工具调用）。
     *
     * @param message 用户任务描述
     * @return 智能体执行过程的流式输出
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        return yuManus.runStream(message);
    }
}
