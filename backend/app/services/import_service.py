"""导入业务逻辑：批量写入 + 留痕 import_records。"""
from sqlalchemy.orm import Session

from ..models import ImportRecord
from ..schemas import ImportOut, TransactionCreate, fmt_dt
from .transaction_service import batch_create_transactions


def create_import(db: Session, source: str, rows: list[TransactionCreate]) -> ImportOut:
    """CSV/OCR 解析结果入库：逐条去重，写入导入记录。"""
    stats = batch_create_transactions(db, rows, source)
    record = ImportRecord(
        source=source,
        status='done',
        total=stats['total'],
        imported=stats['imported'],
        skipped=stats['skipped'],
    )
    db.add(record)
    db.commit()
    db.refresh(record)
    return ImportOut(
        id=record.id,
        source=record.source,
        status=record.status,
        total=record.total,
        imported=record.imported,
        skipped=record.skipped,
        created_at=fmt_dt(record.created_at),
    )
