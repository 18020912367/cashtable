# 家庭记账小工具 · 极简版设计文档

> 版本：v0.3（v0.2 演进稿）
> 定位：不超过 4 人的家庭内部自用小工具。FastAPI + SQLite 轻量后端，Android App 作为入口（截图 OCR + 悬浮球截图记账 + AI 自动分类）。
> 简化原则：**不做认证、不做密码、不做 HTTPS、不做复杂聚合与图表美化**，数据表直连查询 + 筛选条件，能满足基本记账留存即可。

---

## 1. 概述

### 1.1 定位与边界

- 仅个人与家庭成员（≤4 人）使用，数据不敏感、无并发压力。
- 核心能力：手动记账、账单查询与筛选、截图 OCR 记账、悬浮球截图记账（OCR 视觉识别 + AI 自动分类）。
- 账号信息**手动写入 users 表**（无注册/登录/管理员界面），App 端选择"当前记账人"即可记账。

### 1.2 相比 v0.1 的简化项

| 原方案 | 简化后 |
|---|---|
| PostgreSQL + Redis | SQLite 单文件 |
| Django + DRF + 权限体系 | FastAPI，无权限、无认证 |
| 账号注册 + 邀请码 + 登录限流 | 无注册登录，users 表手写 |
| HTTPS + 域名 | HTTP 直连 IP:端口 |
| 复杂统计/图表 | 仅基础筛选查询，统计可选（最简 GROUP BY） |
| Family/Membership 多角色 | 去掉，共用一张 transactions 表，`created_by` 记记账人 |
| 无障碍自动记账（读节点树，改版即失效） | 悬浮球截图 OCR（看画面，抗改版）+ AI 自动分类 |

---

## 2. 总体架构

```
┌──────────────────────── Android App (Kotlin) ────────────────────────┐
│ 记账人选择(本地) · 记一笔 · 账单列表+筛选 · 截图OCR · 悬浮球截图记账 │
└───────────────┬──────────────────────────────────────────────────────┘
                │ HTTP (http://服务器IP:8000，无加密)
┌───────────────▼──────────────────────────────────────────────────────┐
│                       云服务器（轻量配置即可）                         │
│   uvicorn ── FastAPI ── SQLAlchemy ── SQLite 单文件 (cashtable.db)    │
│                    │                                                  │
│                    └─ 每日备份脚本：cp cashtable.db → 备份目录(保留30天) │
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
| OCR | 端侧 ONNX Runtime + PP-OCRv4 mobile | 无 GMS，端侧识别，零延迟 |
| AI 分类 | OpenAI 兼容 LLM API（DeepSeek/通义等） | 本地规则未命中时调用，结果自动学习为规则；api_key 配在后端 |
| 悬浮球截图 | WindowManager 悬浮球 + MediaProjection | 悬浮球常驻，点击截当前屏（需悬浮窗 + 录屏授权） |
| 网络 | Retrofit + OkHttp | 与 FastAPI 对接 |
| 部署 | uvicorn 进程（可加 supervisor/docker 二选一） | 直接跑最简单 |
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
| GET/POST | `/api/users/` | 记账人列表 / 新增（一般只在 sqlite 手动写） |
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
| GET | `/api/statistics/monthly/` | 可选：按月收支合计（最简 GROUP BY） |

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
- 白底、极简、无广告；主色蓝 `#2E6FF2`（观感贴近钱迹），支出金额深灰、收入金额绿色 `#00B578`。
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
- 切换记账人（点开下拉选择）、分类管理（增删一级/二级分类）、分类规则管理（查看/删除固定规则与待确认规则）、服务器地址、导入记录、关于

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
| 截图 OCR | 相册/分享进入 → 端侧识别 → 逐行确认/编辑 → 批量提交 |
| 悬浮球记账 | 悬浮球开关 + 权限引导（悬浮窗/录屏）+ 最近识别记录 |
| 设置 | 切换记账人、分类管理、分类规则管理、服务器地址、导入记录 |

### 6.4 截图 OCR 模块（复用 v0.1 设计，交互简化）

```
截图来源：悬浮球截图（MediaProjection）/ 相册选择 / 系统分享"导入到App" → 端侧 OCR（ONNX Runtime + PP-OCRv4）
  └─ 文字块按 Y 轴聚合成流水行
  └─ 正则提取：时间 / 商户(→交易内容) / 收支(支出→负号、收入→正号) / 金额
  └─ 列表页可点选修正（分类/收支方向），未知类型标红不提交
      └─ 确认 → POST /api/transactions/batch/（带 external_id 去重）
```

### 6.5 悬浮球截图记账（替代无障碍方案）

**为何替代**：无障碍读节点树（DOM），微信/支付宝改版即失效；截图 OCR 是看画面，视觉不变就能识别，更抗改版、实现更简单。

```
悬浮球常驻（TYPE_APPLICATION_OVERLAY，可拖动/点击）
  └─ 点击 → MediaProjection 截当前全屏（首次弹系统授权，Android 14+ 重启后需重新授权）
      └─ 端侧 OCR 识别整屏文字 + 坐标
      └─ 版面解析：定位金额文本(¥/数字) → 取金额上/下方最近的文本块为商户(→交易内容)，
          |           按"支付成功页 / 账单详情页"两种版式分别处理
      └─ 弹确认卡片：预填 金额(带正负号) / 交易内容 / 交易时间
          ├─ AI 分类：content → /api/ai/classify/ → 推荐分类（可改）
          └─ 确认 → POST /api/transactions/（保存时自动做规则晋升）
```

- 与手动记一笔共用同一确认卡片，只是字段已预填，用户只需确认/微调。
- 悬浮球需"悬浮窗 + 录屏（MediaProjection）"两个授权；授权页做引导，token 失效时提示重新授权。

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

```
目录：
  /opt/cashtable/
    app/            # FastAPI 代码
    data/           # cashtable.db（数据目录，勿删除）
    backup/         # 每日备份
```

- 启动：`uvicorn app.main:app --host 0.0.0.0 --port 8000`（cron 保活或 supervisor/docker 二选一）。
- AI 分类配置：`.env` 中配 `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`（OpenAI 兼容，如 DeepSeek）；不配置时自动降级为纯本地规则分类。
- **HTTP 直连，不做 HTTPS**（内部自用、数据不敏感）。
- 备份：cron 每日 `cp data/cashtable.db backup/cashtable-$(date +%F).db`，保留 30 天，需要时手动下载。
- 可选加固（如公网暴露有顾虑，非必须）：
  - 云安全组仅放行家人常用出口 IP；
  - 或 nginx 加一个简单 basic_auth（一条配置的事，但按你的要求默认不做）。

---

## 8. 实施计划

| 阶段 | 内容 | 交付物 |
|---|---|---|
| P0 | FastAPI 骨架 + SQLite DDL + 健康检查 `/health` | 可运行后端 |
| P1 | 各表查询/写入接口 + 筛选 + batch 去重 + 可选月度统计 | 全量 API |
| P2 | Android 工程 + 记账人选择 + 记一笔 + 账单列表/筛选 | 可用 App（手动记账） |
| P3 | 截图 OCR（端侧识别 + 批量确认） | 截图记账 |
| P4 | 悬浮球截图记账（MediaProjection + 版面解析 + AI 分类 + 规则晋升） | 悬浮球记账 |
| P5 | 服务器部署 + 每日备份 | 正式使用 |

> 依赖：P0→P1 串行；P2 可与 P1 并行（按接口文档联调）；P3 依赖 P2；P4 依赖 P3（复用 OCR）与 P1 的 `/api/ai/classify/`。

---

## 9. 风险与对策（精简版）

| 风险 | 对策 |
|---|---|
| 截图 OCR 看错金额/商户 | 识别置信度低时确认卡片标黄提示，人工核对后保存 |
| MediaProjection 授权失效 | Android 14+ token 重启后失效，授权页引导重新授权 |
| 悬浮球被系统限制/误关 | 悬浮窗权限引导；被关时通知栏提示 |
| LLM 分类错误 | 分类可改；改选后临时规则标记 discarded，不学错规则 |
| LLM API 不可用/欠费 | `.env` 未配 key 时自动降级为本地规则分类，功能不中断 |
| 重复记账 | external_id 端侧查本地 + 服务端唯一索引双重去重 |
| SQLite 文件损坏/丢失 | 每日 cp 备份保留 30 天，定期手动验证 |
| HTTP 裸奔公网 | 默认信任家人网络；如暴露公网建议防火墙 IP 白名单（可选） |
