"""本地配置:存储 GitHub api_key 等用户设置(config.json)。

安全说明:token 仅保存在本机 config.json,不随代码提交。
建议将 config.json 加入 .gitignore。
"""

from __future__ import annotations

import json
import os
from pathlib import Path

CONFIG_FILE = Path(__file__).resolve().parent / "config.json"


def load_config() -> dict:
    cfg = {}
    try:
        with open(CONFIG_FILE, encoding="utf-8") as f:
            cfg = json.load(f)
    except (OSError, json.JSONDecodeError):
        pass
    return cfg


def save_config(cfg: dict) -> None:
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


def get_github_token() -> str | None:
    """优先级:环境变量 > config.json。"""
    env = os.environ.get("GITHUB_TOKEN", "").strip()
    if env:
        return env
    return (load_config().get("github_token") or "").strip() or None


def os_env_token() -> bool:
    return bool(os.environ.get("GITHUB_TOKEN", "").strip())


def set_github_token(token: str) -> None:
    cfg = load_config()
    token = (token or "").strip()
    if token:
        cfg["github_token"] = token
    else:
        cfg.pop("github_token", None)
    save_config(cfg)


MAX_REPO_COUNT = 20


def get_repo_count() -> int:
    """每轮探测的候选仓库数(1-MAX_REPO_COUNT,默认 5);每轮从这些仓库的新游戏全部加入。"""
    try:
        n = int(load_config().get("repo_count", 5))
    except (TypeError, ValueError):
        n = 5
    return max(1, min(MAX_REPO_COUNT, n))


def set_repo_count(n: int) -> int:
    cfg = load_config()
    try:
        n = int(n)
    except (TypeError, ValueError):
        n = 5
    n = max(1, min(MAX_REPO_COUNT, n))
    cfg["repo_count"] = n
    save_config(cfg)
    return n


if __name__ == "__main__":
    print("token:", get_github_token())
