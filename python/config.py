import json,os
from pathlib import Path
APP_DIR=Path(os.getenv("APPDATA",str(Path.home())))/"RequestReplay";APP_DIR.mkdir(parents=True,exist_ok=True)
CONFIG_FILE=APP_DIR/"config.json"
DEFAULT={"host":"127.0.0.1","port":3306,"database":"request_recorder","user":"root","password":"","charset":"utf8mb4","connection_timeout":5}
def load_config():
 try:return {**DEFAULT,**json.loads(CONFIG_FILE.read_text(encoding="utf-8"))}
 except Exception:return DEFAULT.copy()
def save_config(c):CONFIG_FILE.write_text(json.dumps(c,ensure_ascii=False,indent=2),encoding="utf-8")
