"""数据库引擎、会话与初始化。

支持两种库：
- 默认库：backend/data/cashtable.db（本地/自定义模式后端使用）
- 租户库：全功能模式按激活码路由到 ACTIVATED_DB_DIR/{激活码}_cashtable.db
  （依赖 get_db 中的 X-Activate-Code 请求头切换，见 api/deps.py）
"""
import threading
from pathlib import Path

from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from . import config
from .config import DATABASE_URL


class Base(DeclarativeBase):
    """ORM 模型基类。"""


# SQLite 需关闭跨线程检查（uvicorn 多线程访问同一连接）
engine = create_engine(DATABASE_URL, connect_args={'check_same_thread': False})
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)

# 租户库 engine 缓存：激活码 → (engine, SessionLocal 工厂)
_tenant_lock = threading.Lock()
_tenant_factories: dict[str, tuple[Engine, sessionmaker]] = {}


def activated_db_path(code: str) -> Path:
    """激活码对应的租户库文件路径（{激活码}_cashtable.db）。"""
    return config.ACTIVATED_DB_DIR / f'{code}_cashtable.db'


def get_tenant_session_factory(code: str) -> sessionmaker:
    """返回激活码对应的 Session 工厂（按激活码缓存 engine）。

    租户库文件不存在时抛 FileNotFoundError，由依赖层转 403。
    """
    with _tenant_lock:
        entry = _tenant_factories.get(code)
        if entry is None:
            db_file = activated_db_path(code)
            if not db_file.exists():
                raise FileNotFoundError(db_file)
            tengine = create_engine(
                f'sqlite:///{db_file}',
                connect_args={'check_same_thread': False, 'timeout': 30},
            )
            factory = sessionmaker(bind=tengine, autoflush=False, autocommit=False)
            entry = (tengine, factory)
            _tenant_factories[code] = entry
        return entry[1]


def drop_tenant_engine(code: str) -> None:
    """释放激活码对应的租户 engine 连接池（覆盖激活库文件前必须调用）。"""
    with _tenant_lock:
        entry = _tenant_factories.pop(code, None)
    if entry is not None:
        entry[0].dispose()


def init_db() -> None:
    """建表并写入初始分类数据（幂等，可重复调用）。

    注意：seed 在函数内导入，避免与 seed.py 产生循环依赖。
    """
    from . import models  # noqa: F401  确保模型注册到 Base.metadata
    from .seed import seed_categories

    Base.metadata.create_all(bind=engine)
    seed_categories()
