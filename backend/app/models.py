"""ORM 模型：对应设计文档 4.1 的 SQLite 表结构。"""
from datetime import datetime

from sqlalchemy import DateTime, Float, ForeignKey, Index, Integer, String, text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


class User(Base):
    """记账人表：手工写入，不提供注册/管理界面。"""

    __tablename__ = 'users'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(50), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )


class Category(Base):
    """分类表：支出/收入各一套，支持一级/二级（parent_id）。"""

    __tablename__ = 'categories'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(30), nullable=False)
    parent_id: Mapped[int | None] = mapped_column(ForeignKey('categories.id'))
    kind: Mapped[str] = mapped_column(String(10), nullable=False)  # expense / income
    icon: Mapped[str] = mapped_column(String(50), default='', nullable=False)
    sort: Mapped[int] = mapped_column(Integer, default=0, nullable=False)


class Transaction(Base):
    """账单表：核心表。金额正负表达收支（负=支出，正=收入）。"""

    __tablename__ = 'transactions'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    amount: Mapped[float] = mapped_column(Float, nullable=False)
    category_id: Mapped[int | None] = mapped_column(ForeignKey('categories.id'))
    happened_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )
    created_by: Mapped[int | None] = mapped_column(ForeignKey('users.id'))
    content: Mapped[str] = mapped_column(String(100), default='', nullable=False)
    source: Mapped[str] = mapped_column(
        String(20), default='manual', nullable=False
    )  # manual / ocr / import / auto
    external_id: Mapped[str] = mapped_column(String(64), default='', nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )

    __table_args__ = (
        # 去重约束：同一交易单号只允许一条（external_id 为空不参与）
        Index(
            'idx_txn_dedup',
            'external_id',
            unique=True,
            sqlite_where=text("external_id <> ''"),
        ),
    )


class CategoryRule(Base):
    """固定分类规则表：关键词 → 分类（含用户确认晋升的 LLM 结果）。"""

    __tablename__ = 'category_rules'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    keyword: Mapped[str] = mapped_column(String(100), nullable=False, unique=True)
    category_id: Mapped[int] = mapped_column(ForeignKey('categories.id'), nullable=False)
    source: Mapped[str] = mapped_column(
        String(20), default='manual', nullable=False
    )  # manual / llm
    usage_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )


class CategoryRuleTemp(Base):
    """临时分类规则表：LLM 建议结果先落这里，用户认可后晋升到 category_rules。"""

    __tablename__ = 'category_rules_temp'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    keyword: Mapped[str] = mapped_column(String(100), nullable=False, unique=True)
    category_id: Mapped[int] = mapped_column(ForeignKey('categories.id'), nullable=False)
    status: Mapped[str] = mapped_column(
        String(20), default='pending', nullable=False
    )  # pending / confirmed / discarded
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )


class ImportRecord(Base):
    """导入记录表：每次批量写入留痕。"""

    __tablename__ = 'import_records'

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    source: Mapped[str] = mapped_column(String(20), nullable=False)  # ocr / wechat / alipay
    status: Mapped[str] = mapped_column(String(20), default='done', nullable=False)
    total: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    imported: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    skipped: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.now, nullable=False
    )
