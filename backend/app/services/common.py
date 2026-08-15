"""通用工具函数。"""
from datetime import datetime


def parse_dt(value: str) -> datetime:
    """宽松解析时间字符串：支持 '2026-08-07'、'2026-08-07 10:30' 等格式。"""
    try:
        return datetime.fromisoformat(value.replace('T', ' '))
    except ValueError:
        raise ValueError(f'无效的时间格式: {value}')
