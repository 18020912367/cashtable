"""FastAPI 应用入口：健康检查 + 路由注册 + 启动时初始化数据库。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .api import (
    activate,
    ai,
    app_config,
    categories,
    imports,
    logs_api,
    rules,
    statistics,
    transactions,
    users,
)
from .config import APK_DIR, WEB_DIR
from .database import init_db


@asynccontextmanager
async def lifespan(_: FastAPI):
    """启动时建表并写入初始分类数据。"""
    init_db()
    activate.init_activation()
    yield


app = FastAPI(title='截记（家庭记账小工具）', version='0.8.0', lifespan=lifespan)


@app.get('/health')
def health() -> dict:
    """健康检查。"""
    return {'status': 'ok'}


# 注册 API 路由
app.include_router(users.router)
app.include_router(categories.router)
app.include_router(transactions.router)
app.include_router(imports.router)
app.include_router(rules.router)
app.include_router(ai.router)
app.include_router(statistics.router)
app.include_router(app_config.router)
app.include_router(activate.router)
app.include_router(logs_api.router)


# APK 静态托管（App 更新下载统一走云端，不再依赖本机开发环境）
app.mount('/apk', StaticFiles(directory=APK_DIR), name='apk')

# 官网静态站点（App 介绍与下载页，挂在根路径；API 路由已先行注册，不受影响）
app.mount('/', StaticFiles(directory=WEB_DIR, html=True), name='web')


if __name__ == '__main__':
    """直接运行：python -m app.main

    绑定 0.0.0.0 同时支持本地局域网与云服务器访问；
    端口通过环境变量 PORT 配置，仅允许 8000~8009（与云安全组放行范围一致）。
    """
    import os

    import uvicorn

    port = int(os.getenv('PORT', '8000'))
    if not 8000 <= port <= 8009:
        raise SystemExit(f'端口 PORT 必须位于 8000~8009，当前为 {port}，请检查云安全组是否放行')
    uvicorn.run('app.main:app', host='0.0.0.0', port=port)
