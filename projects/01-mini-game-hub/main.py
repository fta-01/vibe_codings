"""小游戏聚合客户端 —— 入口。

只玩 GitHub 开源游戏的本地小客户端:
1. 启动本地静态 HTTP 服务,服务已下载到 games/ 目录的游戏源码
2. 通过 GitHub API 搜索游戏仓库,整仓下载源代码到本地
3. 只保留免编译、可直接游玩的静态游戏,在本地 iframe 播放(离线可用)
"""

from __future__ import annotations

import json
import threading
from pathlib import Path

import webview

import config as config_mod
import local_server
from github_source import (
    GAMES_DIR, _entry_to_ui, _load_cache,
    delete_local_games, get_github_games, repair_local_games, verify_local_games,
)

ROOT = Path(__file__).resolve().parent


class Api:
    def __init__(self):
        self._window = None

    def bind_window(self, window) -> None:
        self._window = window

    # ---- 配置 ----
    def get_config(self) -> dict:
        token = config_mod.get_github_token()
        return {
            "github_token": token or "",
            "github_token_set": bool(token),
            "github_token_env": bool(config_mod.os_env_token()),
            "repo_count": config_mod.get_repo_count(),
        }

    def set_github_token(self, token: str) -> None:
        config_mod.set_github_token(token)
        return self.get_config()

    def set_repo_count(self, n: int) -> dict:
        config_mod.set_repo_count(n)
        return self.get_config()

    # ---- 游戏 ----
    def get_games(self, refresh: bool = False, repo_count: int | None = None) -> dict:
        """后台扫描 + 进度回显;refresh=False 且本地有游戏时秒开。
        refresh=True 且本地库已建立 → 探测 repo_count 个新仓库,新增其中的可玩游戏。"""
        result: dict = {}
        done = threading.Event()

        def progress(msg: str):
            if self._window:
                payload = json.dumps(msg, ensure_ascii=False)
                self._window.evaluate_js(f"window.__ghProgress({payload})")

        def worker():
            try:
                result.update(get_github_games(
                    config_mod.get_github_token(), refresh=bool(refresh),
                    progress=progress, repo_count=repo_count))
            except Exception as exc:
                result["ok"] = False
                result["games"] = []
                result["error"] = f"{type(exc).__name__}: {exc}"
            finally:
                done.set()

        threading.Thread(target=worker, daemon=True).start()
        done.wait(timeout=600)
        if not result:
            result = {"ok": False, "games": [], "error": "扫描超时"}
        if result.get("games") and not result.get("error"):
            result["note"] = "已下载到本地,离线可玩"
        return result

    def open_local(self, url: str) -> None:
        import webbrowser

        def _open():
            webbrowser.open(url)

        threading.Thread(target=_open, daemon=True).start()

    def delete_games(self, items: list) -> dict:
        """删除指定游戏(repo+rel 匹配),同步清理本地文件;支持批量。"""
        try:
            remaining, n = delete_local_games(list(items or []))
            return {"ok": True, "deleted": n,
                    "games": [_entry_to_ui(e) for e in remaining]}
        except Exception as exc:
            return {"ok": False, "deleted": 0, "games": [], "error": f"{type(exc).__name__}: {exc}"}

    def verify_games(self, repair: bool = False) -> dict:
        """可玩性巡检。repair=True 会对缺失本地资源的游戏尝试联网补齐后剔除。"""
        result: dict = {}
        done = threading.Event()

        def progress(msg: str):
            if self._window:
                payload = json.dumps(msg, ensure_ascii=False)
                self._window.evaluate_js(f"window.__ghProgress({payload})")

        def worker():
            try:
                if repair:
                    rep = repair_local_games(progress)
                    local = verify_local_games()  # 补完后再做一次纯本地核定
                    result.update(rep)
                    result["checked2"] = local["checked"]
                    result["remaining_broken"] = len(local["removed"])
                    result["network"] = local.get("network", [])
                else:
                    local = verify_local_games()
                    result.update(local)
            except Exception as exc:
                result["error"] = f"{type(exc).__name__}: {exc}"
            finally:
                done.set()

        threading.Thread(target=worker, daemon=True).start()
        done.wait(timeout=600)
        if not result:
            result = {"error": "检查超时"}
        cache = [_entry_to_ui(e) for e in _load_cache()]
        result["games"] = cache
        result["ok"] = True
        return result


def main() -> None:
    GAMES_DIR.mkdir(parents=True, exist_ok=True)
    port = local_server.start(GAMES_DIR)
    api = Api()
    window = webview.create_window(
        "小游戏聚合客户端(GitHub 本地)",
        str(ROOT / "web" / "index.html"),
        js_api=api,
        width=1280,
        height=820,
        min_size=(960, 640),
        background_color="#0f1420",
    )
    api.bind_window(window)
    print(f"🎮 本地游戏服务: http://127.0.0.1:{port}  (games 目录: {GAMES_DIR})")
    webview.start(debug=False)


if __name__ == "__main__":
    main()