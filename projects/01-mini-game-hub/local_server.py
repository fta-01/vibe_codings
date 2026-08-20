"""本地静态 HTTP 服务:把下载到本地的游戏目录(games/)以 http://127.0.0.1:端口 提供,
iframe 播放本地游戏时资源相对路径可正常解析,且完全离线可用。"""

from __future__ import annotations

import functools
import http.server
import threading
from pathlib import Path

import socket

PORT = 8765
_server: http.server.ThreadingHTTPServer | None = None
_port = PORT


def start(root: Path) -> int:
    """在后台线程启动本地静态服务,返回实际端口。root 为游戏根目录。"""
    global _server, _port

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(root), **kwargs)

        def log_message(self, *args):  # 静音访问日志
            pass

        def end_headers(self):
            self.send_header("Cache-Control", "no-store")
            self.send_header("Access-Control-Allow-Origin", "*")
            super().end_headers()

    for attempt in range(100):
        try:
            _server = http.server.ThreadingHTTPServer(("127.0.0.1", _port + attempt), Handler)
            _port = _port + attempt
            break
        except OSError:
            continue
    t = threading.Thread(target=_server.serve_forever, daemon=True)
    t.start()
    return _port


def url(*parts: str) -> str:
    """把本地相对路径拼成 http://127.0.0.1:端口/... 的播放地址。"""
    return f"http://127.0.0.1:{_port}/" + "/".join(str(p).replace("\\", "/") for p in parts)


def stop() -> None:
    if _server:
        _server.shutdown()


if __name__ == "__main__":
    import time

    p = start(Path(r"D:\cs\all_game2\games"))
    print("serving on", p, url("test", "index.html"))
    time.sleep(2)