"""激活码管理（keycode.db，独立于主库 cashtable.db）。

表「全功能码」：激活码 / 激活时间 / 激活设备 / 是否已激活。
激活码由管理员通过 scripts/gen_keycode.py 生成后手动发放；
App 全功能模式激活时校验并写入激活信息。
"""
from datetime import datetime

from sqlalchemy import Integer, String, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

from .config import KEYCODE_DB_PATH

# SQLite 需关闭跨线程检查（uvicorn 多线程访问同一连接）
_keycode_engine = create_engine(
    f'sqlite:///{KEYCODE_DB_PATH}', connect_args={'check_same_thread': False}
)
_keycode_session = sessionmaker(bind=_keycode_engine, autoflush=False, autocommit=False)


class KeycodeBase(DeclarativeBase):
    """keycode.db 的 ORM 基类（独立于主库 Base）。"""


class ActivationCode(KeycodeBase):
    """全功能码：激活码 + 激活设备 + 激活时间。"""

    __tablename__ = '全功能码'

    code: Mapped[str] = mapped_column(String(64), primary_key=True)  # 激活码
    activated_at: Mapped[str] = mapped_column(
        String(32), default='', nullable=False
    )  # 激活时间 'YYYY-MM-DD HH:MM:SS'（空=未激活）
    device_mac: Mapped[str] = mapped_column(
        String(64), default='', nullable=False
    )  # 激活设备标识（App 端用 ANDROID_ID，Android 10+ 无法可靠取真实 MAC）
    is_activated: Mapped[int] = mapped_column(Integer, default=0, nullable=False)  # 0/1


def init_keycode_db() -> None:
    """建表（幂等）。"""
    KeycodeBase.metadata.create_all(bind=_keycode_engine)
    _init_done[0] = True


_init_done = [False]


def _ensure_init() -> None:
    """惰性建表：独立调用（脚本/测试）时表可能未创建。"""
    if not _init_done[0]:
        init_keycode_db()


def now_str() -> str:
    return datetime.now().strftime('%Y-%m-%d %H:%M:%S')


def get_code(code: str) -> ActivationCode | None:
    """按激活码查记录。"""
    _ensure_init()
    db = _keycode_session()
    try:
        return db.get(ActivationCode, code)
    finally:
        db.close()


def check_code_active(code: str, device_mac: str = '') -> bool:
    """激活码有效且已激活返回 True。

    激活码允许重复使用（多设备共用同一数据库），不校验设备标识；
    device_mac 参数保留仅为兼容旧调用方。
    """
    row = get_code(code)
    if row is None or row.is_activated != 1:
        return False
    return True


def activate_code(code: str, device_mac: str) -> None:
    """将激活码标记为已激活（记录激活时间与设备）。"""
    _ensure_init()
    db = _keycode_session()
    try:
        row = db.get(ActivationCode, code)
        if row is None:
            row = ActivationCode(code=code)
            db.add(row)
        row.is_activated = 1
        row.device_mac = device_mac
        row.activated_at = now_str()
        db.commit()
    finally:
        db.close()


def create_code(code: str) -> None:
    """新增激活码（未激活）。"""
    _ensure_init()
    db = _keycode_session()
    try:
        if db.get(ActivationCode, code) is None:
            db.add(ActivationCode(code=code))
            db.commit()
    finally:
        db.close()
