package com.example.recorder;
import java.util.*;
public class RecorderProperties {
 public boolean enabled=true; public String projectCode="default", projectName="Default";
 public String jdbcUrl="jdbc:mysql://127.0.0.1:3306/request_recorder?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
 public String username="root", password=""; public int queueCapacity=5000, maxBodySize=1048576, workers=1;
 public List<String> excludePaths=new ArrayList<String>(Arrays.asList("/actuator/**","/favicon.ico"));
 public List<String> sensitiveHeaders=new ArrayList<String>(Arrays.asList("authorization","cookie","set-cookie","x-api-key"));
 public List<String> sensitiveKeys=new ArrayList<String>(Arrays.asList("password","passwd","token","secret","access_token","refresh_token"));
}
