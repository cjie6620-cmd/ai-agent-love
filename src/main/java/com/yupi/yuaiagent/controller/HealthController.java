package com.yupi.yuaiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器。
 * 用于给部署平台、监控系统或前端快速判断服务是否存活。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * 最简单的健康检查接口。
     *
     * @return 固定返回 ok，表示服务进程可正常响应
     */
    @GetMapping
    public String healthCheck() {
        return "ok";
    }
}
