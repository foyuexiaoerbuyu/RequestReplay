package com.requestrecorder.starter.boot3;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 有界响应缓存包装器。
 * <p>
 * 业务响应数据一边直通写回客户端、一边按上限缓存到内存，超出 {@code maxCacheSize} 后停止缓存
 * 只做直通，避免大文件下载把内存打满，同时保证客户端拿到的响应完整无损。
 * 该包装器不继承 Spring 的 ContentCachingResponseWrapper，因为后者会把整个响应体缓存到内存。
 */
public class BoundedResponseWrapper extends HttpServletResponseWrapper {

    private final int maxCacheSize;
    private final ByteArrayOutputStream cache = new ByteArrayOutputStream(1024);
    private long totalBytes;
    private boolean truncated;
    private TeeServletOutputStream stream;
    private PrintWriter writer;

    public BoundedResponseWrapper(HttpServletResponse response, int maxCacheSize) {
        super(response);
        this.maxCacheSize = maxCacheSize > 0 ? maxCacheSize : Integer.MAX_VALUE;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called for this response");
        }
        if (stream == null) {
            stream = new TeeServletOutputStream(getResponse().getOutputStream());
        }
        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (stream != null) {
            throw new IllegalStateException("getOutputStream() has already been called for this response");
        }
        if (writer == null) {
            String encoding = getCharacterEncoding();
            if (encoding == null || encoding.isEmpty()) {
                encoding = "UTF-8";
                setCharacterEncoding(encoding);
            }
            stream = new TeeServletOutputStream(getResponse().getOutputStream());
            writer = new PrintWriter(new OutputStreamWriter(stream, encoding), false);
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        flushCaptured();
        super.flushBuffer();
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        synchronized (cache) {
            cache.reset();
        }
        totalBytes = 0L;
        truncated = false;
    }

    /** 把缓冲区内容刷给客户端（tee 模式下主要用于把 writer 内容落地）。 */
    public void flushCaptured() {
        if (writer != null) {
            writer.flush();
        }
        if (stream != null) {
            try {
                stream.flush();
            } catch (IOException ignored) {
                // flush 失败不影响采集
            }
        }
    }

    /** 已缓存（受上限约束）的响应体字节。 */
    public byte[] getCapturedBody() {
        synchronized (cache) {
            return cache.toByteArray();
        }
    }

    /** 响应体总字节数（含未缓存部分）。 */
    public long getTotalBytes() {
        return totalBytes;
    }

    /** 是否因超出缓存上限而截断。 */
    public boolean isTruncated() {
        return truncated;
    }

    private void capture(byte[] b, int off, int len) {
        totalBytes += len;
        if (truncated) {
            return;
        }
        synchronized (cache) {
            if ((long) cache.size() + len > maxCacheSize) {
                truncated = true;
                return;
            }
            cache.write(b, off, len);
        }
    }

    private final class TeeServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private TeeServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            capture(new byte[]{(byte) b}, 0, 1);
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            capture(b, off, len);
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // 同步采集场景不需要异步监听
        }
    }
}
