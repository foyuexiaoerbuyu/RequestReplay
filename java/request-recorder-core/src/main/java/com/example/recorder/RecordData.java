package com.example.recorder;
public class RecordData {
 public String projectCode,projectName,requestId,method,url,uri,queryParams,pathParams,requestHeaders,requestBody,clientIp,userAgent,responseHeaders,responseBody,exceptionMessage;
 public int responseStatus; public long requestTime,durationMs,responseSize; public boolean requestTruncated,responseTruncated;
}
