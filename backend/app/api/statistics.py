"""统计接口。"""
import csv
import io
import re
from datetime import datetime, time

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from ..models import Category, Transaction, User
from ..schemas import MonthlyStatOut
from ..services import statistics_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['statistics'])

_DATE_RE = re.compile(r'^\d{4}-\d{2}-\d{2}$')


@router.get('/statistics/monthly/', response_model=list[MonthlyStatOut])
def monthly_summary(year: str, db: Session = Depends(get_db)):
    """按月收支合计（最简 GROUP BY）。"""
    return statistics_service.monthly_summary(db, year)


@router.get('/statistics/export/')
def export_statistics(
    start: str = Query(..., description='起始日期 YYYY-MM-DD'),
    end: str = Query(..., description='结束日期 YYYY-MM-DD'),
    db: Session = Depends(get_db),
):
    """导出区间内账单为 CSV（关联记账人名与分类名）。

    表头：日期, 类型, 金额, 分类, 记账人, 内容, 来源, 记录ID, 创建时间。
    """
    if not (_DATE_RE.match(start) and _DATE_RE.match(end)):
        raise HTTPException(status_code=400, detail='日期格式应为 YYYY-MM-DD')
    if start > end:
        raise HTTPException(status_code=400, detail='起始日期不能晚于结束日期')

    start_dt = datetime.combine(datetime.strptime(start, '%Y-%m-%d').date(), time.min)
    end_dt = datetime.combine(datetime.strptime(end, '%Y-%m-%d').date(), time.max)

    rows = db.query(Transaction, User.name, Category.name) \
        .outerjoin(User, User.id == Transaction.created_by) \
        .outerjoin(Category, Category.id == Transaction.category_id) \
        .filter(
            Transaction.happened_at >= start_dt,
            Transaction.happened_at <= end_dt,
        ) \
        .order_by(Transaction.happened_at.asc()) \
        .all()

    buf = io.StringIO()
    buf.write('\ufeff')  # UTF-8 BOM：Excel 打开不乱码
    writer = csv.writer(buf)
    writer.writerow(['日期', '类型', '金额', '分类', '记账人', '内容', '来源', '记录ID', '创建时间'])
    for txn, user_name, cat_name in rows:
        writer.writerow([
            txn.happened_at.strftime('%Y-%m-%d %H:%M'),
            '收入' if txn.amount > 0 else '支出',
            f'{txn.amount:.2f}',
            cat_name or '',
            user_name or '',
            txn.content,
            txn.source,
            txn.id,
            txn.created_at.strftime('%Y-%m-%d %H:%M'),
        ])

    filename = f'transactions_{start}_to_{end}.csv'
    return StreamingResponse(
        iter([buf.getvalue().encode('utf-8')]),
        media_type='text/csv; charset=utf-8',
        headers={'Content-Disposition': f'attachment; filename="{filename}"'},
    )
