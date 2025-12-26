package com.tes.springai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 单页应用控制器
 * 处理所有非 API 请求，转发到 index.html
 */
@Controller
public class SpaController {

    /**
     * 将所有前端路由请求转发到 index.html
     * 排除 API 请求和静态资源
     */
    @GetMapping(value = {"/", "/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
