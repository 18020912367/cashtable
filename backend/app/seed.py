"""初始数据：分类表默认数据（对应设计文档 4.3）。"""
from sqlalchemy import select

from .database import SessionLocal
from .models import Category

# 支出一级分类（顺序即展示顺序；"其他"固定放最后）
EXPENSE_CATEGORIES = [
    '三餐', '零食', '衣服', '交通', '旅游', '孩子', '宠物', '水电网燃气', '日用品',
    '家具家电', '住房', '人情往来', '健身运动', '医疗', '娱乐', '其他',
]

# 收入一级分类
INCOME_CATEGORIES = ['退款', '工资', '其他']


def seed_categories() -> None:
    """按名称幂等补齐初始分类：只插入缺失项，已有数据与 sort 不受影响。"""
    db = SessionLocal()
    try:
        for sort, name in enumerate(EXPENSE_CATEGORIES, start=1):
            exists = db.scalar(
                select(Category).where(Category.kind == 'expense', Category.name == name)
            )
            if exists is None:
                db.add(Category(name=name, kind='expense', sort=sort))
        for sort, name in enumerate(INCOME_CATEGORIES, start=1):
            exists = db.scalar(
                select(Category).where(Category.kind == 'income', Category.name == name)
            )
            if exists is None:
                db.add(Category(name=name, kind='income', sort=sort))
        db.commit()
    finally:
        db.close()
