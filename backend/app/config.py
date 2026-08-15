"""全局配置：数据库路径与 LLM 相关配置（P4 预留）。"""
import os
from pathlib import Path

# 项目根目录（backend/）
BASE_DIR = Path(__file__).resolve().parent.parent

# 数据目录：SQLite 文件与备份目录
DATA_DIR = BASE_DIR / 'data'
DATA_DIR.mkdir(exist_ok=True)

# 数据库连接（默认 backend/data/cashtable.db，可通过环境变量覆盖）
DATABASE_URL = os.getenv('DATABASE_URL', f"sqlite:///{DATA_DIR / 'cashtable.db'}")

# LLM 分类配置（OpenAI 兼容接口，P4 使用；不配置时自动降级为本地规则分类）
LLM_BASE_URL = os.getenv('LLM_BASE_URL', '')
LLM_API_KEY = os.getenv('LLM_API_KEY', '')
LLM_MODEL = os.getenv('LLM_MODEL', '')
