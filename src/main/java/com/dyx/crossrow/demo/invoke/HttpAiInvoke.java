package com.dyx.crossrow.demo.invoke;
import cn.hutool.http.HttpUtil;
import java.util.HashMap;
import java.util.Map;

public class HttpAiInvoke{
    public static void main(String[] args) {
        // 1. 暂时先【硬编码】你的 Key，排除环境变量读不到的问题
        // 等跑通了再换回 System.getenv
        String apiKey = System.getenv(TestApiKey.API_KEY);

        // 2. 注意这里！如果你是国际站账号，必须加 -intl
        // 如果是中国站账号，请去掉 -intl
        //String url = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions";
         String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        String jsonBody = "{"
                + "\"model\":\"qwen-plus\","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":\"You are a helpful assistant.\"},"
                + "{\"role\":\"user\",\"content\":\"你是谁？\"}"
                + "]"
                + "}";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey.trim()); // 加上 trim()
        headers.put("Content-Type", "application/json");

        try {
            System.out.println("正在向 " + url + " 发送请求...");
            String result = HttpUtil.createPost(url)
                    .addHeaders(headers)
                    .body(jsonBody)
                    .execute()
                    .body();

            System.out.println("响应内容: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}