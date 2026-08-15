"""分类规则业务逻辑：固定规则 CRUD、临时规则查看/确认/删除、保存账单时的晋升。"""
from datetime import datetime

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Category, CategoryRule, CategoryRuleTemp
from ..schemas import RuleOut, RuleTempOut, fmt_dt


def list_rules(db: Session) -> list[RuleOut]:
    """固定规则列表：命中次数多的在前。"""
    rules = db.scalars(
        select(CategoryRule).order_by(CategoryRule.usage_count.desc(), CategoryRule.id)
    ).all()
    return [RuleOut.model_validate(r, from_attributes=True) for r in rules]


def create_rule(db: Session, keyword: str, category_id: int, source: str) -> RuleOut:
    """新增固定规则（关键词唯一）。"""
    keyword = keyword.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail='关键词不能为空')
    if db.get(Category, category_id) is None:
        raise HTTPException(status_code=400, detail='分类不存在')
    exists = db.scalar(select(CategoryRule.id).where(CategoryRule.keyword == keyword))
    if exists:
        raise HTTPException(status_code=409, detail='该关键词规则已存在')

    rule = CategoryRule(keyword=keyword, category_id=category_id, source=source)
    db.add(rule)
    db.commit()
    db.refresh(rule)
    return RuleOut.model_validate(rule, from_attributes=True)


def delete_rule(db: Session, rule_id: int) -> None:
    """删除固定规则。"""
    rule = db.get(CategoryRule, rule_id)
    if rule is None:
        raise HTTPException(status_code=404, detail='规则不存在')
    db.delete(rule)
    db.commit()


def list_pending(db: Session) -> list[RuleTempOut]:
    """待确认的临时规则（LLM 建议列表）。"""
    temps = db.scalars(
        select(CategoryRuleTemp)
        .where(CategoryRuleTemp.status == 'pending')
        .order_by(CategoryRuleTemp.created_at.desc())
    ).all()
    return [
        RuleTempOut(
            id=t.id,
            keyword=t.keyword,
            category_id=t.category_id,
            status=t.status,
            created_at=fmt_dt(t.created_at),
            updated_at=fmt_dt(t.updated_at),
        )
        for t in temps
    ]


def confirm_temp(db: Session, temp_id: int) -> RuleOut:
    """采纳临时规则：晋升为固定规则。"""
    temp = db.get(CategoryRuleTemp, temp_id)
    if temp is None:
        raise HTTPException(status_code=404, detail='临时规则不存在')
    if temp.status != 'pending':
        raise HTTPException(status_code=400, detail='该临时规则已处理')

    exists = db.scalar(
        select(CategoryRule.id).where(CategoryRule.keyword == temp.keyword)
    )
    if not exists:
        db.add(
            CategoryRule(
                keyword=temp.keyword, category_id=temp.category_id, source='llm'
            )
        )
    temp.status = 'confirmed'
    temp.updated_at = datetime.now()
    db.commit()
    rule = db.scalar(
        select(CategoryRule).where(CategoryRule.keyword == temp.keyword)
    )
    return RuleOut.model_validate(rule, from_attributes=True)


def delete_temp(db: Session, temp_id: int) -> None:
    """忽略临时规则（LLM 建议不采纳）。"""
    temp = db.get(CategoryRuleTemp, temp_id)
    if temp is None:
        raise HTTPException(status_code=404, detail='临时规则不存在')
    temp.status = 'discarded'
    db.commit()


def promote_rule_if_confirmed(db: Session, content: str, category_id: int | None) -> None:
    """保存账单时调用：分类来自临时规则且未被修改 → 晋升为固定规则（见 5.3）。

    注意：不提交事务，由调用方统一 commit。
    """
    if not content or category_id is None:
        return
    rule = db.scalar(
        select(CategoryRuleTemp).where(
            CategoryRuleTemp.keyword == content,
            CategoryRuleTemp.status == 'pending',
        )
    )
    if not rule:
        return
    if rule.category_id == category_id:  # 用户认可了 LLM 的建议
        rule.status = 'confirmed'
        exists = db.scalar(
            select(CategoryRule.id).where(CategoryRule.keyword == content)
        )
        if not exists:
            db.add(CategoryRule(keyword=content, category_id=category_id, source='llm'))
    else:  # 用户改选了分类：丢弃 LLM 建议
        rule.status = 'discarded'
