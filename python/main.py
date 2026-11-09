import tkinter as tk,json,threading,queue,traceback
from tkinter import ttk,messagebox,simpledialog
from datetime import datetime
from config import load_config,save_config
from db import DB
from replay import replay,save_history

def pretty(v):
 if v is None or v=="":return ""
 if isinstance(v,(dict,list)):return json.dumps(v,ensure_ascii=False,indent=2,default=str)
 s=str(v).strip()
 if not s:return ""
 try:return json.dumps(json.loads(s),ensure_ascii=False,indent=2)
 except Exception:return s

class App(tk.Tk):
 def __init__(self):
  super().__init__();self.title("RequestReplay");self.geometry("1440x900");self.minsize(1100,700)
  self.cfg=load_config();self.db=DB(self.cfg);self._q=queue.Queue();self._ui();self._pump();self.after(100,self.load_projects)
 def _ui(self):
  top=ttk.Frame(self,padding=7);top.pack(fill="x")
  ttk.Button(top,text="数据库配置",command=self.db_config).pack(side="left")
  ttk.Label(top,text="项目").pack(side="left",padx=(12,3));self.project=tk.StringVar(value="全部项目")
  self.project_box=ttk.Combobox(top,textvariable=self.project,state="readonly",width=28);self.project_box.pack(side="left");self.project_box.bind("<<ComboboxSelected>>",lambda e:self.search())
  ttk.Button(top,text="项目管理",command=self.projects_dialog).pack(side="left",padx=4);ttk.Button(top,text="搜索/刷新",command=self.search).pack(side="left")
  self.status=tk.StringVar(value="就绪");ttk.Label(top,textvariable=self.status).pack(side="right")
  f=ttk.LabelFrame(self,text="组合筛选",padding=7);f.pack(fill="x",padx=8,pady=4)
  self.keyword=tk.StringVar();self.method=tk.StringVar(value="全部");self.code=tk.StringVar(value="全部");self.start=tk.StringVar();self.end=tk.StringVar()
  ttk.Label(f,text="关键词（URL/请求/响应）").grid(row=0,column=0,sticky="w");ttk.Entry(f,textvariable=self.keyword,width=42).grid(row=0,column=1,padx=4)
  ttk.Label(f,text="方法").grid(row=0,column=2);ttk.Combobox(f,textvariable=self.method,values=["全部","GET","POST","PUT","PATCH","DELETE","OPTIONS","HEAD"],state="readonly",width=10).grid(row=0,column=3)
  ttk.Label(f,text="状态").grid(row=0,column=4);ttk.Combobox(f,textvariable=self.code,values=["全部","2xx","3xx","4xx","5xx"],state="readonly",width=8).grid(row=0,column=5)
  ttk.Label(f,text="开始时间").grid(row=1,column=0,sticky="w",pady=4);ttk.Entry(f,textvariable=self.start,width=22).grid(row=1,column=1,sticky="w")
  ttk.Label(f,text="结束时间").grid(row=1,column=2);ttk.Entry(f,textvariable=self.end,width=22).grid(row=1,column=3,sticky="w")
  ttk.Label(f,text="YYYY-MM-DD HH:MM:SS").grid(row=1,column=4,columnspan=2,sticky="w");ttk.Button(f,text="查询",command=self.search).grid(row=0,column=6,rowspan=2,padx=12)
  pane=ttk.Panedwindow(self,orient="vertical");pane.pack(fill="both",expand=True,padx=8,pady=5);upper=ttk.Frame(pane);lower=ttk.Frame(pane);pane.add(upper,weight=3);pane.add(lower,weight=3)
  cols=("id","project_code","request_time","method","uri","response_status","duration_ms","client_ip")
  self.tree=ttk.Treeview(upper,columns=cols,show="headings",selectmode="browse")
  for c,n,w in zip(cols,["ID","项目","时间","方法","接口","状态","耗时ms","IP"],[60,120,155,70,440,65,80,120]):self.tree.heading(c,text=n);self.tree.column(c,width=w)
  sy=ttk.Scrollbar(upper,orient="vertical",command=self.tree.yview);self.tree.configure(yscrollcommand=sy.set);self.tree.pack(side="left",fill="both",expand=True);sy.pack(side="right",fill="y")
  self.tree.bind("<<TreeviewSelect>>",self.detail);self.tree.bind("<Button-3>",self.popup_menu)
  self.menu=tk.Menu(self,tearoff=0);self.menu.add_command(label="回放",command=self.do_replay);self.menu.add_separator();self.menu.add_command(label="删除该条",command=self.delete_selected)
  dp=ttk.Panedwindow(lower,orient="horizontal");dp.pack(fill="both",expand=True)
  self.req_frame=ttk.LabelFrame(dp,text="请求信息",padding=4);self.resp_frame=ttk.LabelFrame(dp,text="响应信息",padding=4)
  dp.add(self.req_frame,weight=1);dp.add(self.resp_frame,weight=1)
  self.req_nb=ttk.Notebook(self.req_frame);self.req_nb.pack(fill="both",expand=True)
  self.resp_nb=ttk.Notebook(self.resp_frame);self.resp_nb.pack(fill="both",expand=True)
  self.req_tabs={}
  for n in ("基本信息","参数","请求头","请求体"):self.req_tabs[n]=self.area(self.req_nb,n)
  self.resp_tabs={}
  for n in ("响应头","响应体"):self.resp_tabs[n]=self.area(self.resp_nb,n)
  actions=ttk.Frame(self);actions.pack(fill="x",padx=8,pady=3)
  for label,fn in [("回放",self.do_replay),("批量删除",self.delete_selected),("删除当前项目全部",self.delete_project),("删除所有项目",self.delete_all),("回放历史",self.history)]:
   ttk.Button(actions,text=label,command=fn).pack(side="left",padx=3)
  ttk.Label(actions,text="提示：回放会真实调用目标接口，可能产生业务副作用；右键列表同样可回放。",foreground="#666").pack(side="left",padx=12)
 def area(self,nb,name):
  fr=ttk.Frame(nb);t=tk.Text(fr,wrap="none",font=("Consolas",10),undo=False)
  sy=ttk.Scrollbar(fr,orient="vertical",command=t.yview);sx=ttk.Scrollbar(fr,orient="horizontal",command=t.xview)
  t.configure(yscrollcommand=sy.set,xscrollcommand=sx.set)
  sy.pack(side="right",fill="y");sx.pack(side="bottom",fill="x");t.pack(side="left",fill="both",expand=True)
  nb.add(fr,text=name);return t
 def set_text(self,t,content):t.delete("1.0","end");t.insert("1.0",content or "")
 def run_bg(self,fn,done=None):
  def task():
   try:self._q.put(("ok",(done,fn())))
   except Exception as e:traceback.print_exc();self._q.put(("err",e))
  threading.Thread(target=task,daemon=True).start()
 def _pump(self):
  try:
   while True:
    kind,p=self._q.get_nowait()
    if kind=="err":messagebox.showerror("错误",str(p))
    else:
     d,r=p
     if d:
      try:d(r)
      except Exception:traceback.print_exc()
  except queue.Empty:pass
  finally:self.after(80,self._pump)
 def db_config(self):
  w=tk.Toplevel(self);w.title("数据库配置");vars={}
  for i,(k,label) in enumerate([("host","主机"),("port","端口"),("database","数据库"),("user","用户"),("password","密码")]):
   ttk.Label(w,text=label).grid(row=i,column=0,padx=8,pady=5);v=tk.StringVar(value=str(self.cfg.get(k,"")));vars[k]=v;ttk.Entry(w,textvariable=v,width=30,show="*" if k=="password" else "").grid(row=i,column=1,padx=8)
  def save():
   c=dict(self.cfg)
   for k,v in vars.items():c[k]=int(v.get()) if k=="port" else v.get()
   try:DB(c).test();self.cfg=c;self.db=DB(c);save_config(c);w.destroy();self.load_projects()
   except Exception as e:messagebox.showerror("连接失败",str(e),parent=w)
  ttk.Button(w,text="测试并保存",command=save).grid(row=5,columnspan=2,pady=8)
 def load_projects(self):
  def done(rows):
   self.project_box["values"]=["全部项目"]+[x["project_code"]+" | "+x["project_name"] for x in rows]
   if self.project.get() not in self.project_box["values"]:self.project.set("全部项目")
   self.search()
  self.run_bg(lambda:self.db.query("SELECT project_code,project_name FROM http_request_project ORDER BY project_name"),done)
 def projects_dialog(self):
  w=tk.Toplevel(self);w.title("项目管理");w.geometry("600x380")
  t=ttk.Treeview(w,columns=("code","name","desc"),show="headings")
  for c,n in [("code","项目编码"),("name","项目名称"),("desc","描述")]:t.heading(c,text=n);t.column(c,width=180)
  t.pack(fill="both",expand=True,padx=8,pady=8)
  def load():
   for x in t.get_children():t.delete(x)
   for p in self.db.query("SELECT * FROM http_request_project ORDER BY id DESC"):t.insert("","end",values=(p["project_code"],p["project_name"],p.get("description") or ""))
  def add():
   code=simpledialog.askstring("项目编码","输入与 Java project-code 一致的编码",parent=w)
   if not code:return
   name=simpledialog.askstring("项目名称","输入名称",parent=w)
   if not name:return
   self.db.execute("INSERT INTO http_request_project(project_code,project_name) VALUES(%s,%s) ON DUPLICATE KEY UPDATE project_name=VALUES(project_name)",(code.strip(),name.strip()));load();self.load_projects()
  ttk.Button(w,text="新增/更新项目",command=add).pack(pady=5);load()
 def where(self):
  c=[];a=[];p=self.project.get()
  if p!="全部项目":c.append("project_code=%s");a.append(p.split(" | ")[0])
  q=self.keyword.get().strip()
  if q:c.append("(url LIKE %s OR uri LIKE %s OR query_params LIKE %s OR request_headers LIKE %s OR request_body LIKE %s OR response_body LIKE %s)");a.extend(["%"+q+"%"]*6)
  if self.method.get()!="全部":c.append("method=%s");a.append(self.method.get())
  if self.code.get()!="全部":n=int(self.code.get()[0]);c.append("response_status >= %s AND response_status < %s");a.extend([n*100,(n+1)*100])
  for v,op in [(self.start.get().strip(),">="),(self.end.get().strip(),"<=")]:
   if v:datetime.strptime(v,"%Y-%m-%d %H:%M:%S");c.append("request_time "+op+" %s");a.append(v)
  return (" WHERE "+" AND ".join(c) if c else ""),a
 def search(self):
  try:w,a=self.where()
  except Exception as e:messagebox.showerror("筛选条件错误",str(e));return
  def done(rows):
   for x in self.tree.get_children():self.tree.delete(x)
   for r in rows:self.tree.insert("","end",iid=str(r["id"]),values=tuple(r.get(k) for k in ("id","project_code","request_time","method","uri","response_status","duration_ms","client_ip")))
   self.status.set("显示 "+str(len(rows))+" 条，最多 1000 条")
   self.clear_panels()
  sql="SELECT id,project_code,request_time,method,uri,response_status,duration_ms,client_ip FROM http_request_record"+w+" ORDER BY id DESC LIMIT 1000"
  self.run_bg(lambda:self.db.query(sql,a),done)
 def clear_panels(self):
  self.req_frame.configure(text="请求信息");self.resp_frame.configure(text="响应信息")
  for t in list(self.req_tabs.values())+list(self.resp_tabs.values()):self.set_text(t,"")
 def detail(self,event=None):
  sel=self.tree.selection()
  if not sel:return
  rid=int(sel[0])
  def work():
   rows=self.db.query("SELECT * FROM http_request_record WHERE id=%s",(rid,))
   return rows[0] if rows else None
  def done(r):
   if not r:return
   self.fill_request(r)
   self.resp_frame.configure(text="响应信息（原始）  HTTP %s / %sms"%(r.get("response_status"),r.get("duration_ms")))
   self.set_text(self.resp_tabs["响应头"],pretty(r.get("response_headers")) or "(空)")
   self.set_text(self.resp_tabs["响应体"],pretty(r.get("response_body")) or (("[异常] "+str(r.get("exception_message"))) if r.get("exception_message") else "(空)"))
  self.run_bg(work,done)
 def fill_request(self,r,title=None):
  self.req_frame.configure(text=(title or "请求信息")+"  #%s  %s %s"%(r["id"],r.get("method"),r.get("uri") or r.get("url")))
  base={k:r.get(k) for k in ("id","project_code","project_name","request_id","request_time","method","url","uri","client_ip","user_agent","response_status","duration_ms")}
  self.set_text(self.req_tabs["基本信息"],pretty(base))
  params="==== query_params ====\n"+(pretty(r.get("query_params")) or "(空)")+"\n\n==== path_params ====\n"+(pretty(r.get("path_params")) or "(空)")
  self.set_text(self.req_tabs["参数"],params)
  self.set_text(self.req_tabs["请求头"],pretty(r.get("request_headers")) or "(空)")
  self.set_text(self.req_tabs["请求体"],pretty(r.get("request_body")) or "(空)")
 def popup_menu(self,event):
  iid=self.tree.identify_row(event.y)
  if not iid:return
  self.tree.selection_set(iid);self.tree.focus(iid)
  try:self.menu.tk_popup(event.x_root,event.y_root)
  finally:self.menu.grab_release()
 def do_replay(self,event=None):
  sel=self.tree.selection()
  if not sel:messagebox.showwarning("提示","请先在上方列表中选择一条请求记录");return
  rid=int(sel[0]);self.status.set("回放中 ... #%s"%rid)
  def work():
   r=self.db.query("SELECT * FROM http_request_record WHERE id=%s",(rid,))[0]
   try:h=json.loads(r.get("request_headers") or "{}")
   except Exception:h={}
   res=replay(r,r.get("url") or "",r.get("method") or "GET",h,r.get("request_body") or "")
   save_history(self.db,r,r.get("url"),r.get("method"),res)
   return (r,res)
  self.run_bg(work,self.after_replay)
 def after_replay(self,payload):
  r,res=payload
  self.fill_request(r,"请求信息（回放所用）")
  self.resp_frame.configure(text="回放响应  %s / HTTP %s / %sms"%(res.get("replay_status"),res.get("response_status"),res.get("duration_ms")))
  self.set_text(self.resp_tabs["响应头"],pretty(res.get("response_headers")) or "(空)")
  body=pretty(res.get("response_body"))
  if res.get("exception_message"):body="[异常] "+str(res["exception_message"])+"\n\n"+body
  self.set_text(self.resp_tabs["响应体"],body or "(空)")
  self.status.set("回放完成：%s  HTTP %s  耗时 %sms"%(res.get("replay_status"),res.get("response_status"),res.get("duration_ms")))
 def delete_selected(self):
  ids=[(int(x),) for x in self.tree.selection()]
  if ids and messagebox.askyesno("确认","删除选中的 "+str(len(ids))+" 条？"):
   self.run_bg(lambda:self.db.many("DELETE FROM http_request_record WHERE id=%s",ids),lambda _:self.search())
 def delete_project(self):
  p=self.project.get()
  if p=="全部项目":messagebox.showwarning("提示","请先选择具体项目");return
  code=p.split(" | ")[0]
  if messagebox.askyesno("确认","删除项目 "+code+" 的所有采集记录？"):self.run_bg(lambda:self.db.execute("DELETE FROM http_request_record WHERE project_code=%s",(code,)),lambda _:self.search())
 def delete_all(self):
  if messagebox.askyesno("二次确认","删除所有项目全部采集记录，不可恢复。继续？") and messagebox.askyesno("最终确认","确认清空 http_request_record？"):
   self.run_bg(lambda:self.db.execute("DELETE FROM http_request_record"),lambda _:self.search())
 def history(self):
  w=tk.Toplevel(self);w.title("回放历史");w.geometry("1100x600")
  t=ttk.Treeview(w,columns=("id","source","project","time","method","state","status","url"),show="headings")
  for c,n,wd in [("id","ID",55),("source","原记录",75),("project","项目",110),("time","回放时间",150),("method","方法",70),("state","结果",80),("status","状态码",70),("url","目标 URL",430)]:t.heading(c,text=n);t.column(c,width=wd)
  t.pack(fill="both",expand=True)
  self.run_bg(lambda:self.db.query("SELECT * FROM http_replay_history ORDER BY id DESC LIMIT 1000"),lambda rows:[t.insert("","end",values=(r["id"],r["source_record_id"],r["project_code"],r["replay_time"],r["method"],r["replay_status"],r["response_status"],r["target_url"])) for r in rows])

if __name__=="__main__":App().mainloop()
