"""分类业务逻辑：列表、增改删，删除前校验引用。"""
from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Category, Transaction
from ..schemas import CategoryCreate, CategoryUpdate


def list_categories(
    db: Session, kind: str | None = None, parent_id: int | None = None
) -> list[Category]:
    """分类列表：按 kind / parent_id 过滤，sort+id 排序。"""
    stmt = select(Category).order_by(Category.sort, Category.id)
    if kind:
        stmt = stmt.where(Category.kind == kind)
    if parent_id is not None:
        stmt = stmt.where(Category.parent_id == parent_id)
    return db.scalars(stmt).all()


def create_category(db: Session, data: CategoryCreate) -> Category:
    """新增分类（支持二级分类，parent_id 校验）。"""
    if data.parent_id is not None:
        parent = db.get(Category, data.parent_id)
        if parent is None:
            raise HTTPException(status_code=400, detail='父分类不存在')
        if parent.parent_id is not None:
            raise HTTPException(status_code=400, detail='只支持一级/二级分类')
    category = Category(**data.model_dump())
    db.add(category)
    db.commit()
    db.refresh(category)
    return category


def update_category(db: Session, cid: int, data: CategoryUpdate) -> Category:
    """修改分类。"""
    category = db.get(Category, cid)
    if category is None:
        raise HTTPException(status_code=404, detail='分类不存在')

    payload = data.model_dump(exclude_unset=True)
    if 'parent_id' in payload:
        parent_id = payload['parent_id']
        if parent_id == cid:
            raise HTTPException(status_code=400, detail='父分类不能是自己')
        if parent_id is not None:
            parent = db.get(Category, parent_id)
            if parent is None:
                raise HTTPException(status_code=400, detail='父分类不存在')
            if parent.parent_id is not None:
                raise HTTPException(status_code=400, detail='只支持一级/二级分类')

    for field, value in payload.items():
        setattr(category, field, value)
    db.commit()
    db.refresh(category)
    return category


def delete_category(db: Session, cid: int) -> None:
    """删除分类：存在子分类或被账单引用时拒绝。"""
    category = db.get(Category, cid)
    if category is None:
        raise HTTPException(status_code=404, detail='分类不存在')

    child = db.scalar(select(Category.id).where(Category.parent_id == cid).limit(1))
    if child:
        raise HTTPException(status_code=400, detail='该分类下存在二级分类，不能删除')
    used = db.scalar(
        select(Transaction.id).where(Transaction.category_id == cid).limit(1)
    )
    if used:
        raise HTTPException(status_code=400, detail='该分类已被账单使用，不能删除')

    db.delete(category)
    db.commit()
