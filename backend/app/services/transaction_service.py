"""账单业务逻辑：单条增改删、查询筛选、批量写入去重、规则晋升联动。"""
from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Category, Transaction
from ..schemas import (
    TransactionCreate,
    TransactionOut,
    TransactionUpdate,
    fmt_dt,
)
from .common import parse_dt
from .rule_service import promote_rule_if_confirmed


def _check_category_exists(db: Session, category_id: int | None) -> None:
    """分类存在性校验（可空）。"""
    if category_id is not None and db.get(Category, category_id) is None:
        raise HTTPException(status_code=400, detail='分类不存在')


def _check_user_exists(db: Session, user_id: int | None) -> None:
    """记账人存在性校验（可空）。"""
    if user_id is not None:
        from ..models import User

        if db.get(User, user_id) is None:
            raise HTTPException(status_code=400, detail='记账人不存在')


def _check_external_id(db: Session, external_id: str) -> None:
    """交易单号去重校验：已存在则拒绝。"""
    exists = db.scalar(
        select(Transaction.id).where(Transaction.external_id == external_id)
    )
    if exists:
        raise HTTPException(status_code=409, detail='交易单号已存在（重复记账）')


def create_transaction(db: Session, data: TransactionCreate) -> Transaction:
    """记一笔：校验 + 去重 + 规则晋升。"""
    _check_category_exists(db, data.category_id)
    _check_user_exists(db, data.created_by)
    external_id = (data.external_id or '').strip()
    if external_id:
        _check_external_id(db, external_id)

    txn = Transaction(
        amount=data.amount,
        category_id=data.category_id,
        happened_at=parse_dt(data.happened_at),
        created_by=data.created_by,
        content=(data.content or '').strip(),
        source=data.source,
        external_id=external_id,
    )
    db.add(txn)
    # 分类来自临时规则且用户未修改 → 晋升为固定规则
    if txn.category_id:
        promote_rule_if_confirmed(db, txn.content, txn.category_id)
    db.commit()
    db.refresh(txn)
    return txn


def update_transaction(db: Session, txn_id: int, data: TransactionUpdate) -> Transaction:
    """修改账单。"""
    txn = db.get(Transaction, txn_id)
    if txn is None:
        raise HTTPException(status_code=404, detail='账单不存在')

    payload = data.model_dump(exclude_unset=True)
    if 'category_id' in payload:
        _check_category_exists(db, payload['category_id'])
    if 'created_by' in payload:
        _check_user_exists(db, payload['created_by'])
    if 'happened_at' in payload:
        payload['happened_at'] = parse_dt(payload['happened_at'])
    if 'external_id' in payload:
        external_id = (payload['external_id'] or '').strip()
        if external_id and external_id != txn.external_id:
            _check_external_id(db, external_id)
        payload['external_id'] = external_id

    for field, value in payload.items():
        setattr(txn, field, value)
    txn.updated_at = func.now()
    db.commit()
    db.refresh(txn)
    return txn


def delete_transaction(db: Session, txn_id: int) -> None:
    """删除账单。"""
    txn = db.get(Transaction, txn_id)
    if txn is None:
        raise HTTPException(status_code=404, detail='账单不存在')
    db.delete(txn)
    db.commit()


def query_transactions(
    db: Session,
    month: str | None = None,
    category_id: int | None = None,
    keyword: str | None = None,
    created_by: int | None = None,
    page: int = 1,
    page_size: int = 50,
) -> tuple[int, list[Transaction]]:
    """账单查询：按条件筛选，happened_at 倒序 + 分页。"""
    stmt = select(Transaction)
    if month:
        stmt = stmt.where(Transaction.happened_at.like(f'{month}%'))
    if category_id is not None:
        stmt = stmt.where(Transaction.category_id == category_id)
    if keyword:
        stmt = stmt.where(Transaction.content.contains(keyword))
    if created_by is not None:
        stmt = stmt.where(Transaction.created_by == created_by)

    total = db.scalar(select(func.count()).select_from(stmt.subquery()))
    items = db.scalars(
        stmt.order_by(Transaction.happened_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
    ).all()
    return total, items


def batch_create_transactions(
    db: Session, rows: list[TransactionCreate], source: str
) -> dict:
    """批量写入：按 external_id 去重，返回导入统计（核心逻辑，见 5.2）。"""
    imported = 0
    skipped = 0
    for row in rows:
        payload = row.model_dump()
        external_id = (payload.get('external_id') or '').strip()
        if external_id:
            exists = db.scalar(
                select(Transaction.id).where(Transaction.external_id == external_id)
            )
            if exists:
                skipped += 1
                continue
        txn = Transaction(
            amount=payload['amount'],
            category_id=payload.get('category_id'),
            happened_at=parse_dt(payload['happened_at']),
            created_by=payload.get('created_by'),
            content=(payload.get('content') or '').strip(),
            source=payload.get('source') or source,
            external_id=external_id,
        )
        db.add(txn)
        if txn.category_id:
            promote_rule_if_confirmed(db, txn.content, txn.category_id)
        imported += 1
    db.commit()
    return {'total': len(rows), 'imported': imported, 'skipped': skipped}


def build_out(db: Session, txns: list[Transaction]) -> list[TransactionOut]:
    """组装输出模型：补充分类名称。"""
    ids = {t.category_id for t in txns if t.category_id}
    names: dict[int, str] = {}
    if ids:
        for c in db.scalars(select(Category).where(Category.id.in_(ids))):
            names[c.id] = c.name
    return [
        TransactionOut(
            id=t.id,
            amount=t.amount,
            category_id=t.category_id,
            category_name=names.get(t.category_id),
            happened_at=fmt_dt(t.happened_at),
            created_at=fmt_dt(t.created_at),
            created_by=t.created_by,
            content=t.content,
            source=t.source,
            external_id=t.external_id,
        )
        for t in txns
    ]
