package com.yupi.yuaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于本地文件的聊天记忆实现。
 * 简单理解：每个 conversationId 对应一个 .kryo 文件，把消息列表序列化保存到磁盘。
 */
public class FileBasedChatMemory implements ChatMemory {

    /**
     * 聊天记录根目录。
     */
    private final String BASE_DIR;

    /**
     * Kryo 序列化器。
     * 这里做成静态单例，避免频繁创建对象。
     */
    private static final Kryo kryo = new Kryo();

    static {
        // 关闭强制注册，省去给每个类型显式注册的工作量。
        kryo.setRegistrationRequired(false);

        // 允许 Kryo 在没有无参构造时也能创建对象实例。
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * 构造函数：初始化存储目录。
     *
     * @param dir 文件保存目录
     */
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    /**
     * 追加消息到会话。
     *
     * @param conversationId 会话 id
     * @param messages       新增消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    /**
     * 获取某个会话的全部消息。
     *
     * @param conversationId 会话 id
     * @return 消息列表，不存在则返回空列表
     */
    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    /**
     * 清空某个会话记录（删除对应文件）。
     *
     * @param conversationId 会话 id
     */
    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 读取会话记录；如果文件不存在则返回空列表。
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                // 这里沿用最简单的错误输出方式，便于快速定位 IO 问题。
                e.printStackTrace();
            }
        }
        return messages;
    }

    /**
     * 把会话消息写回磁盘。
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 按会话 id 生成对应的存储文件路径。
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
