package com.example.recorder;
import com.fasterxml.jackson.databind.ObjectMapper;import java.util.*;import java.util.regex.*;
public final class RecorderSupport {
 private static final ObjectMapper M=new ObjectMapper();private RecorderSupport(){}
 public static String json(Object x){try{return M.writeValueAsString(x);}catch(Exception e){return "{}";}}
 public static String headers(Map<String,String> h,RecorderProperties p){Map<String,String> o=new LinkedHashMap<>();for(Map.Entry<String,String> e:h.entrySet()){boolean mask=false;for(String k:p.sensitiveHeaders)if(k.equalsIgnoreCase(e.getKey()))mask=true;o.put(e.getKey(),mask?"******":e.getValue());}return json(o);}
 public static String mask(String s,RecorderProperties p){if(s==null)return null;String x=s;for(String k:p.sensitiveKeys)x=x.replaceAll("(?i)([\"']?"+Pattern.quote(k)+"[\"']?\\s*[:=]\\s*[\"']?)[^,\"'&}\\s]+","$1******");return x;}
 public static boolean textual(String c){if(c==null||c.isEmpty())return true;String x=c.toLowerCase(Locale.ROOT);return x.startsWith("text/")||x.contains("json")||x.contains("xml")||x.contains("javascript")||x.contains("x-www-form-urlencoded");}
 public static boolean excluded(String uri,List<String> ps){for(String p:ps){String r=Pattern.quote(p).replace("\\Q**\\E",".*").replace("\\Q*\\E","[^/]*");if(uri.matches(r))return true;}return false;}
}
