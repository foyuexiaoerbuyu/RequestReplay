package com.requestrecorder.starter;

import com.requestrecorder.core.service.RecorderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 采集组件生命周期：容器启动完成后初始化（建表检查 + 项目注册），
 * 停机时先排空队列再释放连接池，尽量不丢记录。
 */
public class RecorderLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RecorderLifecycle.class);

    private final RecorderService service;
    private volatile boolean running;

    public RecorderLifecycle(RecorderService service) {
        this.service = service;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            service.start();
        } catch (Exception e) {
            log.error("RequestRecorder 启动初始化异常（业务不受影响）: {}", e.toString());
        }
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            service.close();
        } catch (Exception e) {
            log.warn("RequestRecorder 停机异常: {}", e.toString());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 在 Web 服务器启动完成之后再初始化，避免启动早期请求被漏采。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
