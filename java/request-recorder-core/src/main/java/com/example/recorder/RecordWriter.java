package com.example.recorder;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;import java.util.logging.Logger;
public final class RecordWriter implements AutoCloseable {
 private static final Logger log=Logger.getLogger(RecordWriter.class.getName()); private final RecorderDatabase db; private final ThreadPoolExecutor pool; private final AtomicLong dropped=new AtomicLong();
 public RecordWriter(RecorderDatabase d,RecorderProperties p){db=d;int n=Math.max(1,p.workers);pool=new ThreadPoolExecutor(n,n,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(Math.max(100,p.queueCapacity)),r->{Thread t=new Thread(r,"request-recorder-writer");t.setDaemon(true);return t;},(r,e)->dropped.incrementAndGet());}
 public void submit(RecordData d){try{pool.execute(()->{try{db.insert(d);}catch(Exception e){log.warning("Recorder DB write failed: "+e.toString());}});}catch(Exception e){dropped.incrementAndGet();}}
 public long droppedCount(){return dropped.get();}
 public void close(){pool.shutdown();try{if(!pool.awaitTermination(3,TimeUnit.SECONDS))pool.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();pool.shutdownNow();}}
}
