"""记账人接口。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import UserCreate, UserOut
from ..services import user_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['users'])


@router.get('/users/', response_model=list[UserOut])
def list_users(db: Session = Depends(get_db)):
    """记账人列表。"""
    return user_service.list_users(db)


@router.post('/users/', response_model=UserOut, status_code=201)
def create_user(data: UserCreate, db: Session = Depends(get_db)):
    """新增记账人。"""
    return user_service.create_user(db, data)


@router.delete('/users/{user_id}/', status_code=204)
def delete_user(user_id: int, db: Session = Depends(get_db)):
    """删除记账人（名下账单 created_by 置空）。"""
    user_service.delete_user(db, user_id)
    return None
