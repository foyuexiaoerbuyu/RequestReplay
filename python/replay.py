import json,time,requests
from datetime import datetime
HOP={"host","content-length","connection","transfer-encoding","accept-encoding"}
def replay(record,url,method,headers,body,timeout=30):
 h={k:v for k,v in headers.items() if k.lower() not in HOP}
 h["X-Request-Replay"]="true";h["X-Request-Replay-Id"]=str(record["id"])
 start=time.time()
 try:
  r=requests.request(method.upper(),url,headers=h,data=body if body else None,timeout=timeout)
  return {"replay_status":"SUCCESS","response_status":r.status_code,"response_headers":json.dumps(dict(r.headers),ensure_ascii=False),"response_body":r.text[:2000000],"duration_ms":int((time.time()-start)*1000),"exception_message":None}
 except Exception as e:
  return {"replay_status":"FAILED","response_status":None,"response_headers":"{}","response_body":None,"duration_ms":int((time.time()-start)*1000),"exception_message":str(e)}
def save_history(db,record,url,method,result):
 db.execute("INSERT INTO http_replay_history(source_record_id,project_code,target_url,method,replay_time,replay_status,response_status,response_headers,response_body,duration_ms,exception_message) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
 (record["id"],record["project_code"],url,method,datetime.now(),result["replay_status"],result["response_status"],result["response_headers"],result["response_body"],result["duration_ms"],result["exception_message"]))
