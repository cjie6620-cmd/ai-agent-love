package com.yupi.yuaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Spring Boot 启动类。
 * 这里是整个后端项目的“总开关”，从这里启动容器并加载所有 Bean。
 */
@SpringBootApplication(exclude = {
        // 为了本地开发更轻量，这里先关闭数据库自动装配。
        // 如果你后续要启用 PgVector 或其他数据库能力，把这一项移除即可。
        DataSourceAutoConfiguration.class
})
public class YuAiAgentApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动参数，通常可忽略，由 Spring Boot 框架接管
     */
    public static void main(String[] args) {
        SpringApplication.run(YuAiAgentApplication.class, args);
    }

}
