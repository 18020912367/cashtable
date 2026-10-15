"""运行日志接口（全功能模式）：App 崩溃 / 功能失效 / 闪退 / 权限未正常生效上报。

设计：
- 每个激活码一个日志文件：LOG_DIR/{code}.log（云端项目根 logs/ 目录）；
- 每条记录一行，以「时间 | 设备标识 | 类型 | 详情」为标识逐行追加写入；
- 堆栈等多行详情压成单行，保证一行一条记录；
- 上报前校验激活码必须是已激活的全功能码（keycode.db），防止任意文件写入。
"""
import re
import threading
from datetime import datetime

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ..config import LOG_DIR
from ..keycode import get_code

router = APIRouter(prefix='/api/logs', tags=['logs'])

# 单线程服务内用锁串行化追加，保证多请求并发写同一文件的行完整性
_write_lock = threading.Lock()

# 激活码文件名安全白名单（防路径穿越；实际格式由 keycode.db 校验兜底）
_CODE_RE = re.compile(r'^[A-Za-z0-9_-]{1,64}$')

MAX_DETAIL = 8000  # 单条详情上限


class LogReport(BaseModel):
    code: str = Field(min_length=1, max_length=64)
    device_mac: str = Field(default='', max_length=128)
    kind: str = Field(default='ERROR', max_length=32)
    detail: str = Field(default='', max_length=MAX_DETAIL)


@router.post('/report/')
def report_log(data: LogReport):
    """App 异常日志上报：以「时间 | 设备标识 | 类型 | 详情」为一行，追加写入 logs/{激活码}.log。"""
    code = data.code.strip()
    if not _CODE_RE.match(code):
        raise HTTPException(status_code=400, detail='激活码格式非法')
    row = get_code(code)
    if row is None or row.is_activated != 1:
        raise HTTPException(status_code=403, detail='激活码不存在或未激活')

    kind = (data.kind.strip() or 'ERROR').upper()[:32]
    # 多行详情压成单行（\n 转义为可见占位），保证一行一条记录
    detail = data.detail.replace('\r', '').replace('\n', ' \\n ').strip() or '(无详情)'
    device = data.device_mac.strip() or '-'
    ts = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    line = f'{ts} | {device} | {kind} | {detail}\n'

    target = LOG_DIR / f'{code}.log'
    with _write_lock:
        with open(target, 'a', encoding='utf-8') as f:
            f.write(line)
    return {'ok': True}
