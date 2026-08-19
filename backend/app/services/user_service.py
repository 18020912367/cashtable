"""记账人业务逻辑：列表 / 新增 / 删除（无注册/管理界面，表手写）。"""
from fastapi import HTTPException
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from ..models import Transaction, User
from ..schemas import UserCreate, UserOut, fmt_dt


def list_users(db: Session) -> list[UserOut]:
    """记账人列表。"""
    users = db.scalars(select(User).order_by(User.id)).all()
    return [UserOut(id=u.id, name=u.name, created_at=fmt_dt(u.created_at)) for u in users]


def create_user(db: Session, data: UserCreate) -> UserOut:
    """新增记账人。"""
    user = User(name=data.name.strip())
    db.add(user)
    db.commit()
    db.refresh(user)
    return UserOut(id=user.id, name=user.name, created_at=fmt_dt(user.created_at))


def delete_user(db: Session, user_id: int) -> None:
    """删除记账人：其名下账单 created_by 置空（不删除账单）。

    当前记账人被删除后，App 端需重新选择记账人。
    """
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail='记账人不存在')
    db.execute(
        update(Transaction)
        .where(Transaction.created_by == user_id)
        .values(created_by=None)
    )
    db.delete(user)
    db.commit()
