"""账单接口：查询筛选、单条增改删、批量写入去重。"""
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..schemas import (
    TransactionBatchIn,
    TransactionCreate,
    TransactionListOut,
    TransactionOut,
    TransactionUpdate,
)
from ..services import transaction_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['transactions'])


@router.get('/transactions/', response_model=TransactionListOut)
def query_transactions(
    month: str | None = Query(None, description='交易发生月份，如 2026-08'),
    category_id: int | None = None,
    keyword: str | None = Query(None, description='交易内容关键词'),
    created_by: int | None = None,
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
):
    """账单查询（见 5.1 筛选参数）。"""
    total, items = transaction_service.query_transactions(
        db,
        month=month,
        category_id=category_id,
        keyword=keyword,
        created_by=created_by,
        page=page,
        page_size=page_size,
    )
    return TransactionListOut(total=total, items=transaction_service.build_out(db, items))


@router.post('/transactions/', response_model=TransactionOut, status_code=201)
def create_transaction(data: TransactionCreate, db: Session = Depends(get_db)):
    """记一笔（保存时自动做规则晋升）。"""
    txn = transaction_service.create_transaction(db, data)
    return transaction_service.build_out(db, [txn])[0]


@router.patch('/transactions/{txn_id}/', response_model=TransactionOut)
def update_transaction(txn_id: int, data: TransactionUpdate, db: Session = Depends(get_db)):
    """修改账单。"""
    txn = transaction_service.update_transaction(db, txn_id, data)
    return transaction_service.build_out(db, [txn])[0]


@router.delete('/transactions/{txn_id}/', status_code=204)
def delete_transaction(txn_id: int, db: Session = Depends(get_db)):
    """删除账单。"""
    transaction_service.delete_transaction(db, txn_id)


@router.post('/transactions/batch/')
def batch_create_transactions(data: TransactionBatchIn, db: Session = Depends(get_db)):
    """批量写入（OCR/导入确认后一次提交，逐条按 external_id 去重）。"""
    return transaction_service.batch_create_transactions(db, data.rows, data.source)
