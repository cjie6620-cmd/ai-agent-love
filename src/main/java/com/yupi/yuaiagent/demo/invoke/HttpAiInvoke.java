package com.yupi.yuaiagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 使用原生 HTTP 请求调用 AI 接口的示例。
 * 适合学习最底层调用流程：拼参数 -> 发请求 -> 看返回。
 */
public class HttpAiInvoke {

    public static void main(String[] args) {
        // API 密钥。
        String apiKey = TestApiKey.API_KEY;

        // DashScope 文本生成接口地址。
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // 组装 input.messages。
        JSONObject inputJson = new JSONObject();
        JSONObject messagesJson = new JSONObject();

        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", "You are a helpful assistant.");

        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", "你是谁？");

        messagesJson.set("messages", JSONUtil.createArray().set(systemMessage).set(userMessage));
        inputJson.set("messages", messagesJson.get("messages"));

        // 组装 parameters。
        JSONObject parametersJson = new JSONObject();
        parametersJson.set("result_format", "message");

        // 组装完整请求体。
        JSONObject requestJson = new JSONObject();
        requestJson.set("model", "qwen-plus");
        requestJson.set("input", inputJson);
        requestJson.set("parameters", parametersJson);

        // 发送请求并输出结果。
        String result = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestJson.toString())
                .execute()
                .body();

        System.out.println(result);
    }
}
