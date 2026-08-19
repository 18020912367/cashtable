# 截记（家庭记账小工具）· 设计文档

> 版本：v0.8
> 定位：家庭/个人记账小工具（已开源）。FastAPI + SQLite 轻量后端 + Android App。
> v0.8 起支持**三种运行模式**（本地 / 自定义服务器 / 全功能公共服务器），可打包对外分发。
> 简化原则：**不做注册登录、不做 HTTPS**，默认本地模式开箱即用，云端多人记账需激活码。

---

## 1. 概述

### 1.1 定位与边界

- 仅个人与家庭成员（≤4 人）使用，数据不敏感、无并发压力。
- 核心能力：手动记账、账单查询与筛选、截图 OCR 记账、悬浮球自动记账（系统截屏 + 自动读取相册 + AI 识别与分类）、App 内更新。
- 账号信息**手动写入 users 表**（无注册/登录/管理员界面），App 端选择"当前记账人"即可记账。

---

## 2. 总体架构

```
┌──────────────────────── Android App (Kotlin) ────────────────────────┐
│ 记账人选择(本地) · 记一笔 · 账单列表+筛选 · 截图OCR · 悬浮球自动记账 │
└───────────────┬──────────────────────────────────────────────────────┘
                │ HTTP（公网 http://124.223.74.139:8000，无加密）
┌───────────────▼─────────── 云端服务器（124.223.74.139）──────────────┐
│  FastAPI(uvicorn, systemd)：8000 直出 API + /apk APK 静态托管         │
│  frps 0.70.1：8010 → 本地后端（开发备用通道；安全组放行 7000/8000~8010）│
└───────────────┬──────────────────────────────────────────────────────┘
                │ frp 隧道（本地 frpc 回连云端 7000，开发备用）
┌───────────────▼──────────────────────────────────────────────────────┐
│                       本地电脑（Windows，开发环境）                    │
│  FastAPI(uvicorn) ── SQLAlchemy ── SQLite 单文件 (cashtable.db)      │
│  （APK 由云端 /apk 静态托管，本地不再分发）                            │
└──────────────────────────────────────────────────────────────────────┘
```

**无认证：任何人拿到 IP:端口 即可访问。4 人内部自用可接受；如后续暴露公网有顾虑，可选加防火墙 IP 白名单（不强制）。**

---

## 3. 技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| 后端 | FastAPI + uvicorn | 轻量、天然异步，路由即文档（/docs） |
| 数据库 | SQLite（单文件 `cashtable.db`） | 4 人用量毫无压力，免部署 |
| ORM | SQLAlchemy 2.x | 表结构即代码，DDL 统一管理；不引入迁移框架（表手动建即可） |
| Android | Kotlin + Jetpack Compose | 单 Activity 架构 |
| 图像识别 | 多模态大模型（后端调用，主 `glm-4v-flash`、备用 `glm-4.6v-flash`） | 截图上传后端识别（/api/ai/extract/）；429 退避重试 + 自动切备用模型 |
| AI 分类 | OpenAI 兼容 LLM API（DeepSeek/通义等） | 本地规则未命中时调用，结果自动学习为规则；api_key 配在后端 |
| 悬浮球自动记账 | WindowManager 悬浮球（仅入口） | 悬浮球常驻，点击跳转自动记账页；用户手动系统截屏 → 相册读取（MediaStore）→ 后端大模型识别 |
| App 内更新 | OkHttp 下载 + FileProvider 安装 | `/api/app/config` 版本检查（含 config_fingerprint），下载到 cacheDir 后系统安装页确认 |
| 网络 | Retrofit + OkHttp | 与 FastAPI 对接 |
| 网络穿透/分发 | frp（云端 frps ↔ 本地 frpc，仅开发备用通道） | 公网 8000 由云端后端直出（API + /apk APK 静态托管）；frp 仅保留 8010 → 本地后端备用通道 |
| 部署 | 云端 uvicorn（systemd）+ 本地 frpc 备用通道 | 后端/DB/APK 均在云端运行，本机仅开发联调 |
| 备份 | cron 每日 `cp` SQLite 文件 | 保留 30 天，可手动下载 |

---

## 4. 数据模型（SQLite 表）

### 4.1 表结构（DDL）

```sql
-- 记账人表：手工写入，不提供注册/管理界面
CREATE TABLE users (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL,                -- 姓名/昵称
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- 分类表：支出/收入各一套，支持一级/二级
CREATE TABLE categories (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL,
    parent_id INTEGER REFERENCES categories(id),  -- 二级分类
    kind      TEXT NOT NULL CHECK (kind IN ('expense', 'income')),
    icon      TEXT NOT NULL DEFAULT '',
    sort      INTEGER NOT NULL DEFAULT 0
);

-- 账单表：核心表（字段对应：金额/分类/记账日期/交易发生日期/记账人/交易内容）
CREATE TABLE transactions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    amount       REAL NOT NULL,            -- 金额：支出为负、收入为正（支持正负号）
    category_id  INTEGER REFERENCES categories(id),   -- 分类（由 category.kind 决定支出/收入）
    happened_at  TEXT NOT NULL,            -- 交易发生日期 'YYYY-MM-DD HH:MM'
    created_at   TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),  -- 记账日期（自动记录）
    created_by   INTEGER REFERENCES users(id),        -- 记账人（无登录校验，App 选择）
    content      TEXT NOT NULL DEFAULT '',           -- 交易内容（如"美团外卖"）
    source       TEXT NOT NULL DEFAULT 'manual',     -- manual/ocr/import/auto
    external_id  TEXT NOT NULL DEFAULT '',           -- 微信/支付宝交易单号（去重）
    updated_at   TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE UNIQUE INDEX idx_txn_dedup ON transactions (external_id) WHERE external_id <> '';

-- 固定分类规则表：交易内容/商户关键词 → 分类（含用户确认晋升的 LLM 结果）
CREATE TABLE category_rules (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword    TEXT NOT NULL,             -- 关键词（LIKE 匹配交易内容）
    category_id INTEGER NOT NULL REFERENCES categories(id),
    source     TEXT NOT NULL DEFAULT 'manual',   -- manual / llm
    usage_count INTEGER NOT NULL DEFAULT 0,      -- 命中次数（可辅助排序）
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE UNIQUE INDEX idx_rule_keyword ON category_rules (keyword);

-- 临时分类规则表：LLM 建议结果先落这里，用户认可后晋升到 category_rules
CREATE TABLE category_rules_temp (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword    TEXT NOT NULL,             -- 交易内容/商户关键词
    category_id INTEGER NOT NULL REFERENCES categories(id),  -- LLM 建议分类
    status     TEXT NOT NULL DEFAULT 'pending',   -- pending / confirmed / discarded
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);
CREATE UNIQUE INDEX idx_rule_temp_keyword ON category_rules_temp (keyword);

-- 导入记录表：每次批量写入留痕
CREATE TABLE import_records (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    source     TEXT NOT NULL,              -- ocr/wechat/alipay
    status     TEXT NOT NULL DEFAULT 'done',
    total      INTEGER NOT NULL DEFAULT 0,
    imported   INTEGER NOT NULL DEFAULT 0,
    skipped    INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);
```

### 4.2 说明

- 金额用 `REAL` 足够（内部小工具），记账 App 精度要求不高；如需精确可后续换 `NUMERIC`。
- `external_id` 部分唯一索引即去重约束：同一交易单号重复写入会被服务端拦截（`INSERT ... ON CONFLICT` 或先查后插）。
- `happened_at` 存本地时间字符串，简单直观，不做时区转换。
- **收支方向由金额正负表达**（负=支出、正=收入），并与所选分类的 `kind` 保持一致；App 录入时由"支出/收入"切换自动决定金额符号，用户无需手动输入负号。
- `created_at` 即"记账日期"（自动记录，无需用户输入）；`happened_at` 即"交易发生日期"（默认当前，可改）。
- 分类规则两表配合实现"LLM 结果自学习"：`category_rules_temp`（pending）→ 用户确认保存 → 晋升为 `category_rules`（固定），避免同一商户反复调用 LLM。

### 4.3 分类初始数据（建表后执行）

```sql
-- 支出一级分类
INSERT INTO categories (name, kind, sort) VALUES
('三餐', 'expense', 1), ('零食', 'expense', 2), ('衣服', 'expense', 3),
('交通', 'expense', 4), ('旅游', 'expense', 5), ('孩子', 'expense', 6),
('宠物', 'expense', 7), ('水电网燃气', 'expense', 8), ('日用品', 'expense', 9),
('其他', 'expense', 10);

-- 收入一级分类
INSERT INTO categories (name, kind, sort) VALUES
('退款', 'income', 1), ('工资', 'income', 2), ('其他', 'income', 3);

-- 二级分类：在一级分类下通过 parent_id 挂接，由用户在 App 内自行添加
-- 示例：INSERT INTO categories (name, parent_id, kind, sort) VALUES ('早餐', 1, 'expense', 1);
```

> 分类表的 `icon` 字段存 Material Symbols 图标名称（见 6.2 图标映射表），App 端按名称渲染。

---

## 5. 后端 API（直连表 + 筛选）

原则：**每张表一个资源，GET 支持筛选参数，无认证**。POST 返回创建记录（含 id），供 App 批量同步。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/DELETE | `/api/users/` | 记账人列表 / 新增 / 删除（v0.8 新增 DELETE，名下账单 created_by 置空） |
| GET/POST | `/api/categories/` | 分类列表（`?kind=expense&parent_id=`）/ 新增 |
| PATCH/DELETE | `/api/categories/{id}` | 改 / 删 |
| GET | `/api/transactions/` | 账单查询，筛选参数见下 |
| POST | `/api/transactions/` | 记一笔 |
| PATCH/DELETE | `/api/transactions/{id}` | 改 / 删 |
| POST | `/api/transactions/batch/` | 批量写入（OCR/导入确认后一次提交，逐条去重） |
| POST | `/api/imports/` | 微信/支付宝 CSV 解析入库（可选，v1 可只做 OCR batch） |
| GET/POST/DELETE | `/api/category-rules/` | 固定分类规则列表 / 新增 / 删除（设置页管理） |
| GET | `/api/category-rules/pending/` | 待确认的临时规则（LLM 建议列表） |
| POST | `/api/ai/classify/` | 按交易内容返回推荐分类（固定规则 → 临时规则 → LLM） |
| POST | `/api/ai/extract/` | 截图上传识别（multipart）→ 金额/内容/分类/时间/来源（大模型视觉识别，P3/P4 共用） |
| GET | `/api/app/config` | App 版本检查/APK 下载地址/默认服务器（附 config_fingerprint 供排查缓存） |
| GET | `/api/statistics/monthly/` | 可选：按月收支合计（最简 GROUP BY） |
| POST | `/api/activate/check` | 激活码校验（全功能模式）：未激活可激活 / 已激活同设备 / 已绑定其他设备 |
| GET | `/api/activate/status` | 激活状态查询（App 启动/切换模式时调用） |
| POST | `/api/activate/upload` | 首次激活：上传 App 本地 sqlite → 云端 `{激活码}_cashtable.db`（开 WAL） |
| GET | `/api/activate/template` | 下载标准 sqlite 模板（自定义模式用户自行导入服务器） |

### 5.1 账单查询筛选参数

```
GET /api/transactions/?month=2026-08&category_id=3&keyword=美团&created_by=2&page=1&page_size=50
```

- `month`：`happened_at` 前缀匹配 `'2026-08%'`
- `keyword`：`content LIKE '%keyword%'`
- 其余字段精确相等过滤；收支方向由金额符号（`amount < 0` 支出 / `> 0` 收入）或所选分类 `kind` 决定
- 返回按 `happened_at DESC` 排序 + 简单分页（`page`/`page_size`）

### 5.2 批量写入与去重（核心逻辑）

```python
def batch_create_transactions(db: Session, rows: list[dict]) -> dict:
    """批量写入账单，按 external_id 去重，返回导入统计。"""
    imported = 0
    skipped = 0
    for row in rows:
        external_id = (row.get('external_id') or '').strip()
        if external_id:
            exists = db.query(Transaction).filter(
                Transaction.external_id == external_id
            ).first()
            if exists:            # 重复交易单号，跳过
                skipped += 1
                continue
        db.add(Transaction(**row))
        imported += 1
    db.commit()
    return {'total': len(rows), 'imported': imported, 'skipped': skipped}
```

### 5.3 AI 自动分类与规则晋升（核心逻辑）

分类优先级：**固定规则 → 临时规则 → LLM**。

```
POST /api/ai/classify/ {content: "美团外卖"}
  ├─ category_rules 命中？ ── 是 → 返回 {category_id, source: "rule"}（usage_count+1）
  ├─ category_rules_temp 命中（pending）？ ── 是 → 返回 {category_id, source: "temp"}（不重复调 LLM）
  └─ 否则调 LLM：
       prompt：列出全部一级分类，从候选中选出最匹配的一个，返回 JSON
       ├─ 结果写入 category_rules_temp（status=pending）
       └─ 返回 {category_id, source: "llm"}
```

保存账单时（POST `/api/transactions/`、`batch`）自动做**规则晋升**：

```python
def promote_rule_if_confirmed(db: Session, content: str, category_id: int) -> None:
    """用户保存账单时：分类来自临时规则且未被修改 → 晋升为固定规则。"""
    rule = db.query(CategoryRuleTemp).filter(
        CategoryRuleTemp.keyword == content,
        CategoryRuleTemp.status == 'pending',
    ).first()
    if not rule:
        return
    if rule.category_id == category_id:      # 用户认可了 LLM 的建议
        rule.status = 'confirmed'
        db.add(CategoryRule(keyword=content, category_id=category_id, source='llm'))
    else:                                     # 用户改选了分类：丢弃 LLM 建议
        rule.status = 'discarded'
    db.commit()
```

> 用户改选分类不学成规则（保持克制）；固定规则命中后直接复用，LLM 调用频率随使用自然下降。

### 5.4 可选的最简统计

```sql
-- 按月收支合计（接口 /api/statistics/monthly/?year=2026）
-- 收支方向：金额为负=支出，为正=收入
SELECT substr(happened_at, 1, 7) AS month,
       CASE WHEN amount < 0 THEN 'expense' ELSE 'income' END AS kind,
       SUM(amount) AS total
FROM transactions
WHERE happened_at LIKE '2026%'
GROUP BY month, kind;
```

> 只做这一档最简聚合；分类占比、趋势图等图表美化学后续需要再加。

---

## 6. Android 客户端

### 6.1 记账人机制（无登录）

- 首次启动：从 `/api/users/` 拉取记账人列表，选择"当前记账人"存入本地（SharedPreferences）。
- 设置页可随时切换当前记账人；记账时 `created_by` 自动带上。
- 不做任何登录/密码交互。

### 6.2 前端 UI 设计参考（钱迹风格）

> 钱迹为商业 App，其图标与图片素材有版权，本项目**仅参考其布局与交互风格**；图标统一使用开源 **Material Symbols**（Apache-2.0，可免费商用），避免直接打包钱迹素材带来的版权问题。

**整体风格**
- 白底、极简、无广告；主色邮政绿 `#00965E`，支出金额深灰、收入金额绿色 `#00B578`。
- 底部 3 个 Tab：**明细 / 统计 / 我的**。

**明细页（首页）**
- 顶部月份切换条：`‹ 2026年8月 ›`
- 月概览卡：收入 / 支出 / 结余三个数字
- 账单列表**按日分组**：日期行（如 `8月7日 周五 支出 ¥xx`）+ 当日条目；每条 = 分类图标（圆角方块底色）+ 分类名 + 交易内容，右侧金额（支出 `-35.00`、收入 `+8000.00`）
- 底部中央悬浮圆钮"记一笔"（`+`）

**记一笔页**
- 顶部"支出 | 收入"分段切换：切换后金额符号自动取负/取正，分类列表联动显示对应 `kind` 的分类
- 大号金额输入 + 数字键盘
- 分类九宫格（图标 + 名称，选中高亮），点"更多"展开该分类下的二级分类
- 下方可选：交易发生时间（默认当前，可修改）、交易内容输入框
- 保存按钮；记账人 = 当前选择的记账人（不显示输入）

**统计页（本版从简）**
- 月份切换 + 分类支出排行列表（金额从大到小）+ 收支合计；饼图/趋势图美化后续再加

**我的页**
- 切换记账人（点开下拉选择，v0.8 支持**新增/删除**）、切换模式（本地/自定义/全功能）、LLM 设置（Key 只写加密存储 + 测试连接）、自定义服务器设置、全功能激活、分类管理、悬浮球记账开关、检查更新、关于

**分类图标映射（Material Symbols 名称）**

| 分类 | 图标 | 分类 | 图标 |
|---|---|---|---|
| 三餐 | restaurant | 水电网燃气 | bolt |
| 零食 | cookie | 日用品 | shopping_basket |
| 衣服 | checkroom | 其他(支出) | more_horiz |
| 交通 | directions_bus | 退款 | assignment_return |
| 旅游 | luggage | 工资 | payments |
| 孩子 | child_care | 其他(收入) | add_circle |
| 宠物 | pets | | |

### 6.3 页面清单

| 页面 | 功能 |
|---|---|
| 首页/记一笔 | 支出/收入切换、金额（自动带正负号）、分类、交易时间、交易内容；记账人=当前选择 |
| 账单列表 | 按日分组，顶部筛选：月份、分类、记账人、关键词；下拉刷新 |
| 截图记账 | 相册选图（系统 Photo Picker） → 大模型识别 → 确认/微调 → 记账 |
| 自动记账（悬浮球入口） | 先系统截屏 → 点悬浮球 → 自动读相册最新截图识别预录 → 保存后删图并返回原界面；「从相册选择」兜底 |
| 悬浮球开关 | 「我的」页开关 + 权限引导（悬浮窗/通知）；前台服务常驻悬浮球 |
| 设置 | 切换记账人、分类管理、分类规则管理、悬浮球记账、检查更新、服务器地址、导入记录 |

### 6.4 截图 OCR 模块（后端大模型识别）

```
截图来源：系统截屏（P4 自动记账）/ 相册选择（P3 截图记账）/ 系统分享"导入到App" → 后端大模型识别
  └─ 返回 金额(带正负号) / 交易内容 / 分类 / 交易时间
  └─ 确认/微调表单（分类网格可长按选二级）→ POST /api/transactions/（带 external_id 去重）
```

### 6.5 悬浮球自动记账（P4，系统截屏 + 自动读相册 + 大模型识别）

**目标**：用户在其他 App 看到待记账账单 → 手动系统截屏 → 点悬浮球 → App 自动读取相册最新截图 → 大模型识别预录 → 确认保存后**自动删除该截图并退回原界面**。

**交互流程**

```
「我的」页「悬浮球记账」开关：
  ① 检查悬浮窗权限（SYSTEM_ALERT_WINDOW）→ 未开启跳系统设置引导，返回后自动继续
  ② 启动前台服务 FloatingBallService（foregroundServiceType="specialUse"，Android 14+ 需
     PROPERTY_SPECIAL_USE_FGS_SUBTYPE 属性声明用途）；开关状态 ON_RESUME 同步刷新

悬浮球常驻（TYPE_APPLICATION_OVERLAY，半透明圆钮，可拖动；仅入口，无任何截屏能力）
  └─ 点击悬浮球 → Intent 携带 EXTRA_AUTO_ACCOUNTING 启动 MainActivity
       （NEW_TASK|SINGLE_TOP，onCreate/onNewIntent 检测后 autoAccountingSeq 自增）
       └─ 进入「自动记账」页（AutoAccountScreen，key(seq) 强制全新实例，样式同截图记账页）
            ├─ 自动读取相册最新截图：MediaStore 查询（优先 Screenshots 目录，DATE_ADDED DESC）
            │    权限矩阵：SDK≥33 → READ_MEDIA_IMAGES；SDK≥29 → READ_EXTERNAL_STORAGE；SDK<29 免权限
            ├─ 未找到 → 提示「请先使用系统截屏后再点悬浮球」+「从相册选择」兜底
            ├─ 识别：压缩 ≤1600px → POST /api/ai/extract/（复用 P3 大模型识别）→ 预录表单
            ├─ 用户确认 → POST /api/transactions/ 记账
            └─ 保存成功后：删除该截图（contentResolver.delete；SDK≥30 失败时用
                 MediaStore.createDeleteRequest 系统确认弹窗）→ moveTaskToBack(true)
                 退回点击悬浮球前的原界面（App 退到后台）
```

**关键实现点**

- 前台服务：`FloatingBallService`（`com.cashtable.app.p4`），`startForeground` 后创建悬浮球；Android 14+ 声明 `foregroundServiceType="specialUse"` + property 子类型。
- 开关状态：`pendingBallStart`（rememberSaveable，防 Activity 重建丢失）+ ON_RESUME 自动继续授权流程；`justRequestedStart` + Handler 延迟 500ms 收敛服务异步启动，避免"实际已开启但 UI 仍显示未开启"。
- 相册读取：MediaStore 按 `DATE_ADDED DESC` 取最新一张（优先 `MediaStore.Images.Media.BUCKET_DISPLAY_NAME == 'Screenshots'`）；读取失败或未授权时提示，并可用 Photo Picker（`PickVisualMedia`，免权限系统选图）手动选图兜底。
- 删除截图：仅限自动读取场景（`autoScreenshot=true`）执行；手动选图不删除；非本 App 写入的媒体删除需系统确认（`createDeleteRequest` 返回 PendingIntent，用 `IntentSenderRequest.Builder` 包装发起）。
- 返回原界面：记账完成后 `(context as? Activity)?.moveTaskToBack(true)`，App 退到后台，回到点击悬浮球前的第三方 App。
- 识别复用：AutoAccountScreen 与截图记账页共用 `recognizeUri`（读取压缩 → Repository.extractFromImage → 表单回填），分类网格/表单组件改为 internal 供两页复用。

**权限清单**

| 权限 | 用途 | 说明 |
|---|---|---|
| SYSTEM_ALERT_WINDOW | 悬浮球显示 | 设置页引导跳系统设置 |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE | 前台服务 | Android 14+ 必需（specialUse 类型） |
| POST_NOTIFICATIONS | 前台服务通知 | Android 13+ 运行时申请 |
| READ_MEDIA_IMAGES | 读取相册截图 | Android 13+（API 33）运行时申请 |
| READ_EXTERNAL_STORAGE | 读取相册截图 | Android 12 及以下（maxSdkVersion=29，API 26-29） |

### 6.6 基础网络封装

```kotlin
// 简单的 Retrofit 配置：仅 BaseUrl，无拦截器、无鉴权
val retrofit = Retrofit.Builder()
    .baseUrl(prefs.serverUrl)          // 如 http://1.2.3.4:8000/
    .addConverterFactory(GsonConverterFactory.create())
    .client(OkHttpClient())
    .build()
```

---

## 7. 部署

**后端/数据库运行在本地电脑（Windows），云端仅做 frp 隧道转发与 APK 公网分发。**

```
本地电脑（Windows）：
  backend/                    # FastAPI 代码（venv）
    app/                      # 应用代码（config 读 .env，兼容 LLMAKPmode/LLMAPIKEY）
    data/                     # cashtable.db + app_config.json + apks/（勿删除）
    scripts/frp/win/.../frpc.toml   # frp 客户端配置（0.70.1）
  .env                        # 服务器账号/密码、LLM 配置（位于项目根）

云端服务器（124.223.74.139）：
  /usr/local/frps/frps + frps.toml  # frps 0.70.1，控制端口 7000，token 见配置
  └─ frps 仅隧道 8010 → 本地后端（开发备用通道）；APK 由云端后端 /apk 静态托管（无应用逻辑）
  └─ 安全组放行：7000 / 8000~8010
```

- 启动顺序（本地）：
  1. 后端：`backend` 目录下 `python -m app.main`（端口 8000，`PORT` 环境变量可覆盖，限 8000~8009）；
  2. （APK 不再本地分发：更新包由云端 `/apk` 静态托管，App 更新检查固定连接云端）；
  3. frp 客户端：运行 `backend/scripts/frp/win/.../frpc`（回连云端 7000，建立公网隧道）。
- LLM 配置：`.env` 配 `LLMAKPmode`/`LLMAPIKEY`（config.py 兼容读取），BASE_URL 默认智谱 `https://open.bigmodel.cn/api/paas/v4`；主模型 `glm-4v-flash`、备用 `glm-4.6v-flash`（`LLM_FALLBACK_MODELS`），429 退避重试后自动切备用。
- App 版本/下载配置：`backend/data/app_config.json`（版本号/APK 地址/MD5/changelog，改配置无需重启后端）；发版后核对磁盘文件与接口返回值一致（`/api/app/config` 附 `config_fingerprint` 排查缓存）。
- **HTTP 直连，不做 HTTPS**（内部自用、数据不敏感）。
- 备份：每日 `cp` SQLite 文件，保留 30 天，需要时手动下载。
- 可选加固（如公网暴露有顾虑，非必须）：云安全组仅放行家人常用出口 IP；或 nginx 加简单 basic_auth。
- 已知遗留：云端 frps / 本地 frpc / 本地后端均为手工启动，无开机自启（后续可加 systemd/启动脚本）。

---

## 8. 实施计划

| 阶段 | 内容 | 交付物 |
|---|---|---|
| P0 | FastAPI 骨架 + SQLite DDL + 健康检查 `/health` | 可运行后端 |
| P1 | 各表查询/写入接口 + 筛选 + batch 去重 + 可选月度统计 | 全量 API |
| P2 | Android 工程 + 记账人选择 + 记一笔 + 账单列表/筛选 | 可用 App（手动记账） |
| P3 | 截图记账（相册选图 → 后端大模型识别 → 确认/微调 → 记账） | 截图记账 |
| P4 | 悬浮球自动记账（用户手动系统截屏 → 悬浮球点击跳转 → 自动读相册最新截图 → 大模型识别预录 → 保存删图 + 退回原界面；悬浮球仅为入口） | 悬浮球自动记账 |
| P5 | 云端部署（后端 + APK 静态托管 + App 内更新）+ 每日备份 | 正式使用 |

> 依赖：P0→P1 串行；P2 可与 P1 并行（按接口文档联调）；P3 依赖 P2；P4 依赖 P3（复用 /api/ai/extract/ 与确认表单）。
> 实际进度：P0~P5 均已落地（0.7.x 运行中）。

---

## 9. 风险与对策（精简版）

| 风险 | 对策 |
|---|---|
| 系统安全页面（FLAG_SECURE）无法截取 | App 不参与截屏，由用户手动系统截屏完成 |
| 自动记账读不到相册最新截图（权限缺失） | 权限矩阵引导（API 33+ `READ_MEDIA_IMAGES`、API 26-29 `READ_EXTERNAL_STORAGE`）；仍未读到 → 提示「从相册选择」Photo Picker 免权限兜底 |
| 删除系统截图需系统确认 | `contentResolver.delete` 失败时（SDK≥30）用 `MediaStore.createDeleteRequest` 弹系统确认窗；手动选图场景不删除 |
| 悬浮球被系统限制/误关 | 悬浮窗权限引导跳系统设置；被关时通知栏提示 |
| 前台服务类型限制（Android 14+） | 改用 `specialUse` 类型 + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 属性声明用途 |
| 返回原界面失败/悬浮球未反应 | 服务启动状态用 rememberSaveable + ON_RESUME 收敛刷新；点击用 `key(seq)` 保证每次全新页面 |
| LLM 分类错误 | 分类可改；改选后临时规则标记 discarded，不学错规则 |
| LLM API 不可用/欠费/429 | 模型链主备切换（`glm-4v-flash` → `glm-4.6v-flash`）+ 429 退避重试；无法识别时提示手动记账 |
| 重复记账 | external_id 端侧查本地 + 服务端唯一索引双重去重 |
| SQLite 文件损坏/丢失 | 每日 cp 备份保留 30 天，定期手动验证 |
| HTTP 裸奔公网 | 默认信任家人网络；如暴露公网建议防火墙 IP 白名单（可选） |

---

## 10. 多模式运行与开源（v0.8）

### 10.1 三种运行模式

App 默认**本地模式**，可在「我的 → 切换模式」随时切换。

| 模式 | 数据存储 | 截图识别（AI） | 适用场景 |
|---|---|---|---|
| **本地模式**（默认） | App 自带 sqlite（schema 与后端一致），数据只在本机 | App 直连大模型，使用个人中心配置的 **LLM Key**（EncryptedSharedPreferences 加密存储，只显示头尾、不可复制） | 个人单机使用，无需任何服务器 |
| **自定义模式** | 用户自建服务器（部署本项目后端 + 导入标准 sqlite 模板），多人共用同一库 | 走后端 `/api/ai/extract/`（服务器配 LLM Key） | 有自己服务器的用户；**注意：多人同时写入存在 SQLite 写锁冲突风险** |
| **全功能模式** | 公共服务器（`124.223.74.139`），绑定激活码后按激活码路由到独立租户库 | 走公共服务器 API（支持并发，FastAPI async + SQLite WAL） | 直接激活使用全部系统功能 |

### 10.2 激活码与 keycode.db

- 新增独立数据库 **`keycode.db`**（默认 `backend/data/keycode.db`，与主库 cashtable.db 分离），表「全功能码」：

| 字段 | 说明 |
|---|---|
| code | 激活码（主键，管理员通过 `backend/scripts/gen_keycode.py` 生成并发放） |
| activated_at | 激活时间 `'YYYY-MM-DD HH:MM:SS'`（空=未激活） |
| device_mac | 激活设备标识（App 端取 ANDROID_ID，Android 10+ 无法可靠获取真实 MAC） |
| is_activated | 0/1 是否已激活 |

- 激活码格式 `JI-XXXX-XXXX-XXXX`；一个激活码首次激活即绑定设备，重复使用会报「已绑定其他设备」。

### 10.3 全功能模式激活流程

```
「我的 → 切换模式 → 全功能模式」（未激活则先进入激活页）
  ├─ 输入激活码 → POST /api/activate/upload（multipart：code + device_mac + 本地 cashtable.db）
  │    ① 服务端校验激活码（不存在 / 已绑定其他设备 → 403）
  │    ② 保存 App 的 sqlite 为 ACTIVATED_DB_DIR/{激活码}_cashtable.db
  │    ③ 开启该库 SQLite WAL 模式（并发读写）
  │    ④ 标记激活（记录激活时间与设备）
  └─ 激活成功后 App 切换到全功能模式，后续所有数据/AI 请求携带请求头：
      X-Activate-Code: {激活码}  X-Device-Mac: {ANDROID_ID}
      由 deps.get_db 校验激活状态后路由到对应租户库（按激活码缓存 engine）。
```

- 激活接口为 `async`（FastAPI async + 同激活码进程内锁），可支撑并发激活/访问。
- 云端（Linux）`ACTIVATED_DB_DIR` 固定 `/home/codefile/cashtabledb`；本地 Windows 开发用 `backend/data/activated`。
- 幂等：同一设备重复激活会覆盖已有租户库（重装后恢复数据用）。

### 10.4 自定义模式与标准模板

- 「我的 → 切换模式 → 自定义模式」填写：服务器地址（IP/域名）+ 端口（仅 8000~8009）+ 服务器上 sqlite 目录。
- 「下载标准 sqlite 模板」：`GET /api/activate/template`（公共服务器提供，含表结构与初始分类、无数据），下载后导入自己的服务器，再部署本项目后端即可连接。
- 自定义模式请求不带激活头，走后端默认库 `cashtable.db`。

### 10.5 本地模式数据层与 LLM 直连

- Android 新增原生 `SQLiteDatabase` 数据层（`LocalDbHelper` / `LocalAccountDataSource`），schema 与后端完全一致（users / categories / transactions / category_rules / category_rules_temp / import_records），便于全功能模式激活时直接上传接管。
- `Repository` 按当前模式路由：本地 → sqlite（IO 线程）；自定义/全功能 → Retrofit API。
- `LlmClient`：OpenAI 兼容 `/chat/completions` 视觉识别，复用后端系统提示词与时间/金额/分类规范化逻辑；主模型 `glm-4v-flash`，429 退避重试后切备用 `glm-4.6v-flash`；分类候选取自本地 categories 表。
- 「测试连接」按钮：个人中心 LLM 设置页直接发最小请求验证连通性。

### 10.6 记账人新增/删除

- 「我的 → 切换记账人」：顶部输入框新增记账人；每条记录右侧删除图标（确认后删除，名下账单 `created_by` 置空）。
- 首次启动页同样支持「新增并选择」。
- 后端：`POST /api/users/`（已有）、`DELETE /api/users/{id}/`（v0.8 新增）。

### 10.7 命名与 Logo

- App 名称由「家庭记账」改为「截记」（`strings.xml app_name`）。
- 启动图标使用 `docs/APP logo.png`（打包为 `res/mipmap-nodpi/ic_launcher.png`，替换原自适应矢量图标）。

### 10.8 开源与部署注意

- 本项目已开源，默认本地模式开箱即用；公共服务器激活/多租户为可选增强。
- 部署（后端仍在本机运行 + frp 穿透公网，见第 7 节）：新增 `keycode.db` 自动建表；全功能激活目录云端固定 `/home/codefile/cashtabledb`（需创建目录并保证后端进程可写）。
- 激活码由管理员运行 `python -m scripts.gen_keycode <数量>` 生成后线下发放。
- LLM Key：本地模式由 App 端维护（加密存储）；自定义/全功能模式由服务器 `.env` 配置，App 端在非本地模式不可编辑 Key 字段。
