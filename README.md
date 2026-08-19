# 截记（Cashtable）

一款支持截图 AI 记账的记账小工具：支持本地单机使用，也支持自建服务器多人记账与公共服务器激活使用。

> **官网（App 介绍与下载）**：http://124.223.74.139:8000/
> **用户操作说明**：[docs/用户操作说明.md](docs/用户操作说明.md)（官网 `/docs/用户操作说明.md` 可在线阅读）

## 功能特性

- **截图记账**：支付截图 → 大模型视觉识别（金额/时间/分类/内容）→ 一键入账
- **悬浮球记账**（P4）：任意界面点悬浮球 → MediaProjection 截屏 → AI 识别 → 直达确认页入账
- **自动记账**（P3）：授权后自动读取最新相册截图并识别入账
- **AI 自动分类与规则晋升**：识别分类可自动沉淀为分类规则，后续本地规则优先
- **记账人机制**（无登录）：支持多记账人，支持新增与删除
- **统计**：按月/分类统计
- **App 内更新**：检测更新 → 下载安装，内置 FileProvider

## 运行模式（v0.8，三模式）

App 默认**本地模式**，可在「我的 → 切换模式」中切换。

| 模式 | 说明 | 数据存储 | 截图识别 |
| --- | --- | --- | --- |
| 本地模式（默认） | App 自带全部功能与数据库，单机使用 | 内置 sqlite | 直连大模型（个人中心维护 LLM Key） |
| 自定义模式 | 填写自有服务器地址/端口/数据库目录，多人记账 | 云端 sqlite（下载标准模板自行导入服务器） | 走自有服务器 API |
| 全功能模式 | 绑定激活码激活公共服务器，使用全部功能 | 云端 sqlite（首次激活自动上传） | 走公共服务器 API |

> 注意：自定义/全功能模式为多人共享同一数据库，**不支持多人同时操作**（存在写入冲突风险），请错峰记账。

### 1. LLM Key 维护（个人中心）

- 「我的 → LLM 设置」以输入框形式维护 API Key（仅写操作），选择全功能模式后该字段不可编辑
- Key 使用 **Android EncryptedSharedPreferences**（Android Keystore）加密存储
- 界面只显示头尾（如 `sk-ab...xyz`），**不可复制**
- 提供「测试连接」按钮，点击验证 LLM 是否连通

### 2. 激活码库（keycode.db）

后端新增独立数据库 `keycode.db`，内置「全功能码」表，字段：激活码、激活时间、激活设备 MAC、是否已激活。

### 3. 自定义模式

填写自己的服务器地址、端口号、数据库目录，连接云端数据库管理；需先下载标准 sqlite 模板导入自有服务器（「我的 → 切换模式 → 自定义 → 下载标准 sqlite 模板」）。

### 4. 全功能模式（激活流程）

1. 个人中心输入激活码并「校验并激活」
2. 后端校验激活码；首次激活通过 API（FastAPI + async，支持并发）将 App 本地 sqlite 上传至云端指定目录 `/home/codefile/cashtabledb`
3. 重命名为 `{激活码}_cashtable.db`，并开启 SQLite WAL 模式
4. 之后该 App 所有数据与 AI 记账操作统一走云服务器 API

### 5. 记账人管理

「记一笔」页切换记账人支持**新增**与**删除**（删除当前记账人后自动引导重新选择）。

### 6. 命名与 Logo

App 更名为「**截记**」，Logo 见 [docs/APP logo.png](docs/APP%20logo.png)。

## 技术栈

- **Android**：Kotlin + Jetpack Compose、Room 无关（原生 SQLiteDatabase）、Retrofit + OkHttp、EncryptedSharedPreferences
- **后端**：FastAPI（async）+ SQLAlchemy + SQLite、httpx（LLM 调用）
- **AI**：智谱视觉大模型（主 `glm-4v-flash` / 备用 `glm-4.6v-flash`，429 退避重试自动切换）

## 目录结构

```
backend/    # FastAPI 后端（含 keycode.db 激活码库、激活接口、AI 识别接口）
android/    # Android 客户端（三模式路由、悬浮球、截图识别）
docs/       # 设计文档（design.md）、版本规则（devrule.txt）、Logo
```

## 本地构建与运行

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

## 发版

构建 → 复制 APK 到 `backend/data/apks/` → 更新 `backend/data/app_config.json`（版本/MD5/大小/changelog）→ 同步官网 `backend/web/`（如有改动）→ 上传 backend/app + web + APK + 配置到云端 → 重启 `cashtable-api.service` → 公网验证（`/` 官网、`/api/app/config` 新版本、`/apk/app-debug.apk` 可下载）。

## 云端部署（公共服务器，全功能模式）

- 云端目录：`/home/codefile/cashtable/`（代码 + venv），systemd 服务 `cashtable-api.service`（`PORT=8000`、`KEYCODE_DB_PATH=/home/codefile/cashtabledb/keycode.db`），开机自启
- 激活码库：`/home/codefile/cashtabledb/keycode.db`（激活码/激活时间/激活设备 MAC/是否已激活）
- 租户库：`/home/codefile/cashtabledb/{激活码}_cashtable.db`（激活时上传，WAL 模式）
- 生成激活码：云端执行 `venv/bin/python backend/scripts/gen_keycode.py`
- 公网 `8000` 由云端后端 systemd 直出：API + APK 静态托管 `/apk/app-debug.apk`
- App 更新检查固定连接云端（`/api/app/config`），与运行模式无关；APK 不再依赖本机分发
- 本地 `frpc.toml` 仅保留 `8010 → 本地 8000` 备用通道（开发联调用）
- 本地服务自启：`backend/scripts/start_services.ps1 -Install`（frpc + 可选本地后端）

## 版本规则

- 小修复：`Z+1`（0.001）
- 5 个及以上问题点修复 / 新增操作功能：`Y+1` 且 Z 归零（0.01）
- 核心功能新增：`X+1` 且 Y/Z 归零（0.1）
- versionCode 单调递增 +1

详见 [docs/devrule.txt](docs/devrule.txt)。

## 已发布版本

| 版本 | versionCode | 说明 |
| --- | --- | --- |
| 0.8.1 | 20 | 更新应用 Logo |
| 0.8.0 | 19 | 三模式（本地/自定义/全功能）、LLM Key 个人中心维护（加密存储）、keycode.db 激活码库、记账人新增/删除、改名「截记」 |
| 0.6.1 | 14 | 悬浮球记账开启流程优化（授权返回自动继续） |
| 0.6.0 | 13 | 悬浮球截图记账（MediaProjection + 大模型识别） |
| 0.5.x | 9~12 | 截图记账系列优化（入口/分类/404 修复/App 内更新） |

## 开源说明

- 本地模式无需任何服务器；「自定义/全功能」模式需公网后端（推荐直接部署在 Linux 云服务器上）
- 全功能模式需在服务端预先生成激活码（`backend/scripts/gen_keycode.py`）并确保目录 `/home/codefile/cashtabledb` 存在（属主 www）
- 激活码库固化在云端 `/home/codefile/cashtabledb/keycode.db`，不随本地机器迁移
- 自定义/全功能模式为多人共享数据库，存在写入冲突风险，建议错峰使用
