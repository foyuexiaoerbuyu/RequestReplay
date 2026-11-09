package com.example.recorder.boot2;

import com.example.recorder.*;
import java.io.*;import java.nio.charset.Charset;import java.util.*;import java.util.UUID;
import javax.servlet.*;import javax.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class RecorderFilter extends OncePerRequestFilter {
 private final RecorderProperties p;private final RecordWriter writer;
 public RecorderFilter(RecorderProperties p,RecordWriter w){this.p=p;writer=w;}
 protected boolean shouldNotFilter(HttpServletRequest req){return !p.enabled||"true".equalsIgnoreCase(req.getHeader("X-Request-Replay"))||RecorderSupport.excluded(req.getRequestURI(),p.excludePaths);}
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  long start=System.currentTimeMillis();ContentCachingRequestWrapper req=new ContentCachingRequestWrapper(request,p.maxBodySize);ContentCachingResponseWrapper res=new ContentCachingResponseWrapper(response);String err=null;
  try{chain.doFilter(req,res);}catch(IOException e){err=e.getClass().getName()+": "+e.getMessage();throw e;}catch(ServletException e){err=e.getClass().getName()+": "+e.getMessage();throw e;}catch(RuntimeException e){err=e.getClass().getName()+": "+e.getMessage();throw e;}finally{
   try{
    RecordData d=new RecordData();d.projectCode=p.projectCode;d.projectName=p.projectName;d.requestId=UUID.randomUUID().toString();d.requestTime=start;d.method=req.getMethod();d.url=req.getRequestURL().toString()+(req.getQueryString()==null?"":"?"+req.getQueryString());d.uri=req.getRequestURI();d.queryParams=req.getQueryString();d.pathParams="{}";
    Map<String,String> h=new LinkedHashMap<>();Enumeration<String> en=req.getHeaderNames();while(en!=null&&en.hasMoreElements()){String n=en.nextElement();h.put(n,req.getHeader(n));}d.requestHeaders=RecorderSupport.headers(h,p);
    byte[] rb=req.getContentAsByteArray();String cs=req.getCharacterEncoding()==null?"UTF-8":req.getCharacterEncoding();d.requestBody=RecorderSupport.textual(req.getContentType())?RecorderSupport.mask(new String(rb,Charset.forName(cs)),p):"[non-text body skipped]";d.requestTruncated=rb.length>=p.maxBodySize;
    d.clientIp=req.getRemoteAddr();d.userAgent=req.getHeader("User-Agent");d.responseStatus=res.getStatus();Map<String,String> rh=new LinkedHashMap<>();for(String n:res.getHeaderNames())rh.put(n,res.getHeader(n));d.responseHeaders=RecorderSupport.headers(rh,p);
    byte[] sb=res.getContentAsByteArray();d.responseSize=sb.length;d.responseBody=RecorderSupport.textual(res.getContentType())?RecorderSupport.mask(new String(sb,Charset.forName(res.getCharacterEncoding()==null?"UTF-8":res.getCharacterEncoding())),p):"[non-text response skipped]";
    d.responseTruncated=sb.length>=p.maxBodySize;d.durationMs=System.currentTimeMillis()-start;d.exceptionMessage=err;writer.submit(d);
   }catch(Exception ignored){}finally{res.copyBodyToResponse();}
  }
 }
}
