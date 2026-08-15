"""统计业务逻辑：按月收支合计（最简 GROUP BY，见设计文档 5.4）。"""
import re

from fastapi import HTTPException
from sqlalchemy import case, func, select
from sqlalchemy.orm import Session

from ..models import Transaction
from ..schemas import MonthlyStatOut


def monthly_summary(db: Session, year: str) -> list[MonthlyStatOut]:
    """按年分组统计每月收支合计（金额为负=支出，为正=收入）。"""
    if not re.fullmatch(r'\d{4}', year or ''):
        raise HTTPException(status_code=400, detail='year 须为 4 位年份，如 2026')

    stmt = (
        select(
            func.substr(Transaction.happened_at, 1, 7).label('month'),
            case((Transaction.amount < 0, 'expense'), else_='income').label('kind'),
            func.sum(Transaction.amount).label('total'),
        )
        .where(Transaction.happened_at.like(f'{year}%'))
        .group_by('month', 'kind')
        .order_by('month')
    )
    rows = db.execute(stmt).all()
    return [MonthlyStatOut(month=r.month, kind=r.kind, total=r.total) for r in rows]
