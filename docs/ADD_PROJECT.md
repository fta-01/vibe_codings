# 新增一个 vibe 项目到项目集

monorepo 结构下,每个 vibe 项目 = `projects/` 下独立的一个子目录。新项目照这个流程走。

## 步骤

```text
projects/
├── 01-mini-game-hub/      <- 已有的第 1 个项目
└── 02-workout-checkin/    <- 已有的第 2 个项目
```

### 1. 建目录

在 `projects/` 下新建,编号接续:`02-xxx`、`03-xxx`。名字用一两句话能说清、且跟项目类型一致。

### 2. 复制 README 模板

把 [`docs/PROJECT_TEMPLATE.md`](PROJECT_TEMPLATE.md) 复制为 `projects/02-xxx/README.md`,逐项填好。
README 至少要包含:一句话 pitch、截图、快速开始、开发记录。

### 3. 保证"在仓库根能一键跑"

项目相互独立,别人 clone 仓库后是进入某个子目录再运行的。所以:

- **路径用 `__file__` 相对解析,不要依赖当前工作目录**(进程 CWD)。
- 启动命令写进 README,例如 `cd projects/02-xxx && python main.py`。
- 所有入口脚本在本机 `python-m compileall` 通过、真实跑一次验证过再提交。

### 4. 配 .gitignore

每个项目目录内放独立的 `.gitignore`。**常见红线:**

- 任何可能包含 token / 密钥的配置文件(本集合约定统一叫 `config.json` 并在根 .gitignore 里忽略)
- 下载到本地的数据 / 缓存 / 大数据文件(`data/`、`games/` 这类)
- `__pycache__/`、`.venv/`、编辑器目录

### 5. 写在根 README 的项目列表

在**根** `README.md` 的项目列表表格里加一行:编号、名称、一句话介绍、技术栈、状态。

### 6. 写「开发记录」

在项目 README 末尾补一节 dev log:第一轮需求是什么、中途发现什么问题、怎么改、
哪次验证给出了关键结论。不用多,真实最重要。

## 提交前检查清单

- [ ] `projects/02-xxx/README.md` 填好(有截图、有快速开始、有开发记录)
- [ ] 目录内 `.gitignore` 正确,无密钥 / 大文件
- [ ] `python -m compileall` 全绿,并且实跑过一次
- [ ] 根 `README.md` 项目列表已加一行
- [ ] 根 `.gitignore` 已覆盖通用忽略项(见下)

## 根 .gitignore 约定(通用忽略项)

```gitignore
# 密钥与本地配置(约定:任何个人配置一律叫 config.json)
config.json
local.properties
*.keystore
*.jks

# 运行时数据 / 下载内容(如需忽略某项目数据,请限定到具体目录,勿用裸 data/)
projects/01-mini-game-hub/games/
projects/01-mini-game-hub/data/

# Android 构建产物
*.apk
.gradle/
build/
.kotlin/
__pycache__/