"""分类规则提供器：把 categories 表动态整理成 LLM 的标准输入规则文本。

categories 表会随用户在 App 内增删改而变化，因此这里定时（TTL 30 分钟）
重新读取表并缓存，保证 LLM 提示词中的分类列表始终与库内一致。
"""
import time

from sqlalchemy.orm import Session

from ..models import Category

# 缓存：{'ts': 上次更新时间, 'text': 规则文本}
_CACHE: dict = {}
_TTL_SECONDS = 30 * 60


def get_category_rule_text(db: Session) -> str:
    """返回分类规则文本（一级 + 二级，含 id 与收支方向）。"""
    now = time.time()
    if _CACHE.get('ts') and now - _CACHE['ts'] < _TTL_SECONDS:
        return _CACHE['text']

    categories = db.query(Category).order_by(Category.kind, Category.sort, Category.id).all()
    parents = [c for c in categories if c.parent_id is None]
    children = [c for c in categories if c.parent_id is not None]

    lines = []
    for p in parents:
        subs = [c for c in children if c.parent_id == p.id]
        if subs:
            sub_names = '、'.join(f'{c.name}(id={c.id})' for c in subs)
            lines.append(f'{p.name}(id={p.id}, {p.kind})，二级分类：{sub_names}')
        else:
            lines.append(f'{p.name}(id={p.id}, {p.kind})')

    text = '\n'.join(lines)
    _CACHE['ts'] = now
    _CACHE['text'] = text
    return text
