package com.requestrecorder.starter.boot2;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.service.RecorderService;
import com.requestrecorder.starter.RecorderAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Spring Boot 2.x 自动装配入口（javax.servlet）。
 * <p>
 * 通过 {@code META-INF/spring.factories} 与自研导入器注册；Boot3 环境下因缺少 javax.servlet.Filter 自动跳过。
 * 所有 javax 相关类型都收敛在内部静态配置类中，避免 Boot3 环境加载本类时出现类解析错误。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "javax.servlet.Filter")
@Import({RecorderAutoConfiguration.class, RequestRecorderBoot2AutoConfiguration.ServletFilterConfiguration.class})
public class RequestRecorderBoot2AutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "javax.servlet.Filter")
    static class ServletFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "requestRecorderFilterRegistration")
        public FilterRegistrationBean<RecorderFilter> requestRecorderFilterRegistration(RecorderService service) {
            RecorderSettings settings = service.getSettings();
            FilterRegistrationBean<RecorderFilter> registration = new FilterRegistrationBean<RecorderFilter>();
            registration.setFilter(new RecorderFilter(settings, service.getCollector()));
            registration.addUrlPatterns("/*");
            registration.setName("requestRecorderFilter");
            // 位于字符编码过滤器之后、绝大多数业务过滤器之前，保证 body 编码正确且捕获范围足够大
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
            return registration;
        }
    }
}
