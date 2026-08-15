"""AI 接口：交易分类 + 截图识别（P3）。"""
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy.orm import Session

from ..schemas import ClassifyRequest, ClassifyResponse, VisionExtractResponse
from ..services import classify_service, vision_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['ai'])


@router.post('/ai/classify/', response_model=ClassifyResponse)
def classify(data: ClassifyRequest, db: Session = Depends(get_db)):
    """按交易内容返回推荐分类（固定规则 → 临时规则 → LLM）。"""
    return classify_service.classify(db, data.content)


@router.post('/ai/extract/', response_model=VisionExtractResponse)
async def extract_transaction(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    """截图记账（P3）：上传支付截图，返回交易时间/内容/类型/金额。"""
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail='图片内容为空')
    try:
        mime = file.content_type or 'image/jpeg'
        result = vision_service.extract_transaction(db, content, mime)
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    return result
