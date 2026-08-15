"""AI 自动分类：固定规则 → 临时规则 → LLM（见设计文档 5.3）。

LLM 未配置或调用失败时自动降级，返回 source='none'，由 App 让用户手选分类。
"""
import json

import httpx
from sqlalchemy import func, literal, select
from sqlalchemy.orm import Session

from .. import config
from ..models import Category, CategoryRule, CategoryRuleTemp
from ..schemas import ClassifyResponse


def classify(db: Session, content: str) -> ClassifyResponse:
    """按交易内容返回推荐分类（分类优先级：固定规则 → 临时规则 → LLM）。"""
    content = content.strip()
    if not content:
        return ClassifyResponse()

    # 1. 固定规则命中：关键词越长越精确，其次按历史命中次数
    rule = db.scalars(
        select(CategoryRule)
        .where(literal(content).contains(CategoryRule.keyword))
        .order_by(
            func.length(CategoryRule.keyword).desc(), CategoryRule.usage_count.desc()
        )
        .limit(1)
    ).first()
    if rule:
        rule.usage_count += 1
        db.commit()
        category = db.get(Category, rule.category_id)
        return ClassifyResponse(
            category_id=rule.category_id,
            category_name=category.name if category else None,
            source='rule',
        )

    # 2. 临时规则命中：复用上次 LLM 建议，不重复调用
    temp = db.scalar(
        select(CategoryRuleTemp).where(
            CategoryRuleTemp.keyword == content,
            CategoryRuleTemp.status == 'pending',
        )
    )
    if temp:
        category = db.get(Category, temp.category_id)
        return ClassifyResponse(
            category_id=temp.category_id,
            category_name=category.name if category else None,
            source='temp',
        )

    # 3. 调 LLM：结果写入临时表（pending），等待用户保存账单时确认晋升
    cat_id = _llm_classify(db, content)
    if cat_id is not None:
        db.add(CategoryRuleTemp(keyword=content, category_id=cat_id, status='pending'))
        db.commit()
        category = db.get(Category, cat_id)
        return ClassifyResponse(
            category_id=cat_id,
            category_name=category.name if category else None,
            source='llm',
        )

    return ClassifyResponse()


def _llm_classify(db: Session, content: str) -> int | None:
    """调用 OpenAI 兼容 LLM（DeepSeek/通义等），返回分类 id；失败返回 None。"""
    if not (config.LLM_API_KEY and config.LLM_BASE_URL):
        return None

    # 候选：一级分类
    categories = db.scalars(
        select(Category).where(Category.parent_id.is_(None)).order_by(Category.sort)
    ).all()
    if not categories:
        return None
    valid_ids = {c.id for c in categories}
    candidates = ', '.join(f'{c.id}:{c.name}' for c in categories)
    prompt = (
        '你是账单分类助手。根据"交易内容"从候选分类中选择最匹配的一个。\n'
        f'候选分类（id:名称）：{candidates}\n'
        f'交易内容：{content}\n'
        '只返回 JSON，格式：{"category_id": <id>}'
    )
    try:
        resp = httpx.post(
            f"{config.LLM_BASE_URL.rstrip('/')}/chat/completions",
            headers={'Authorization': f'Bearer {config.LLM_API_KEY}'},
            json={
                'model': config.LLM_MODEL or 'deepseek-chat',
                'messages': [{'role': 'user', 'content': prompt}],
                'temperature': 0,
                'response_format': {'type': 'json_object'},
            },
            timeout=10,
        )
        resp.raise_for_status()
        text = resp.json()['choices'][0]['message']['content']
        cat_id = int(json.loads(text)['category_id'])
        return cat_id if cat_id in valid_ids else None
    except Exception:
        return None
