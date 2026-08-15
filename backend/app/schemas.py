"""Pydantic 模型：请求体与响应模型。"""
from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field


def fmt_dt(dt: Optional[datetime]) -> str:
    """时间统一输出为 'YYYY-MM-DD HH:MM' 字符串。"""
    return dt.strftime('%Y-%m-%d %H:%M') if dt else ''


# ---------- 记账人 ----------

class UserCreate(BaseModel):
    name: str = Field(min_length=1, max_length=50)


class UserOut(BaseModel):
    id: int
    name: str
    created_at: str


# ---------- 分类 ----------

class CategoryCreate(BaseModel):
    name: str = Field(min_length=1, max_length=30)
    kind: Literal['expense', 'income']
    parent_id: Optional[int] = None
    icon: str = ''
    sort: int = 0


class CategoryUpdate(BaseModel):
    name: Optional[str] = None
    parent_id: Optional[int] = None
    icon: Optional[str] = None
    sort: Optional[int] = None


class CategoryOut(BaseModel):
    id: int
    name: str
    parent_id: Optional[int] = None
    kind: str
    icon: str
    sort: int


# ---------- 账单 ----------

class TransactionCreate(BaseModel):
    amount: float
    category_id: Optional[int] = None
    happened_at: str = Field(description='交易发生日期，如 2026-08-07 10:30')
    created_by: Optional[int] = None
    content: str = ''
    source: str = 'manual'
    external_id: str = ''


class TransactionUpdate(BaseModel):
    amount: Optional[float] = None
    category_id: Optional[int] = None
    happened_at: Optional[str] = None
    created_by: Optional[int] = None
    content: Optional[str] = None
    source: Optional[str] = None
    external_id: Optional[str] = None


class TransactionOut(BaseModel):
    id: int
    amount: float
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    happened_at: str
    created_at: str
    created_by: Optional[int] = None
    content: str
    source: str
    external_id: str


class TransactionListOut(BaseModel):
    total: int
    items: list[TransactionOut]


class TransactionBatchIn(BaseModel):
    rows: list[TransactionCreate]
    source: str = 'ocr'


# ---------- 导入记录 ----------

class ImportIn(BaseModel):
    source: str = Field(pattern='^(ocr|wechat|alipay)$')
    rows: list[TransactionCreate]


class ImportOut(BaseModel):
    id: int
    source: str
    status: str
    total: int
    imported: int
    skipped: int
    created_at: str


# ---------- 分类规则 ----------

class RuleCreate(BaseModel):
    keyword: str = Field(min_length=1, max_length=100)
    category_id: int
    source: str = 'manual'


class RuleOut(BaseModel):
    id: int
    keyword: str
    category_id: int
    source: str
    usage_count: int


class RuleTempOut(BaseModel):
    id: int
    keyword: str
    category_id: int
    status: str
    created_at: str
    updated_at: str


# ---------- AI 分类 ----------

class ClassifyRequest(BaseModel):
    content: str = Field(min_length=1, max_length=100)


class ClassifyResponse(BaseModel):
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    source: str = 'none'  # rule / temp / llm / none


# ---------- 统计 ----------

class MonthlyStatOut(BaseModel):
    month: str
    kind: str  # expense / income
    total: float
