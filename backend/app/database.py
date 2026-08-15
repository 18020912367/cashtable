"""数据库引擎、会话与初始化。"""
from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .config import DATABASE_URL


class Base(DeclarativeBase):
    """ORM 模型基类。"""


# SQLite 需关闭跨线程检查（uvicorn 多线程访问同一连接）
engine = create_engine(DATABASE_URL, connect_args={'check_same_thread': False})
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def init_db() -> None:
    """建表并写入初始分类数据（幂等，可重复调用）。

    注意：seed 在函数内导入，避免与 seed.py 产生循环依赖。
    """
    from . import models  # noqa: F401  确保模型注册到 Base.metadata
    from .seed import seed_categories

    Base.metadata.create_all(bind=engine)
    seed_categories()
