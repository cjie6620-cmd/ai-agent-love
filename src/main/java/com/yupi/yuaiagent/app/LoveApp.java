package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 恋爱助手应用服务。
 * 这是项目里的“业务入口层”：封装了普通聊天、流式聊天、结构化输出、RAG、工具调用、MCP 调用等能力。
 */
@Component
@Slf4j
public class LoveApp {

    /**
     * 统一复用的 ChatClient。
     */
    private final ChatClient chatClient;

    /**
     * 系统提示词。
     * 用于设定模型的人设、沟通风格和业务范围。
     */
    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。"
            + "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；"
            + "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。"
            + "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    /**
     * 初始化 ChatClient。
     *
     * @param dashscopeChatModel 大模型实例
     */
    public LoveApp(ChatModel dashscopeChatModel) {
//        // 可选方案：基于文件持久化的聊天记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        // 当前默认方案：基于内存的窗口记忆（只保留最近 20 条消息）。
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 打开日志 Advisor，便于观察模型请求/响应。
                        new MyLoggerAdvisor()
//                        // 可选：开启 ReReading 增强推理。
//                        ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * 普通同步对话。
     *
     * @param message 用户消息
     * @param chatId  会话 id（用于多轮上下文）
     * @return 模型完整回答
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 流式对话。
     *
     * @param message 用户消息
     * @param chatId  会话 id
     * @return 按分片输出的文本流
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * 恋爱建议报告结构化输出。
     *
     * @param title       报告标题
     * @param suggestions 建议列表
     */
    record LoveReport(String title, List<String> suggestions) {

    }

    /**
     * 输出结构化恋爱报告。
     *
     * @param message 用户消息
     * @param chatId  会话 id
     * @return 结构化报告对象
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱建议报告，标题为《用户恋爱报告》，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    /**
     * 内存向量库（本地 SimpleVectorStore）。
     */
    @Resource
    private VectorStore loveAppVectorStore;

    /**
     * 云端 RAG Advisor（阿里云知识库服务）。
     */
    @Resource
    private Advisor loveAppRagCloudAdvisor;

    /**
     * PgVector 向量库。
     */
    @Resource
    private VectorStore pgVectorVectorStore;

    /**
     * 查询改写器。
     */
    @Resource
    private QueryRewriter queryRewriter;

    /**
     * RAG 对话。
     * 流程：用户问题 -> 查询改写 -> 向量检索增强 -> 大模型回答。
     *
     * @param message 用户消息
     * @param chatId  会话 id
     * @return 回答文本
     */
    public String doChatWithRag(String message, String chatId) {
        // 先做查询改写，提升检索命中率。
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 单独加日志，便于观察 RAG 效果。
                .advisors(new MyLoggerAdvisor())
                // 当前默认使用本地向量库问答增强。
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                // 可选方案：云端知识库增强
                // .advisors(loveAppRagCloudAdvisor)
                // 可选方案：PgVector 增强
                // .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 可选方案：自定义检索增强
                // .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(loveAppVectorStore, "单身"))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 项目中注册的工具集合。
     */
    @Resource
    private ToolCallback[] allTools;

    /**
     * 开启工具调用能力的对话。
     *
     * @param message 用户消息
     * @param chatId  会话 id
     * @return 回答文本
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * MCP 工具提供器（可对接外部 MCP 服务）。
     */
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * 开启 MCP 调用能力的对话。
     *
     * @param message 用户消息
     * @param chatId  会话 id
     * @return 回答文本
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
