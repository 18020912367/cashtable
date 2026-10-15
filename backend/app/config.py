"""全局配置：数据库路径与 LLM 相关配置。"""
import os
from pathlib import Path

# 项目根目录（backend/）
BASE_DIR = Path(__file__).resolve().parent.parent


def _load_dotenv() -> None:
    """轻量加载 .env（兼容 LLMAKPmode/LLMAPIKEY 旧变量名），不依赖 python-dotenv。"""
    for p in (BASE_DIR / '.env', BASE_DIR.parent / '.env'):
        if not p.exists():
            continue
        for line in p.read_text('utf-8').splitlines():
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, _, val = line.partition('=')
            os.environ.setdefault(key.strip(), val.strip())


_load_dotenv()

# 数据目录：SQLite 文件与备份目录
DATA_DIR = BASE_DIR / 'data'
DATA_DIR.mkdir(exist_ok=True)

# APK 发布目录（云端静态托管，App 更新下载统一走云端：http://<server>/apk/app-debug.apk）
APK_DIR = DATA_DIR / 'apks'
APK_DIR.mkdir(exist_ok=True)

# 运行日志目录（App 崩溃/功能异常上报：每个激活码一个 {code}.log，一行一条）
# 云端（Linux）固定项目根 /home/codefile/cashtable/logs；本地 Windows 用项目根 logs/
LOG_DIR = Path(os.getenv('LOG_DIR', str(BASE_DIR.parent / 'logs')))
LOG_DIR.mkdir(parents=True, exist_ok=True)

# 官网静态站点目录（App 介绍与下载页，服务在 http://<server>/）
WEB_DIR = BASE_DIR / 'web'
WEB_DIR.mkdir(exist_ok=True)

# 数据库连接（默认 backend/data/cashtable.db，可通过环境变量覆盖）
DATABASE_URL = os.getenv('DATABASE_URL', f"sqlite:///{DATA_DIR / 'cashtable.db'}")

# 激活码库（keycode.db，独立于主库）：存"全功能码"（激活码/激活时间/激活设备/是否已激活）
KEYCODE_DB_PATH = Path(os.getenv('KEYCODE_DB_PATH', str(DATA_DIR / 'keycode.db')))

# 激活库目录（全功能模式：每个激活码一个独立数据库，命名 {激活码}_cashtable.db）
# 云端（Linux）固定 /home/codefile/cashtabledb；本地 Windows 用 data/activated 便于开发
_activated_default = Path('/home/codefile/cashtabledb') if os.name != 'nt' else DATA_DIR / 'activated'
ACTIVATED_DB_DIR = Path(os.getenv('ACTIVATED_DB_DIR', str(_activated_default)))
ACTIVATED_DB_DIR.mkdir(parents=True, exist_ok=True)

# LLM 配置：优先新变量名，兼容旧变量名（LLMAKPmode / LLMAPIKEY）
# 默认智谱 base_url；不配置时自动降级为本地规则分类
LLM_BASE_URL = (os.getenv('LLM_BASE_URL', '').rstrip('/')
                or 'https://open.bigmodel.cn/api/paas/v4')
LLM_API_KEY = os.getenv('LLM_API_KEY', '') or os.getenv('LLMAPIKEY', '')
LLM_MODEL = os.getenv('LLM_MODEL', '') or os.getenv('LLMAKPmode', '')
# 备用模型列表：主模型被限流（429）时依次切换
LLM_FALLBACK_MODELS = [m.strip() for m in os.getenv('LLM_FALLBACK_MODELS', '').split(',') if m.strip()]
