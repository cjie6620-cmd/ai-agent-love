package com.yupi.yuaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 上下文查询增强器工厂。
 * 当 RAG 检索不到有效上下文时，这里定义兜底提示，避免模型胡乱回答。
 */
public class LoveAppContextualQueryAugmenterFactory {

    /**
     * 创建一个“严格要求有上下文”的增强器。
     *
     * @return ContextualQueryAugmenter 实例
     */
    public static ContextualQueryAugmenter createInstance() {
        String emptyContextPrompt = String.join("\n",
                "你应该输出下面的内容：",
                "抱歉，我只能回答恋爱相关的问题，别的没办法帮到您啦，",
                "有问题可以联系编程导航客服 https://codefather.cn"
        );
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate(emptyContextPrompt);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}