"""分类接口。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import CategoryCreate, CategoryOut, CategoryUpdate
from ..services import category_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['categories'])


@router.get('/categories/', response_model=list[CategoryOut])
def list_categories(
    kind: str | None = None,
    parent_id: int | None = None,
    db: Session = Depends(get_db),
):
    """分类列表：?kind=expense&parent_id= 过滤。"""
    return category_service.list_categories(db, kind=kind, parent_id=parent_id)


@router.post('/categories/', response_model=CategoryOut, status_code=201)
def create_category(data: CategoryCreate, db: Session = Depends(get_db)):
    """新增分类（parent_id 挂二级）。"""
    return category_service.create_category(db, data)


@router.patch('/categories/{cid}/', response_model=CategoryOut)
def update_category(cid: int, data: CategoryUpdate, db: Session = Depends(get_db)):
    """修改分类。"""
    return category_service.update_category(db, cid, data)


@router.delete('/categories/{cid}/', status_code=204)
def delete_category(cid: int, db: Session = Depends(get_db)):
    """删除分类：一级分类级联删其二级分类，关联账单的 category_id 置空。"""
    category_service.delete_category(db, cid)
