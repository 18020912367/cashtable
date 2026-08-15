"""导入接口：批量写入 + 留痕。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import ImportIn, ImportOut
from ..services import import_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['imports'])


@router.post('/imports/', response_model=ImportOut, status_code=201)
def create_import(data: ImportIn, db: Session = Depends(get_db)):
    """微信/支付宝/OCR 解析结果入库（逐条去重 + 导入记录留痕）。"""
    return import_service.create_import(db, data.source, data.rows)
