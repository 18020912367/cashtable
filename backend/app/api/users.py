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
    """新增记账人（一般只在 sqlite 手动写，接口备用）。"""
    return user_service.create_user(db, data)
