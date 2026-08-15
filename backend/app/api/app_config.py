"""App 通用配置接口：版本信息 / APK 下载地址 / 默认服务器地址。

配置保存在 data/app_config.json（与数据库同目录），改配置无需重启后端。
"""
import hashlib
import json

from fastapi import APIRouter

from ..config import DATA_DIR

router = APIRouter(prefix='/api/app', tags=['app'])

CONFIG_FILE = DATA_DIR / 'app_config.json'

# 兜底默认配置：文件缺失/损坏时使用
DEFAULT_CONFIG = {
    'app_name': '家庭记账',
    'latest_version_code': 2,
    'latest_version_name': '0.1.2',
    'apk_url': 'http://124.223.74.139:8001/app-debug.apk',
    'changelog': '新增应用内检查更新；修复分类加载问题',
    'default_server_url': 'http://124.223.74.139:8000/',
}


def load_app_config() -> dict:
    """读取配置；文件存在则与默认合并（文件优先）。"""
    if CONFIG_FILE.exists():
        try:
            extra = json.loads(CONFIG_FILE.read_text('utf-8'))
            return {**DEFAULT_CONFIG, **extra}
        except (json.JSONDecodeError, OSError):
            pass
    return dict(DEFAULT_CONFIG)


@router.get('/config')
def get_app_config() -> dict:
    """App 通用配置：版本检查与默认服务器地址下发。

    附加 config_fingerprint（配置内容 MD5）：App 端据此判断配置是否变化，
    防止本地缓存旧配置导致"提示已是最新版本"的假象。
    """
    cfg = load_app_config()
    raw = json.dumps(cfg, ensure_ascii=False, sort_keys=True)
    cfg['config_fingerprint'] = hashlib.md5(raw.encode('utf-8')).hexdigest()
    return cfg
