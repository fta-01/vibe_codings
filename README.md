# 🎮 Vibe Coding 项目集

一个用 **vibe coding(AI 辅助编程)** 迭代出来的项目合集:每个项目都是从一句自然语言需求开始,
让 AI 直接写代码、我在本机运行验证、再继续对话调整,直到功能真实可用。
这里的每个项目都不是"演示壳",**全部在本机跑通过**。

## 项目列表

| # | 项目 | 一句话介绍 | 技术栈 | 状态 |
|---|------|-----------|--------|------|
| 1 | [01-mini-game-hub](projects/01-mini-game-hub/) | 从 GitHub 拉取开源小游戏**源码到本地**,免编译、离线即点即玩 | Python · pywebview · GitHub API | ✅ 可用 |
| 2 | [02-workout-checkin](projects/02-workout-checkin/) | 本地离线 Android **运动打卡** App:连续天数、运动库、组合训练、完成音效 | Kotlin · Compose · Room | ✅ 可用 |

> 每个项目的 README 末尾都有「开发记录」,记录了这次 vibe coding 的迭代过程、踩过的坑和学到的东西。

## 目录结构

```
vibe-projects/
├── README.md              # 项目集索引(本页)
├── LICENSE                # 集合级 MIT 许可
├── docs/
│   ├── PROJECT_TEMPLATE.md  # 每个项目 README 的模板
│   └── ADD_PROJECT.md       # 新增项目的步骤与检查清单
└── projects/
    ├── 01-mini-game-hub/      # 小游戏聚合客户端(Python)
    └── 02-workout-checkin/    # 运动打卡 Android App
```

## 新增一个项目

1. 在 `projects/` 下新建目录:`projects/02-你的项目名/`
2. 复制 `docs/PROJECT_TEMPLATE.md` 作为项目的 `README.md` 并填好
3. 保证项目在仓库根也能一键运行(用相对 `__file__` 的路径,别依赖当前工作目录)
4. 补上 `.gitignore`,别把 token / 缓存 / 大数据文件提交进去
5. 跑通后写「开发记录」

详细步骤和检查清单见 **[docs/ADD_PROJECT.md](docs/ADD_PROJECT.md)**。

## 关于 vibe coding

- 用自然语言描述"我想要什么",AI 写代码,我做验证和纠偏;
- 小步快跑:一个功能一次对话,跑通再聊下一个;
- 代码可以不是最优,但**必须是能跑的**;
- 每一次真实运行验证的结论,都值得记进开发记录。
