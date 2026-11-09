package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示接口：覆盖正常、异常、大响应、敏感头、排除路径五类采集场景。
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    /** 正常 GET：应被采集，status=200。 */
    @GetMapping("/hello")
    public String hello(@RequestParam(value = "name", defaultValue = "world") String name) {
        return "hello, " + name;
    }

    /** 正常 POST JSON：应被采集，请求体完整入库。 */
    @PostMapping(value = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> order(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("accepted", true);
        result.put("sku", body.get("sku"));
        result.put("qty", body.get("qty"));
        return result;
    }

    /** 抛异常：由全局异常处理器写回 500，采集到的状态码与响应体与真实一致。 */
    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("demo-boom: 故意抛出的异常");
    }

    /** 大响应：默认 512KB，超过 max-body-size(256KB) 时记录被截断并标记 response_truncated=1。 */
    @GetMapping("/large")
    public String large(@RequestParam(value = "kb", defaultValue = "512") int kb) {
        int size = Math.max(1, kb) * 1024;
        StringBuilder builder = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            builder.append('x');
        }
        return builder.toString();
    }

    /** 敏感信息：Authorization 头与 token 参数在库中应被替换为 ******。 */
    @GetMapping("/secure")
    public Map<String, Object> secure(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", defaultValue = "") String token) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("authorizationLength", authorization == null ? 0 : authorization.length());
        result.put("tokenLength", token.length());
        return result;
    }

    /** 排除路径：业务正常响应，但不会被采集。 */
    @GetMapping("/skip/ping")
    public String skipPing() {
        return "pong";
    }
}
