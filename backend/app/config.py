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

# 数据库连接（默认 backend/data/cashtable.db，可通过环境变量覆盖）
DATABASE_URL = os.getenv('DATABASE_URL', f"sqlite:///{DATA_DIR / 'cashtable.db'}")

# LLM 配置：优先新变量名，兼容旧变量名（LLMAKPmode / LLMAPIKEY）
# 默认智谱 base_url；不配置时自动降级为本地规则分类
LLM_BASE_URL = (os.getenv('LLM_BASE_URL', '').rstrip('/')
                or 'https://open.bigmodel.cn/api/paas/v4')
LLM_API_KEY = os.getenv('LLM_API_KEY', '') or os.getenv('LLMAPIKEY', '')
LLM_MODEL = os.getenv('LLM_MODEL', '') or os.getenv('LLMAKPmode', '')
# 备用模型列表：主模型被限流（429）时依次切换
LLM_FALLBACK_MODELS = [m.strip() for m in os.getenv('LLM_FALLBACK_MODELS', '').split(',') if m.strip()]
