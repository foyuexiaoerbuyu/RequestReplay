import mysql.connector
class DB:
 def __init__(self,cfg):self.cfg=cfg
 def connect(self):return mysql.connector.connect(**self.cfg)
 def query(self,sql,args=()):
  c=self.connect()
  try:
   cur=c.cursor(dictionary=True);cur.execute(sql,args);r=cur.fetchall();cur.close();return r
  finally:c.close()
 def execute(self,sql,args=()):
  c=self.connect()
  try:
   cur=c.cursor();cur.execute(sql,args);c.commit();n=cur.rowcount;cur.close();return n
  finally:c.close()
 def many(self,sql,args):
  c=self.connect()
  try:
   cur=c.cursor();cur.executemany(sql,args);c.commit();n=cur.rowcount;cur.close();return n
  finally:c.close()
 def test(self):
  c=self.connect()
  try:c.cursor().execute("SELECT 1")
  finally:c.close()
