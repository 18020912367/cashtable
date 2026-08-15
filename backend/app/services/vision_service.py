"""截图识别服务（P3）：调用多模态大模型识别支付截图，返回结构化账单字段。

分类列表动态来自 categories 表（见 category_rule_provider），识别要求
参照 docs/viewrule.txt；LLM 未配置时抛错降级，由接口层返回 400。
"""
import base64
import json
import re
import time
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from sqlalchemy.orm import Session

from ..config import LLM_API_KEY, LLM_BASE_URL, LLM_FALLBACK_MODELS, LLM_MODEL
from ..models import Category
from .category_rule_provider import get_category_rule_text

SYSTEM_PROMPT = """你是一个账单信息提取助手。你的任务是从用户提供的支付截图中，提取出以下4个字段：
1. 交易时间
2. 交易内容（即商品或服务描述或对方名称），如现场扫码支付，只有店铺信息没有商品信息则将店铺信息作为内容，如果有交易备注则备注也组合成内容
3. 交易类型（调用本项目内的已有分类表中的信息）
4. 金额（带正负号）

**严格要求**：
- 你必须只输出一个合法的JSON对象，不要包含任何其他文字、解释或标记。
- JSON的key必须严格为：transaction_time, description, transaction_type, amount。
- 如果图片中无法识别某个字段，请将该字段的值设为null。
- 金额只保留数字，不要包含货币符号（如¥、$）。
"""


def _match_category(db: Session, name: str | None) -> tuple[int | None, str | None]:
    """把 LLM 返回的分类名映射到 categories 表（精确 → 包含 → 拼音无关的模糊）。"""
    if not name:
        return None, None
    kw = name.strip()
    if not kw:
        return None, None
    cat = db.query(Category).filter(Category.name == kw).first()
    if cat:
        return cat.id, cat.kind
    cat = db.query(Category).filter(Category.name.like(f'%{kw}%')).first()
    if cat:
        return cat.id, cat.kind
    for row in db.query(Category).all():
        if kw in row.name or row.name in kw:
            return row.id, row.kind
    return None, None


def _normalize_time(raw) -> str | None:
    """把 LLM 返回的时间规范为 'YYYY-MM-DD HH:MM'。"""
    if not raw:
        return None
    s = str(raw).strip().strip('"\'')
    if not s:
        return None
    for fmt in ('%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M', '%Y/%m/%d %H:%M',
                '%Y-%m-%d', '%Y/%m/%d'):
        try:
            return datetime.strptime(s, fmt).strftime('%Y-%m-%d %H:%M')
        except ValueError:
            continue
    # 兜底：提取数字段拼凑
    nums = re.findall(r'\d{4}[-/]\d{1,2}[-/]\d{1,2}(\s+\d{1,2}:\d{2})?', s)
    return None


def _normalize_amount(raw) -> float | None:
    """金额规范化：去掉货币符号/逗号，正数=收入、负数=支出。"""
    if raw is None:
        return None
    s = str(raw).strip().strip('"\'')
    if not s:
        return None
    s = re.sub(r'[¥￥$元]', '', s)
    s = s.replace('，', '').replace(',', '')
    try:
        return float(s)
    except ValueError:
        return None


def _call_llm(image_bytes: bytes, mime: str, rules: str) -> dict:
    """调用多模态大模型，返回原始识别 JSON。

    依次尝试主模型与备用模型；单个模型 429 时退避重试后切换下一个。
    """
    models = [m for m in [LLM_MODEL, *LLM_FALLBACK_MODELS] if m]
    if not models:
        raise RuntimeError('未配置大模型模型名（LLMAKPmode / LLM_MODEL）')
    last_err: Exception | None = None
    for model in dict.fromkeys(models):  # 去重保序
        payload = {
            'model': model,
            'messages': [
                {'role': 'system', 'content': SYSTEM_PROMPT},
                {'role': 'user', 'content': [
                    {'type': 'text',
                     'text': f'可用分类（一级分类，请从中选择最匹配的作为 transaction_type）：\n{rules}\n\n'
                             '请提取这张支付截图中的交易信息，严格按照以下JSON格式输出：\n'
                             '{"transaction_time": "交易时间", "description": "交易内容", "transaction_type": "交易类型", "amount": "金额"}\n\n'
                             '只输出JSON，不要有其他内容。'},
                    {'type': 'image_url',
                     'image_url': {'url': f'data:{mime};base64,{base64.b64encode(image_bytes).decode()}'}},
                ]},
            ],
            'temperature': 0.1,
        }
        req = Request(
            f'{LLM_BASE_URL}/chat/completions',
            data=json.dumps(payload).encode('utf-8'),
            headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {LLM_API_KEY}'},
            method='POST',
        )
        data: dict | None = None
        rate_limited = False
        for attempt in range(3):
            try:
                with urlopen(req, timeout=90) as resp:
                    data = json.loads(resp.read().decode('utf-8'))
                    break
            except HTTPError as e:
                body = e.read().decode('utf-8', 'ignore')[:300]
                if e.code == 429:
                    rate_limited = True
                    last_err = RuntimeError(f'大模型接口限流 HTTP {e.code}: {body}')
                    if attempt < 2:
                        time.sleep(2 ** (attempt + 1))
                        continue
                    break  # 本模型重试完仍 429，切下一个模型
                last_err = RuntimeError(f'大模型接口调用失败 HTTP {e.code}: {body}')
                raise last_err from e
            except URLError as e:
                last_err = RuntimeError(f'大模型网络连接失败: {e.reason}')
                if attempt < 2:
                    time.sleep(2 ** (attempt + 1))
                    continue
                break
        if data is not None:
            content = data['choices'][0]['message']['content']
            content = content.strip()
            # 容错：去掉 ```json 围栏
            m = re.search(r'\{.*\}', content, re.DOTALL)
            if not m:
                raise RuntimeError(f'大模型返回非 JSON: {content[:200]}')
            return json.loads(m.group(0))
        # 本模型 429 限流（非致命错误）时尝试下一个模型
        if rate_limited and last_err is not None:
            print(f'[vision] 模型 {model} 限流，切换备用模型', flush=True)
            continue
        raise last_err
    raise last_err


def extract_transaction(db: Session, image_bytes: bytes, mime: str = 'image/jpeg') -> dict:
    """识别支付截图，返回与 VisionExtractResponse 对齐的字段 dict。"""
    if not LLM_API_KEY or not LLM_MODEL:
        raise RuntimeError('未配置大模型（LLMAPIKEY / LLMAKPmode），无法识别截图')

    rules = get_category_rule_text(db)
    raw = _call_llm(image_bytes, mime, rules)

    amount = _normalize_amount(raw.get('amount'))
    kind = 'expense' if amount is not None and amount < 0 else 'income'
    category_id, cat_kind = _match_category(db, raw.get('transaction_type'))
    # 收支方向以分类为准（分类一定时），金额符号兜底
    if cat_kind:
        kind = cat_kind
        if amount is not None and kind == 'expense' and amount > 0:
            amount = -amount
        elif kind == 'income' and amount is not None and amount < 0:
            amount = -amount

    # 匹配到分类时用标准分类名回填 transaction_type（剔除 LLM 夹带的注释）
    type_name = category_id and db.query(Category.name).filter(Category.id == category_id).scalar() or None

    return {
        'transaction_time': _normalize_time(raw.get('transaction_time')),
        'description': (raw.get('description') or '').strip() or None,
        'transaction_type': type_name or (raw.get('transaction_type') or '').strip() or None,
        'category_id': category_id,
        'kind': kind,
        'amount': amount,
        'source': 'llm',
    }
