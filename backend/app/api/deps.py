"""FastAPI 依赖：数据库会话（支持默认库 / 全功能模式租户库路由）。"""
from fastapi import HTTPException, Request

from ..database import SessionLocal, get_tenant_session_factory
from ..keycode import check_code_active


def get_db(request: Request):
    """数据库会话。

    - 默认（本地/自定义模式）：使用 backend/data/cashtable.db；
    - 全功能模式：请求头携带 X-Activate-Code（+ X-Device-Mac），
      校验激活状态后切换到对应激活码的租户库（{激活码}_cashtable.db）。
    """
    code = request.headers.get('X-Activate-Code', '').strip()
    if code:
        device_mac = request.headers.get('X-Device-Mac', '').strip()
        if not check_code_active(code, device_mac):
            raise HTTPException(status_code=403, detail='激活码无效或未激活')
        try:
            factory = get_tenant_session_factory(code)
        except FileNotFoundError:
            raise HTTPException(status_code=403, detail='激活库不存在，请在「全功能模式」重新激活')
        db = factory()
        try:
            yield db
        finally:
            db.close()
    else:
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()
