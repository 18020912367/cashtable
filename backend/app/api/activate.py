"""激活接口（全功能模式）：激活码校验 / 首次激活上传本地库 / 模板下载 / 状态查询。

设计：
- keycode.db 存「全功能码」，激活码由管理员 scripts/gen_keycode.py 生成；
- 首次激活：App 上传本地 cashtable.db → 云端保存为
  ACTIVATED_DB_DIR/{激活码}_cashtable.db 并开启 WAL；
- 之后 App 所有数据/AI 请求带 X-Activate-Code 头，由 deps.get_db 路由到该租户库。
- 激活上传为 async 接口（FastAPI async + SQLite WAL），可支撑并发。
"""
import threading
from datetime import datetime

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from .. import models  # noqa: F401  确保模型注册到 Base.metadata
from ..config import ACTIVATED_DB_DIR
from ..database import Base, drop_tenant_engine
from ..keycode import activate_code, check_code_active, get_code, init_keycode_db, now_str
from ..seed import EXPENSE_CATEGORIES, INCOME_CATEGORIES

router = APIRouter(prefix='/api/activate', tags=['activate'])

# 同一激活码的并发激活用进程内锁串行化（覆盖写 + keycode 状态更新需原子）
_activation_lock = threading.Lock()

MAX_DB_BYTES = 64 * 1024 * 1024  # 激活库上限 64MB


# ---------- 请求/响应模型 ----------

class CheckRequest(BaseModel):
    code: str = Field(min_length=1, max_length=64)
    device_mac: str = ''


class CheckResponse(BaseModel):
    ok: bool
    message: str
    is_activated: bool = False
    activated_at: str = ''
    device_mac: str = ''


# ---------- 激活码校验 ----------

@router.post('/check', response_model=CheckResponse)
def check_activation(data: CheckRequest):
    """激活码校验：未激活返回可激活；已激活返回已激活（激活码可重复使用，多设备共用同一数据库）。"""
    code = data.code.strip()
    row = get_code(code)
    if row is None:
        return CheckResponse(ok=False, message='激活码不存在')
    if row.is_activated != 1:
        return CheckResponse(ok=True, message='激活码可用，可进行激活', is_activated=False)
    return CheckResponse(
        ok=True, message='该激活码已激活，可直接使用（多设备共用同一数据库）',
        is_activated=True, activated_at=row.activated_at, device_mac=row.device_mac,
    )


@router.get('/status', response_model=CheckResponse)
def activation_status(code: str, device_mac: str = ''):
    """激活状态查询（App 启动 / 模式切换时调用）。"""
    code = code.strip()
    row = get_code(code)
    if row is None:
        return CheckResponse(ok=False, message='激活码不存在')
    if row.is_activated != 1:
        return CheckResponse(ok=True, message='未激活', is_activated=False)
    return CheckResponse(
        ok=True, message='已激活（多设备共用同一数据库）', is_activated=True,
        activated_at=row.activated_at, device_mac=row.device_mac,
    )


# ---------- 首次激活：上传本地库 ----------

@router.post('/upload')
async def upload_activation_db(
    code: str = Form(...),
    device_mac: str = Form(...),
    file: UploadFile = File(...),
):
    """首次激活：校验激活码 → 保存 App 的 sqlite 为 {激活码}_cashtable.db → 开 WAL → 标记已激活。

    - 首次激活：上传本地库初始化租户数据库；
    - 重复使用（已激活）：不覆盖已有数据库，直接连接该激活码已激活的数据库，
      记录新设备标识（多设备共用同一数据库）。
    """
    code = code.strip()
    with _activation_lock:
        row = get_code(code)
        if row is None:
            raise HTTPException(status_code=403, detail='激活码不存在')

        if row.is_activated == 1:
            # 重复使用：直接连接已有数据库，不覆盖原数据
            if row.device_mac != device_mac:
                activate_code(code, device_mac)
            return {
                'ok': True,
                'message': '激活码已激活，已连接该激活码的数据库（未覆盖原数据）',
                'activated_at': row.activated_at,
            }

        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail='上传的数据库文件为空')
        if len(content) > MAX_DB_BYTES:
            raise HTTPException(status_code=400, detail='数据库文件超过 64MB 限制')

        # 覆盖写前释放该激活码的连接池，避免旧连接持有文件锁
        drop_tenant_engine(code)

        target = ACTIVATED_DB_DIR / f'{code}_cashtable.db'
        target.write_bytes(content)

        # 开启 SQLite WAL 模式（支持并发读写，全功能模式多端访问更稳）
        eng = create_engine(
            f'sqlite:///{target}', connect_args={'check_same_thread': False, 'timeout': 30}
        )
        try:
            with eng.connect() as conn:
                conn.execute(text('PRAGMA journal_mode=WAL'))
        finally:
            eng.dispose()

        # 标记激活（记录激活时间与设备）
        activate_code(code, device_mac)
        return {'ok': True, 'message': '激活成功', 'activated_at': now_str()}


# ---------- 标准模板下载（自定义模式：自行导入服务器） ----------

@router.get('/template')
def download_template():
    """返回标准 cashtable.db 模板（表结构 + 初始分类，不含数据）。

    自定义模式用户下载后自行部署到自己的服务器，再填服务器地址连接。
    """
    import tempfile
    from pathlib import Path

    tmp = tempfile.NamedTemporaryFile(suffix='.db', delete=False)
    tmp.close()
    tpath = Path(tmp.name)
    try:
        tengine = create_engine(f'sqlite:///{tpath}', connect_args={'check_same_thread': False})
        Base.metadata.create_all(bind=tengine)
        db = sessionmaker(bind=tengine, autoflush=False)()
        for sort, name in enumerate(EXPENSE_CATEGORIES, start=1):
            db.add(models.Category(name=name, kind='expense', sort=sort))
        for sort, name in enumerate(INCOME_CATEGORIES, start=1):
            db.add(models.Category(name=name, kind='income', sort=sort))
        db.commit()
        db.close()
        tengine.dispose()
        return FileResponse(
            tpath,
            media_type='application/octet-stream',
            filename='cashtable_template.db',
            headers={'Content-Disposition': 'attachment; filename="cashtable_template.db"'},
        )
    finally:
        pass  # 文件由 FastAPI 发送后由系统清理；这里保留以防发送失败可手动删除


def init_activation() -> None:
    """启动时初始化：keycode.db 建表。"""
    init_keycode_db()
