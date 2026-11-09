package com.requestrecorder.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;

/**
 * 采集组件启用条件：
 * <ul>
 *   <li>{@code request-recorder.enabled=false} → 静默禁用；</li>
 *   <li>配置缺失/非法 → 打印明确错误并禁用采集（默认不阻断业务启动，
 *       {@code request-recorder.fail-fast=true} 时直接启动失败）。</li>
 * </ul>
 */
public class RecorderEnabledCondition implements Condition {

    private static final Logger log = LoggerFactory.getLogger(RecorderEnabledCondition.class);

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        RecorderProperties properties;
        try {
            properties = Binder.get(environment)
                    .bind("request-recorder", RecorderProperties.class)
                    .orElseGet(RecorderProperties::new);
        } catch (Exception e) {
            return degrade(environment, "配置绑定失败: " + e.getMessage());
        }
        if (!properties.isEnabled()) {
            log.info("RequestRecorder 采集已关闭（request-recorder.enabled=false）");
            return false;
        }
        List<String> errors = properties.toSettings().validate(true);
        if (errors.isEmpty()) {
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (String error : errors) {
            log.error("RequestRecorder 配置错误: {}", error);
            sb.append(error).append("; ");
        }
        return degrade(environment, sb.toString());
    }

    private boolean degrade(Environment environment, String reason) {
        boolean failFast = environment.getProperty("request-recorder.fail-fast", Boolean.class, Boolean.FALSE);
        if (failFast) {
            throw new IllegalStateException("RequestRecorder 配置非法，已按 fail-fast=true 中止启动: " + reason);
        }
        log.warn("RequestRecorder 已自动禁用采集，业务应用继续启动（原因：{}）。"
                + "请检查 request-recorder.project-code 与 request-recorder.database.jdbc-url 配置。", reason);
        return false;
    }
}
