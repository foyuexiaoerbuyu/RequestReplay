package com.example.recorder;
import com.zaxxer.hikari.*; import java.sql.*;
public final class RecorderDatabase implements AutoCloseable {
 private final HikariDataSource ds;
 public RecorderDatabase(RecorderProperties p){HikariConfig c=new HikariConfig();c.setJdbcUrl(p.jdbcUrl);c.setUsername(p.username);c.setPassword(p.password);c.setMaximumPoolSize(3);c.setMinimumIdle(0);c.setConnectionTimeout(2000);c.setPoolName("request-recorder-independent");ds=new HikariDataSource(c);}
 public void insert(RecordData r)throws SQLException{
 String q="INSERT INTO http_request_record(project_code,project_name,request_id,request_time,method,url,uri,query_params,path_params,request_headers,request_body,client_ip,user_agent,response_status,response_headers,response_body,duration_ms,response_size,exception_message,request_truncated,response_truncated) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
 try(Connection c=ds.getConnection();PreparedStatement s=c.prepareStatement(q)){int i=1;s.setString(i++,r.projectCode);s.setString(i++,r.projectName);s.setString(i++,r.requestId);s.setTimestamp(i++,new Timestamp(r.requestTime));s.setString(i++,r.method);s.setString(i++,r.url);s.setString(i++,r.uri);s.setString(i++,r.queryParams);s.setString(i++,r.pathParams);s.setString(i++,r.requestHeaders);s.setString(i++,r.requestBody);s.setString(i++,r.clientIp);s.setString(i++,r.userAgent);s.setInt(i++,r.responseStatus);s.setString(i++,r.responseHeaders);s.setString(i++,r.responseBody);s.setLong(i++,r.durationMs);s.setLong(i++,r.responseSize);s.setString(i++,r.exceptionMessage);s.setBoolean(i++,r.requestTruncated);s.setBoolean(i,r.responseTruncated);s.executeUpdate();}
 }
 public void close(){ds.close();}
}
