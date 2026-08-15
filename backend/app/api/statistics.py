"""统计接口。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..schemas import MonthlyStatOut
from ..services import statistics_service
from .deps import get_db

router = APIRouter(prefix='/api', tags=['statistics'])


@router.get('/statistics/monthly/', response_model=list[MonthlyStatOut])
def monthly_summary(year: str, db: Session = Depends(get_db)):
    """按月收支合计（最简 GROUP BY）。"""
    return statistics_service.monthly_summary(db, year)
