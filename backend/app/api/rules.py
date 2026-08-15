"""分类规则接口：固定规则 CRUD + 待确认临时规则管理。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import RuleCreate, RuleOut, RuleTempOut
from ..services import rule_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['category-rules'])


@router.get('/category-rules/pending/', response_model=list[RuleTempOut])
def list_pending(db: Session = Depends(get_db)):
    """待确认的临时规则（LLM 建议列表）。"""
    return rule_service.list_pending(db)


@router.post('/category-rules/temp/{temp_id}/confirm', response_model=RuleOut)
def confirm_temp(temp_id: int, db: Session = Depends(get_db)):
    """采纳临时规则：晋升为固定规则。"""
    return rule_service.confirm_temp(db, temp_id)


@router.delete('/category-rules/temp/{temp_id}/', status_code=204)
def delete_temp(temp_id: int, db: Session = Depends(get_db)):
    """忽略临时规则（LLM 建议不采纳）。"""
    rule_service.delete_temp(db, temp_id)


@router.get('/category-rules/', response_model=list[RuleOut])
def list_rules(db: Session = Depends(get_db)):
    """固定分类规则列表。"""
    return rule_service.list_rules(db)


@router.post('/category-rules/', response_model=RuleOut, status_code=201)
def create_rule(data: RuleCreate, db: Session = Depends(get_db)):
    """新增固定规则。"""
    return rule_service.create_rule(db, data.keyword, data.category_id, data.source)


@router.delete('/category-rules/{rule_id}/', status_code=204)
def delete_rule(rule_id: int, db: Session = Depends(get_db)):
    """删除固定规则。"""
    rule_service.delete_rule(db, rule_id)
