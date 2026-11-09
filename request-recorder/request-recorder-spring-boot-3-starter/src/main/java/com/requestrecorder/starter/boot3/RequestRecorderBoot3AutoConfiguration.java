package com.requestrecorder.starter.boot3;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.service.RecorderService;
import com.requestrecorder.starter.RecorderAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Spring Boot 3.x 自动装配入口（jakarta.servlet）。
 * <p>
 * 通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@Import({RecorderAutoConfiguration.class, RequestRecorderBoot3AutoConfiguration.ServletFilterConfiguration.class})
public class RequestRecorderBoot3AutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "requestRecorderFilterRegistration")
        public FilterRegistrationBean<RecorderFilter> requestRecorderFilterRegistration(RecorderService service) {
            RecorderSettings settings = service.getSettings();
            FilterRegistrationBean<RecorderFilter> registration = new FilterRegistrationBean<RecorderFilter>();
            registration.setFilter(new RecorderFilter(settings, service.getCollector()));
            registration.addUrlPatterns("/*");
            registration.setName("requestRecorderFilter");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
            return registration;
        }
    }
}
