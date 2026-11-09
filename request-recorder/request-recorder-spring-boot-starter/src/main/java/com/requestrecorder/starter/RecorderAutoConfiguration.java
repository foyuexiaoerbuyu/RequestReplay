package com.requestrecorder.starter;

import com.requestrecorder.core.config.RecorderSettings;
import com.requestrecorder.core.database.RecorderDataSource;
import com.requestrecorder.core.database.RecorderRepository;
import com.requestrecorder.core.queue.AsyncRecordQueue;
import com.requestrecorder.core.service.RecorderService;
import com.requestrecorder.starter.RecorderLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 版本无关的公共自动装配：装配 core 的核心 Bean（配置、数据源、仓储、队列、门面、生命周期）。
 * <p>
 * 本类不直接注册为自动配置，由 boot2 / boot3 版本适配模块通过 {@code @Import} 引入，
 * 从而保证同一份装配逻辑在两个大版本上行为一致。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(RecorderEnabledCondition.class)
@EnableConfigurationProperties(RecorderProperties.class)
public class RecorderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RecorderAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RecorderSettings requestRecorderSettings(RecorderProperties properties) {
        RecorderSettings settings = properties.toSettings();
        log.info("RequestRecorder 配置已加载: {}", settings);
        return settings;
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public RecorderDataSource requestRecorderDataSource(RecorderSettings settings) {
        return new RecorderDataSource(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public RecorderRepository requestRecorderRepository(RecorderDataSource dataSource, RecorderSettings settings) {
        return new RecorderRepository(dataSource, settings);
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public AsyncRecordQueue requestRecorderQueue(RecorderRepository repository, RecorderSettings settings) {
        return new AsyncRecordQueue(repository, settings);
    }

    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public RecorderService requestRecorderService(RecorderSettings settings, RecorderDataSource dataSource,
                                                  RecorderRepository repository, AsyncRecordQueue queue) {
        return new RecorderService(settings, dataSource, repository, queue);
    }

    @Bean
    @ConditionalOnMissingBean
    public RecorderLifecycle requestRecorderLifecycle(RecorderService service) {
        return new RecorderLifecycle(service);
    }
}
