package com.requestrecorder.it.boot3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 集成测试应用：提供正常、异常、大响应、被排除路径等端点。
 */
@SpringBootApplication
public class ItApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItApplication.class, args);
    }

    @RestController
    static class ApiController {

        @GetMapping("/api/echo")
        public String echo(@RequestParam(value = "name", required = false, defaultValue = "") String name) {
            return "echo:" + name;
        }

        @PostMapping("/api/order")
        public Map<String, Object> order(@RequestBody Map<String, Object> body) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("accepted", true);
            result.put("sku", body.get("sku"));
            return result;
        }

        @GetMapping("/api/boom")
        public String boom() {
            throw new IllegalStateException("boom-message");
        }

        @GetMapping("/api/large")
        public String large() {
            StringBuilder builder = new StringBuilder(2048);
            for (int i = 0; i < 2048; i++) {
                builder.append('x');
            }
            return builder.toString();
        }

        @GetMapping("/skip/ping")
        public String ping() {
            return "pong";
        }
    }

    @RestControllerAdvice
    static class GlobalExceptionHandler {

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handle(Exception e) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("error", e.getClass().getSimpleName());
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
