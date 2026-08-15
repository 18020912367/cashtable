"""AI 分类接口。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import ClassifyRequest, ClassifyResponse
from ..services import classify_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['ai'])


@router.post('/ai/classify/', response_model=ClassifyResponse)
def classify(data: ClassifyRequest, db: Session = Depends(get_db)):
    """按交易内容返回推荐分类（固定规则 → 临时规则 → LLM）。"""
    return classify_service.classify(db, data.content)
