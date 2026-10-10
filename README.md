# 截记（Cashtable）

一款支持**截图 AI 记账**的记账小工具：支付截图交给大模型自动识别入账。支持本地单机使用，也支持自建服务器多人记账与公共服务器激活使用。

> **用户操作说明（完整版）**：[docs/用户操作说明.md](docs/用户操作说明.md)（官网 `/docs/用户操作说明.md` 可在线阅读）
> **设计文档**：[docs/design.md](docs/design.md)

---

## 📱 下载安装（最新版 APK）

| 下载渠道 | 地址 |
| --- | --- |
| **GitHub Releases（推荐）** | [app-debug-v0.8.6.apk](https://github.com/18020912367/cashtable/releases/download/v0.8.6/app-debug-v0.8.6.apk) |
| 全部历史版本 | [Releases 页面](https://github.com/18020912367/cashtable/releases) |

**当前最新版本：v0.8.6**（versionCode 25，MD5 `b36b6e49f044aa0dc3b644e011d24b94`）

安装说明：
1. 手机浏览器打开下载链接，下载 APK
2. 安装时如提示"未知来源应用"，点击**允许**即可
3. 覆盖安装不会清除已有数据
4. 已安装用户也可在 App 内「我的 → 检查更新」自动更新

---

## 💡 项目思路

### 核心设计：「截图即记账」

记账 App 最大的门槛是**录入麻烦**。本项目的核心思路是把录入动作压到最短：

```
支付/账单截图 → 多模态大模型识别（金额/时间/分类/内容）→ 用户确认 → 一键入账
```

三种录入方式，覆盖不同使用习惯：

1. **截图记账**：「明细」页点击相机图标 → 选择截图 → AI 识别 → 确认入账
2. **自动记账（悬浮球入口）**：系统截屏后点悬浮球 → App 自动读取相册最新截图 → AI 识别 → 确认入账 → **保存后自动删除截图并返回原界面**（全程无需切换 App）
3. **手动记账**：传统表单录入，作为兜底

### AI 分类自学习

不做冷启动配置：大模型识别的分类建议先落入**临时规则表**，用户确认后**晋升为固定规则**，后续同样商户直接本地规则命中、不再调用大模型——越用越准、越用越省。

### 三种运行模式，同一套 App

| 模式 | 说明 | 数据存储 | 截图识别 |
| --- | --- | --- | --- |
| **本地模式**（默认） | App 自带全部功能与数据库，单机开箱即用 | 手机内置 sqlite | 直连大模型（个人中心维护 LLM Key） |
| **自定义模式** | 填写自有服务器地址/端口/数据库目录，多人记账 | 云端 sqlite（下载标准模板自行导入服务器） | 走自有服务器 API |
| **全功能模式** | 绑定激活码激活公共服务器，使用全部功能 | 云端 sqlite（首次激活自动上传） | 走公共服务器 API |

### 简化原则

- **不做注册登录**：用「记账人」机制代替账号体系（≤4 人家庭场景，App 内选择当前记账人即可）
- **不做 HTTPS**：数据不敏感的内部工具，降低部署门槛
- **SQLite 单文件**：免数据库部署，整库即拷即备份，激活时直接上传整库完成迁移

### 总体架构

```
┌──────────────────────── Android App (Kotlin) ────────────────────────┐
│ 记账人选择(本地) · 记一笔 · 账单列表+筛选 · 截图OCR · 悬浮球自动记账 │
└───────────────┬──────────────────────────────────────────────────────┘
                │ HTTP（公网 http://124.223.74.139:8000）
┌───────────────▼─────────── 云端服务器（124.223.74.139）──────────────┐
│  FastAPI(uvicorn, systemd)：8000 直出 API + /apk APK 静态托管         │
│  租户库 /home/codefile/cashtabledb/{激活码}_cashtable.db（WAL 模式）   │
└───────────────┬──────────────────────────────────────────────────────┘
                │ frp 隧道 8010 → 本地 8000（开发联调备用通道）
┌───────────────▼──────────────────────────────────────────────────────┐
│                  本地电脑（Windows，开发环境）                          │
│  FastAPI(uvicorn) ── SQLAlchemy ── SQLite 单文件 (cashtable.db)       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## ✨ 功能特性

- **截图记账**：支付截图 → 大模型视觉识别（金额/时间/分类/内容）→ 一键入账
- **自动记账（悬浮球入口）**：系统截屏 → 自动读取相册最新截图 → AI 识别 → 确认入账，保存后自动删除截图并返回原界面
- **AI 自动分类与规则晋升**：识别分类自动沉淀为分类规则，后续本地规则优先
- **记账人机制**（无登录）：支持多记账人，支持新增与删除；切换模式自动刷新为对应库第一个记账人
- **明细管理**：按分类 / 记账人 / 关键词筛选，支持编辑、删除
- **统计与导出**：按月/分类统计；选择日期区间一键导出账单 CSV（含记账人与分类名）
- **激活码体系**：激活码支持重复使用（多设备共用同一账本）
- **App 内更新**：检测更新 → 下载安装，内置 FileProvider

---

## 🛠 技术栈与代码路径

| 层 | 技术选型 | 代码路径 | 说明 |
| --- | --- | --- | --- |
| Android 客户端 | Kotlin + Jetpack Compose | `android/app/src/main/java/com/cashtable/app/` | 单 Activity 架构 |
| 网络层 | Retrofit + OkHttp | `android/.../data/ApiClient.kt` | 与 FastAPI 对接 |
| 本地存储 | 原生 SQLiteDatabase | `android/.../data/LocalDbHelper.kt` | 本地模式账单库 |
| 敏感信息 | EncryptedSharedPreferences（Android Keystore） | `android/.../data/Prefs.kt` | LLM Key 加密存储 |
| 悬浮球 | WindowManager 常驻悬浮球 | `android/.../p4/FloatingBallService.kt` | 自动记账入口 |
| 后端 | FastAPI（async）+ uvicorn | `backend/app/main.py` | 端口 8000~8009，路由即文档（/docs） |
| 数据库 | SQLite + SQLAlchemy 2.x | `backend/app/database.py` / `models.py` | 单文件 `cashtable.db`，不引入迁移框架 |
| 截图识别 | 智谱多模态大模型（主 `glm-4v-flash` / 备用 `glm-4.6v-flash`） | `backend/app/services/vision_service.py` | 429 退避重试，自动切换备用模型 |
| AI 分类 | OpenAI 兼容 LLM API | `backend/app/services/classify_service.py` | 本地规则未命中时调用 |
| 规则晋升 | —— | `backend/app/services/rule_service.py` | 临时规则（pending）→ 用户确认 → 固定规则 |
| 激活码 | SQLite（`keycode.db`） | `backend/app/keycode.py` / `backend/scripts/gen_keycode.py` | 激活码/激活时间/设备 MAC/是否已激活 |
| 官网 | 静态 HTML | `backend/web/index.html` | App 介绍 + 下载入口 + 在线操作说明 |
| 部署 | systemd + frp（开发备用） | `backend/scripts/start_services.ps1` / `frpc.toml` | 云端直出公网 8000 |

### 目录结构

```
cashtable/
├── android/                              # Android 客户端
│   └── app/src/main/java/com/cashtable/app/
│       ├── MainActivity.kt               # 单 Activity 入口
│       ├── data/                         # ApiClient / Repository / LocalDbHelper / Prefs（网络与存储层）
│       ├── p4/FloatingBallService.kt     # 悬浮球自动记账服务
│       └── ui/                           # 记账/明细/统计/我的/模式切换等界面（Jetpack Compose）
├── backend/                              # FastAPI 后端
│   ├── app/
│   │   ├── main.py                       # 应用入口（uvicorn）
│   │   ├── api/                          # transactions / categories / users / statistics /
│   │   │                                 #   ai / activate / app_config / imports / rules
│   │   ├── services/                     # vision_service（截图识别）/ classify_service（AI 分类）/
│   │   │                                 #   rule_service（规则晋升）/ transaction_service 等
│   │   ├── database.py · models.py · schemas.py
│   │   └── keycode.py                    # 激活码库读写
│   ├── data/                             # cashtable.db / keycode.db / app_config.json（版本配置）/ APK 托管目录
│   ├── web/                              # 官网页面 + 在线用户操作说明
│   └── scripts/                          # gen_keycode.py（生成激活码）/ start_services.ps1（服务自启）等
├── docs/                                 # design.md（设计文档）/ devrule.txt（版本规则）/ 用户操作说明.md
└── README.md
```

---

## 📖 操作说明（速查）

完整图文说明见 **[docs/用户操作说明.md](docs/用户操作说明.md)**，以下为常用操作速查：

### 快速开始（本地模式）

1. 安装 App 打开后默认进入本地模式
2. 到「我的 → LLM 设置」填写大模型 API Key 并测试连接（获取方式见操作说明文档）
3. 「明细」页右下角 **+** 手动记账，或点**相机图标**截图记账

### 三种记账方式

| 方式 | 入口 | 流程 |
| --- | --- | --- |
| 手动记账 | 「明细」页 **+** | 选类型 → 填金额/分类/内容 → 保存 |
| 截图记账 | 「明细」页相机图标 | 选截图 → AI 识别 → 核对 → 入账 |
| 自动记账 | 「我的 → 悬浮球记账」开启后，任意界面点悬浮球 | 系统截屏 → 点悬浮球 → 自动识别入账 → 自动删图返回 |

### 模式切换与激活

- 「我的 → 切换模式」：本地 / 自定义 / 全功能三种模式切换
- **全功能模式**：输入 12 位十六进制激活码 → 「校验并激活」→ 首次激活自动上传本地数据，之后走公共服务器
- **自定义模式**：先下载标准 sqlite 模板导入自有服务器，再填服务器地址/端口/数据库目录
- **激活码可重复使用**：多台设备共用同一账本，重复激活不会覆盖已有数据

### 统计与导出

- 「统计」页：按月 / 按分类查看收支
- 右上角**导出图标** → 选择日期区间 → 一键导出 CSV（含分类名与记账人名），可分享到微信/QQ/邮件

### 记账人管理

- 「记一笔」页切换记账人，支持**新增**与**删除**
- 删除当前记账人后自动引导重新选择；切换模式自动刷新为对应库第一个记账人

### 检查更新

- 「我的 → 检查更新」固定连接云端检查新版本（与运行模式无关），有新版可直接下载安装

> ⚠️ 注意：自定义/全功能模式为多人共享同一数据库，**不支持多人同时操作**（存在写入冲突风险），请错峰记账。

---

## 💻 本地构建与运行

### 后端

```bash
cd backend
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt   # Windows
.venv/Scripts/python -m app.main               # 默认端口 8000（PORT 可覆盖，限 8000~8009）
```

环境变量（`.env`，可选）：`LLM_API_KEY` / `LLM_MODEL` / `LLM_FALLBACK_MODELS`（也可在 App 个人中心维护）。

### Android

```bash
cd android
# 需 JDK 25（D:\Androidstudio\jbr 等），AGP 9.1.1 / Kotlin 2.3.10
.\gradlew.bat :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 发版流程

构建 → 复制 APK 到 `backend/data/apks/` → 更新 `backend/data/app_config.json`（版本/MD5/大小/changelog）→ 同步官网 `backend/web/`（如有改动）→ 上传 backend/app + web + APK + 配置到云端 → 重启 `cashtable-api.service` → 公网验证（`/` 官网、`/api/app/config` 新版本、`/apk/app-debug.apk` 可下载）。

同步发布 **GitHub Release**（APK 附件 + changelog）。

---

## ☁️ 云端部署（公共服务器，全功能模式）

- 云端目录：`/home/codefile/cashtable/`（代码 + venv），systemd 服务 `cashtable-api.service`（`PORT=8000`、`KEYCODE_DB_PATH=/home/codefile/cashtabledb/keycode.db`），开机自启
- 激活码库：`/home/codefile/cashtabledb/keycode.db`（激活码/激活时间/激活设备 MAC/是否已激活）
- 租户库：`/home/codefile/cashtabledb/{激活码}_cashtable.db`（激活时上传，WAL 模式）
- 生成激活码：云端执行 `venv/bin/python backend/scripts/gen_keycode.py`
- 公网 `8000` 由云端后端 systemd 直出：API + APK 静态托管 `/apk/app-debug.apk`
- App 更新检查固定连接云端（`/api/app/config`），与运行模式无关；APK 不再依赖本机分发
- 本地 `frpc.toml` 仅保留 `8010 → 本地 8000` 备用通道（开发联调用）
- 本地服务自启：`backend/scripts/start_services.ps1 -Install`（frpc + 可选本地后端）

---

## 🏷 版本规则

- 小修复：`Z+1`（0.001）
- 5 个及以上问题点修复 / 新增操作功能：`Y+1` 且 Z 归零（0.01）
- 核心功能新增：`X+1` 且 Y/Z 归零（0.1）
- versionCode 单调递增 +1

详见 [docs/devrule.txt](docs/devrule.txt)。

### 已发布版本

| 版本 | versionCode | 说明 |
| --- | --- | --- |
| [0.8.6](https://github.com/18020912367/cashtable/releases/tag/v0.8.6) | 25 | 优化部分bug，优化云备份数据逻辑，优化apk下载路径 |
| 0.8.5 | 24 | 修复全功能模式数据隔离问题：账单按激活码隔离存储到专属数据库 |
| [0.8.4](https://github.com/18020912367/cashtable/releases/tag/v0.8.4) | 23 | 切换模式自动刷新记账人；激活码支持重复使用（多设备共用账本）；统计页新增账单导出（日期区间+记账人名+分类名） |
| 0.8.3 | 22 | 更新检查固定连接云端；APK 云端托管 |
| 0.8.2 | 21 | 全新邮政绿主题配色 |
| 0.8.1 | 20 | 更新应用 Logo |
| 0.8.0 | 19 | 三模式（本地/自定义/全功能）、LLM Key 个人中心维护（加密存储）、keycode.db 激活码库、记账人新增/删除、改名「截记」 |
| 0.6.1 | 14 | 悬浮球记账开启流程优化（授权返回自动继续） |
| 0.6.0 | 13 | 悬浮球截图记账（MediaProjection + 大模型识别） |
| 0.5.x | 9~12 | 截图记账系列优化（入口/分类/404 修复/App 内更新） |

---

## 📄 开源说明

- 本地模式无需任何服务器；「自定义/全功能」模式需公网后端（推荐直接部署在 Linux 云服务器上）
- 全功能模式需在服务端预先生成激活码（`backend/scripts/gen_keycode.py`）并确保目录 `/home/codefile/cashtabledb` 存在（属主 www）
- 激活码库固化在云端 `/home/codefile/cashtabledb/keycode.db`，不随本地机器迁移
- 自定义/全功能模式为多人共享数据库，存在写入冲突风险，建议错峰使用

## 联系我们

如有问题或获取激活码，请联系管理员：QQ **928365341** · 邮箱 **928365341@qq.com** · 微信 **ferocioustigerpink**
