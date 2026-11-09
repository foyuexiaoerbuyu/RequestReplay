package com.example.recorder.boot2;
import com.example.recorder.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix="request-recorder",name="enabled",havingValue="true",matchIfMissing=true)
public class RecorderAutoConfiguration {
 @Bean @ConfigurationProperties(prefix="request-recorder") public RecorderProperties recorderProperties(){return new RecorderProperties();}
 @Bean(destroyMethod="close") public RecorderDatabase recorderDatabase(RecorderProperties p){return new RecorderDatabase(p);}
 @Bean(destroyMethod="close") public RecordWriter recordWriter(RecorderDatabase d,RecorderProperties p){return new RecordWriter(d,p);}
 @Bean public FilterRegistrationBean<RecorderFilter> recorderFilter(RecorderProperties p,RecordWriter w){FilterRegistrationBean<RecorderFilter>b=new FilterRegistrationBean<>();b.setFilter(new RecorderFilter(p,w));b.addUrlPatterns("/*");b.setOrder(Integer.MIN_VALUE+50);return b;}
}
