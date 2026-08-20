"""GitHub 游戏源:拉取开源小游戏的【源代码文件】到本地,只保留免编译、拉取后即可游玩的静态游戏。

通道设计(实测网络内 cdn.jsdelivr 与 api.github.com 稳定可靠):
- 枚举文件树:api.github.com/git/trees(每仓库 1 次调用)
- 下载文件:cdn.jsdelivr.net/gh/... 直连(快、不占 GitHub 配额);失败回退 contents API(base64)

流程:
1. 精选合集 + Search API 搜索候选仓库(带 api_key 搜索配额更高、扫描更多)。
2. 取每仓库文件树,按结构提取可玩游戏条目(根 index.html / N-游戏名 / 分类目录 / 单文件),
   **只保留免编译静态结构**:
   - 根目录有构建配置(vite/webpack/tsconfig 等)或 package.json → 判定需编译,跳过
   - 单个游戏文件/文件夹体积过大(>12MB)也跳过
3. 把每个条目需要的源码文件下载到本地 games/<owner>__<repo>/。
4. 播放走本地 HTTP 服务,完全离线可玩。
"""

from __future__ import annotations

import base64
import json
import os
import posixpath
import re
import shutil
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

import local_server

API = "https://api.github.com"
ROOT = Path(__file__).resolve().parent
GAMES_DIR = ROOT / "games"
CACHE_FILE = ROOT / "data" / "local_games.json"
SKIPPED_FILE = ROOT / "data" / "skipped_repos.json"
CURSOR_FILE = ROOT / "data" / "search_cursor.json"
MANIFEST_ID = "2"
MAX_ENTRIES = 260
MAX_BYTES_PER_GAME = 12 * 1024 * 1024  # 单个游戏条目总大小上限

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0",
      "Accept": "application/vnd.github+json"}

# 精选合集(全部为免编译静态游戏,保证内容质量与数量)
# ENABLE_HEAVY_COLLECTIONS:game-space 单个游戏含 100+ 资源文件,
# 首次拉取较慢;默认关闭保证 demo 秒速拉取,需要中文合集时可开启。
ENABLE_HEAVY_COLLECTIONS = False

CURATED = [
    {"full_name": "SinceraXY/GameHub", "branch": "main", "kind": "category-folders"},
    {"full_name": "wangzifan396-wzf/mini-browser-games", "branch": "main", "kind": "root-files"},
    {"full_name": "mumuy/pacman", "branch": "master", "kind": "single"},
]
if ENABLE_HEAVY_COLLECTIONS:
    CURATED.insert(0, {"full_name": "chengzuopeng/game-space", "branch": "master", "kind": "numbered-folders"})

CURATED_CAPS = {"root-files": 120, "numbered-folders": 30, "category-folders": 50, "single": 1}
MAX_FILES_PER_GAME = 100  # 单个游戏文件数上限(过滤过大/资源碎片化的游戏)

# ---------- 本地可玩性检查 ----------
_REMOTE_REF = re.compile(r"^(?:https?:)?//|^data:|^(?:about|blob):", re.I)
_ATTR_URL = re.compile(r'''(?:src|href|poster|data-src)\s*=\s*["']([^"'\s]+)["']''', re.I)
_SRCSET = re.compile(r'''(?:srcset)\s*=\s*["']([^"']*)["']''', re.I)
_URL_REF = re.compile(r'''url\(\s*["']?([^"'()]+?)["']?\s*\)''', re.I)
_TAG_RE = re.compile(r"<(?:img|source|audio|video|script|link|iframe|input|embed|track|object|style)[^>]*>", re.I)
_STYLE_RE = re.compile(r"<style[^>]*>(.*?)</style>", re.I | re.S)


def _iter_local_refs(text: str) -> list[str]:
    """仅从真实 HTML 标签与 <style> 块里提取相对资源引用(不从内联 JS 里乱抓),
    返回原始引用字符串;不会过滤远程(调用方各自处理)。"""
    refs: set[str] = set()
    for tag in _TAG_RE.findall(text):
        for m in _ATTR_URL.finditer(tag):
            if m.group(1).startswith("//"):
                continue
            refs.add(m.group(1))
        sm = _SRCSET.search(tag)
        if sm:
            for part in sm.group(1).split(","):
                tok = part.strip().split(None, 1)[0]
                if tok and not tok.startswith("//"):
                    refs.add(tok)
    for style in _STYLE_RE.findall(text):
        for m in _URL_REF.finditer(style):
            if not m.group(1).startswith("//"):
                refs.add(m.group(1))
    return sorted(r.split("?")[0].split("#")[0] for r in refs if r)


def _resolve_ref(base: str, ref: str) -> str | None:
    """把当前文件相对它的引用解析为仓库根相对路径;越出仓库根的返回 None。"""
    base_dir = base.rsplit("/", 1)[0] if "/" in base else ""
    joined = posixpath.normpath(posixpath.join(base_dir, ref))
    if joined == ".":
        return None
    if joined.startswith("../"):
        return None
    return joined


def _scan_local_refs(root: Path, rel: str) -> tuple[list[str], list[str]]:
    """扫描入口 HTML 的资源引用,返回 (缺失的本地相对路径, 远程依赖 URL 列表)。
    判不可玩条件=存在本地引用但文件缺失(哪怕远程有一份,本地也渲染不出来)。"""
    local_file = root / rel
    if not local_file.exists():
        return [rel], []
    missing: set[str] = set()
    remote: list[str] = []
    try:
        text = local_file.read_text("utf-8", errors="ignore")
    except OSError:
        return [], []
    for ref in _iter_local_refs(text):
        if _REMOTE_REF.match(ref):
            remote.append(ref)
            continue
        target = _resolve_ref(rel, ref)
        if target is None:
            remote.append(ref)
            continue
        p = root / target
        if not p.exists() or p.stat().st_size == 0:
            missing.add(target)
    return sorted(missing), sorted(set(remote))

SEARCH_QUERIES = [
    "topic:html5-game", "topic:browser-game", "topic:mini-game",
    "小游戏 in:name", "html5 小游戏 in:name", "html5 game in:name", "minigame in:name",
]
JUNK_RE = re.compile(
    r"engine|core\b|sdk|framework|typescript|starter|template|cli|plugin|boilerplate|"
    r"api\b|library|toolkit|devkit|vite|webpack|leetcode|algorithm|prompts?|weekly|notes|"
    r"python-|book|tutorial|course",
    re.I,
)
POSITIVE_RE = re.compile(r"game|游戏|html5|play|小游戏", re.I)
# 根目录存在这些构建配置 = 需要编译 -> 不能直接游玩
BUILD_CONFIGS = {
    "webpack.config.js", "webpack.config.mjs", "webpack.config.ts",
    "vite.config.js", "vite.config.ts", "vite.config.mjs",
    "rollup.config.js", "rollup.config.mjs",
    "tsconfig.json", "angular.json", "next.config.js", "nuxt.config.js",
    "svelte.config.js", "gulpfile.js", "gruntfile.js",
}


def _sanitize(full_name: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]", "__", full_name)


IMG_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif"}
COVER_KEYWORDS = re.compile(r"cover|screenshot|preview|poster|thumb|logo|splash|banner|title|bg\b|背景|封面", re.I)


def _beautify(name: str) -> str:
    if re.search(r"[\u4e00-\u9fff]", name):
        return name
    words = [w for w in re.split(r"[^a-zA-Z0-9]+", name) if w]
    if not words:
        return name
    return " ".join(w[0].upper() + w[1:] for w in words)


def _title_from_folder(folder: str) -> str:
    name = folder.split("/")[-1]
    name = re.sub(r"^\d+\s*[-_]\s*", "", name)
    name = re.sub(r"^[-_\s]+|[-_\s]+$", "", name)
    return _beautify(name) or folder


# 普通查询翻到底(单查询最多 1000 条)后,按 star 区间逐级下探,直到 0★
STAR_BUCKETS = ["stars:200..499", "stars:100..199", "stars:50..99",
                "stars:20..49", "stars:10..19", "stars:5..9",
                "stars:1..4", "stars:0"]


class GitHubSource:
    def __init__(self, token: str | None = None):
        self.token = token
        self.has_token = bool(token)
        self.rate_limited = False
        self.session = requests.Session()
        headers = dict(UA)
        if token:
            headers["Authorization"] = f"Bearer {token}"
        self.session.headers.update(headers)

    # ---------- Search API ----------
    # 普通查询翻到底(单查询最多 1000 条)后,按 star 区间逐级下探,直到 0★
    def _search_repos(self, exclude: set[str] | None = None, max_repos: int | None = None) -> list[dict]:
        """逐条查询、不限深度地翻页:先查整段(top 段),翻到底(≈1000 条)后
        再按 star 区间向下分段,一直下探到 0★。每轮完成/中断都把每段翻页位置
        写回 search_cursor.json,下轮从上次落点继续,绝不回头重复。
        返回按 stars 递减的前 max_repos 个未见候选。"""
        queries = SEARCH_QUERIES if self.has_token else SEARCH_QUERIES[:3]
        per_page = 30 if self.has_token else 15
        gap = 0.3 if self.has_token else 1.0
        if max_repos is None:
            max_repos = 10 if self.has_token else 5
        exclude = set(exclude or ())
        curated = {c["full_name"] for c in CURATED}
        found: dict[str, dict] = {}
        cursor = _load_cursor()
        qmap = cursor.setdefault("queries", {})
        for q in list(qmap):  # 兼容旧版整型游标格式
            if not isinstance(qmap[q], dict) or "kind" not in qmap[q]:
                qmap[q] = {"kind": "plain", "page": 1}
        if not qmap:
            # 首次建立游标:此前每查询第 1 页已基本处理完,从第 2 页续查
            for q in queries:
                qmap[q] = {"kind": "plain", "page": 2}
        for q in queries:
            if self.rate_limited or len(found) >= max_repos:
                break
            phase = qmap.get(q)
            if phase is None:
                phase = {"kind": "plain", "page": 1}
                qmap[q] = phase
            while len(found) < max_repos and not self.rate_limited:
                kind = phase.get("kind")
                if kind == "done":
                    break  # 该查询已下探到 0★,结束
                query = q if kind == "plain" else f"{q} {STAR_BUCKETS[kind]}"
                page = phase.get("page", 1)
                try:
                    r = self.session.get(
                        f"{API}/search/repositories",
                        params={"q": query, "sort": "stars", "per_page": per_page, "page": page}, timeout=20)
                except requests.RequestException:
                    phase["page"] = page
                    break
                if r.status_code == 403 and "rate" in r.text.lower():
                    self.rate_limited = True
                    phase["page"] = page
                    break
                if r.status_code != 200:
                    phase["page"] = page
                    break
                items = r.json().get("items", [])
                for it in items:
                    fn = it["full_name"]
                    if fn in found or fn in curated or fn in exclude:
                        continue
                    desc = it.get("description") or ""
                    if JUNK_RE.search(f"{fn} {desc}") or not POSITIVE_RE.search(f"{fn} {desc}"):
                        continue
                    found[fn] = {"full_name": fn, "branch": it.get("default_branch", "main"),
                                 "stars": it["stargazers_count"], "desc": desc, "kind": None}
                if len(items) < per_page:
                    # 本段翻到底 → 下探一档;已到 stars:0 档则标记完成
                    if kind == "plain":
                        phase["kind"] = 0
                    elif kind + 1 < len(STAR_BUCKETS):
                        phase["kind"] = kind + 1
                    else:
                        phase["kind"] = "done"
                    phase["page"] = 1
                    continue
                phase["page"] = page + 1
                time.sleep(gap)
        _save_cursor(cursor)
        return sorted(found.values(), key=lambda r: -r["stars"])[:max_repos]

    # ---------- 枚举文件树 ----------
    def _tree(self, full_name: str, branch: str) -> dict[str, int] | None:
        """返回 {path: size_bytes};失败或判定为需编译仓库时返回 None。"""
        r = self.session.get(f"{API}/repos/{full_name}/git/trees/{branch}", params={"recursive": "1"}, timeout=30)
        if r.status_code == 403 and "rate" in r.text.lower():
            self.rate_limited = True
            return None
        if r.status_code != 200:
            return None
        data = r.json()
        if data.get("truncated"):
            return None
        files: dict[str, int] = {}
        tops: set[str] = set()
        for t in data.get("tree", []):
            if t.get("type") != "blob":
                continue
            p = t.get("path", "")
            files[p] = t.get("size") or 0
            tops.add(p.split("/")[0].lower())
        if tops & BUILD_CONFIGS or "package.json" in tops:
            return None
        return files

    # ---------- 下载单个文件 ----------
    def _jsdelivr(self, full_name: str, branch: str | None, path: str) -> bytes | None:
        tag = f"{full_name}@{branch}" if branch else full_name  # 无分支则用仓库默认分支
        url = f"https://cdn.jsdelivr.net/gh/{tag}/{path}"
        try:
            r = requests.get(url, headers=UA, allow_redirects=False, timeout=30)
            if r.status_code == 200:
                return r.content
            if r.status_code in (301, 302, 307, 308):  # 跳转到 raw,本网络不可用
                return None
            return None
        except requests.RequestException:
            return None

    def _contents(self, full_name: str, path: str) -> bytes | None:
        """回退通道:contents API(base64,经 api.github.com,稳定)。"""
        r = self.session.get(f"{API}/repos/{full_name}/contents/{path}", timeout=30)
        if r.status_code == 403 and "rate" in r.text.lower():
            self.rate_limited = True
            return None
        if r.status_code != 200:
            return None
        j = r.json()
        if isinstance(j, dict) and j.get("encoding") == "base64":
            return base64.b64decode(j["content"])
        return None

    def _pick_cover_local(self, root: Path, rel: str) -> str:
        """下载完成后从本地文件找封面:优先游戏 HTML 里引用到的图片,
        其次扫描游戏目录内的图片;按(引用+关键词, 尺寸)排序,返回仓库相对路径。"""
        folder = root / Path(rel).parent
        candidates: list[tuple[str, int, int]] = []
        seen: set[str] = set()

        def consider(relpath: str, prio: int) -> None:
            rp = (folder / relpath.split("?", 1)[0]).resolve()
            scalar = rp.suffix.lower()
            if scalar not in IMG_EXT or rp.name.lower().startswith("favicon"):
                return
            try:
                r = rp.relative_to(root).as_posix()
            except ValueError:
                return
            if r in seen or not rp.is_file():
                return
            seen.add(r)
            candidates.append((r, prio, rp.stat().st_size))

        try:
            html = (root / rel).read_text("utf-8", errors="ignore")
            refs: list[str] = []
            for m in re.finditer(r'''(?:src|poster|data-src)\s*=\s*["']([^"']+)["']''', html, re.I):
                refs.append(m.group(1))
            for m in re.finditer(r'''url\(\s*["']?([^"')]+)["']?\)''', html, re.I):
                refs.append(m.group(1))
            for u in refs:
                if u.startswith(("http:", "https:", "data:", "//", "#")):
                    continue
                consider(u, 0 if COVER_KEYWORDS.search(u) else 1)
        except OSError:
            pass

        top = folder if folder != root else root
        for p in top.rglob("*"):
            if p.is_file() and p.suffix.lower() in IMG_EXT:
                consider(p.relative_to(top).as_posix(), 2 if COVER_KEYWORDS.search(p.name) else 3)

        if not candidates:
            return ""
        candidates.sort(key=lambda t: (t[1], 1 if t[2] < 2000 else 0, -t[2]))
        return candidates[0][0]

    # ---------- 条目规划 ----------

    def _plan_repo(self, repo: dict, files: dict[str, int]) -> list[dict]:
        """规划可玩条目:[{repo,title,rel(入口html),files(需下载的文件),cover}]。"""
        htmls = [p for p in files if p.lower().endswith(".html")]
        kind = repo.get("kind")
        _, name = repo["full_name"].split("/")

        if kind == "single":
            cand = ["index.html"] if "index.html" in htmls else (htmls[:1] if htmls else [])
            pairs = [(c, _beautify(name)) for c in cand]
        elif kind in ("numbered-folders", "category-folders"):
            pairs = [(p, _title_from_folder(p.rsplit("/", 1)[0])) for p in htmls if "/" in p]
        elif kind == "root-files":
            pairs = [(p, _beautify(p[:-5])) for p in sorted(p for p in htmls if "/" not in p)]
        else:  # 动态搜索仓库:根 index.html 最优,其次少量根单文件
            root = sorted(p for p in htmls if "/" not in p)
            if "index.html" in root:
                pairs = [("index.html", _beautify(name))]
            else:
                pairs = [(p, _beautify(p[:-5])) for p in root[:4]]

        cap = repo.get("_cap")
        if cap is not None:
            pairs = pairs[:cap]

        plans: list[dict] = []
        for rel, title in pairs:
            folder = rel.rsplit("/", 1)[0] if "/" in rel else ""
            if folder:
                # 目录型游戏:下载整个游戏目录(该目录内所有文件)
                need = [p for p in files if p.startswith(folder + "/")]
            else:
                # 单文件/根级游戏:下载根目录所有文件(含所需资源)
                need = [p for p in files if "/" not in p]
            need = [p for p in need if p != ".git" and files.get(p, 0) <= MAX_BYTES_PER_GAME]
            if not need or rel not in need:
                continue
            if folder and len(need) > MAX_FILES_PER_GAME:
                # 文件数上限仅约束“一游戏一目录”的仓库(避免拖入大资源目录)
                continue
            if sum(files.get(p, 0) for p in need) > MAX_BYTES_PER_GAME:
                continue
            plans.append({"repo": repo["full_name"], "title": title, "rel": rel,
                          "files": need, "cover": ""})
        return plans

    # ---------- 下载条目文件 ----------
    def _download_plan(self, full_name: str, branch: str, plan: dict, progress=None) -> bool:
        root = GAMES_DIR / _sanitize(full_name)
        root.mkdir(parents=True, exist_ok=True)
        files = plan["files"]

        def one(path: str) -> bool:
            dest = root / path
            if dest.exists() and dest.stat().st_size > 0:  # 已下载过,跳过
                return True
            data = self._jsdelivr(full_name, branch, path)
            if data is None:
                data = self._contents(full_name, path)
            if data is None or not data:
                return False
            dest.parent.mkdir(parents=True, exist_ok=True)
            try:
                dest.write_bytes(data)
                return True
            except OSError:
                return False

        ok = 0
        total = len(files)
        step = max(1, total // 10)
        with ThreadPoolExecutor(max_workers=8) as pool:
            futs = [pool.submit(one, p) for p in files]
            for i, fut in enumerate(as_completed(futs), 1):
                if fut.result():
                    ok += 1
                if progress and (i % step == 0 or i == total):
                    progress(f'  {plan["title"]}: {i}/{total} 个文件')
        if ok == total:
            self._download_extra(full_name, branch, plan)
            # 补齐下载后,入口 HTML 的本地引用若仍未就绪 → 判定不可玩
            missing, _ = _scan_local_refs(root, plan["rel"])
            if missing:
                return False
        return ok == total

    def _download_extra(self, full_name: str, branch: str, plan: dict, extra_cap: int = 40) -> None:
        """补齐下载:扫描已下载文件的相对引用,把缺失的本地资源也拉下来(有界)。
        让“一游戏一目录”仓库里引用 ../ 共享资源的情况也能开箱即玩。"""
        root = GAMES_DIR / _sanitize(full_name)
        queue: list[str] = list(plan["files"])
        downloaded: set[str] = set(plan["files"])
        extra = 0
        i = 0
        while i < len(queue) and extra < extra_cap:
            p = queue[i]
            i += 1
            lp = root / p
            if not lp.exists() or lp.stat().st_size == 0 or Path(p).suffix.lower() not in (".html", ".htm", ".css", ".js"):
                continue
            try:
                text = lp.read_text("utf-8", errors="ignore")
            except OSError:
                continue
            for ref in _iter_local_refs(text):
                if ref.startswith("/") or _REMOTE_REF.match(ref):
                    continue
                target = _resolve_ref(p, ref)
                if target is None or target in downloaded:
                    continue
                tp = root / target
                if tp.exists() and tp.stat().st_size > 0:
                    downloaded.add(target)
                    continue
                data = self._jsdelivr(full_name, branch, target)
                if data is None:
                    data = self._contents(full_name, target)
                if data is None or not data:
                    continue
                try:
                    tp.parent.mkdir(parents=True, exist_ok=True)
                    tp.write_bytes(data)
                    downloaded.add(target)
                    queue.append(target)
                    extra += 1
                except OSError:
                    continue

    # ---------- 主流程 ----------
    def collect(self, progress=None) -> tuple[list[dict], list[str]]:
        all_entries: list[dict] = []
        errors: list[str] = []
        skipped: set[str] = set()

        repos: list[dict] = [dict(c) for c in CURATED]
        for c in repos:
            c["_cap"] = CURATED_CAPS.get(c.get("kind"))
        if progress:
            progress("正在搜索 GitHub 游戏仓库…")
        repos += self._search_repos()

        total = len(repos)
        for i, repo in enumerate(repos, 1):
            fn, branch = repo["full_name"], repo.get("branch") or "main"
            if progress:
                progress(f"正在拉取仓库 {i}/{total}: {fn}")
            files = self._tree(fn, branch)
            if files is None:
                if not self.rate_limited:
                    errors.append(f"{fn}: 跳过(无法枚举或需编译)")
                    skipped.add(fn)
                continue
            plans = self._plan_repo(repo, files)
            if not plans:
                if not self.rate_limited:
                    errors.append(f"{fn}: 未发现免编译可玩游戏")
                    skipped.add(fn)
            for plan in plans:
                if progress:
                    progress(f'正在下载源码: {plan["title"]}')
                if not self._download_plan(fn, branch, plan, progress):
                    errors.append(f'{plan["title"]}: 部分文件失败,已跳过')
                    continue
                if not plan.get("cover"):
                    plan["cover"] = self._pick_cover_local(GAMES_DIR / _sanitize(fn), plan["rel"])
                all_entries.append({
                    "repo": fn, "title": plan["title"],
                    "rel": plan["rel"], "thumb_rel": plan["cover"] or "",
                })
                if len(all_entries) >= MAX_ENTRIES:
                    break
            if len(all_entries) >= MAX_ENTRIES:
                break
            if self.rate_limited:
                errors.append("GitHub API 限流(可在设置中配置 api_key 提升配额)")
                break

        if skipped:
            _save_skipped(_load_skipped() | skipped)
        return all_entries, errors

    # ---------- 增量拉取(初始化之后:仅 Search,按 stars 递减) ----------
    def collect_new(self, known_repos: set[str], progress=None,
                    repo_count: int | None = None) -> tuple[list[dict], list[str]]:
        """只通过 Search API 找 repo_count 个新仓库,按 stars 从高到低逐仓探测,
        把每个仓库中能直接游玩的游戏全部加入;所有已入库/已放弃的仓库绝不重复探测。
        返回 (新条目, 错误)。"""
        if repo_count is None:
            repo_count = 5
        repo_count = max(1, min(20, int(repo_count)))
        errors: list[str] = []
        new_entries: list[dict] = []
        skipped: set[str] = set()
        if progress:
            progress(f"正在通过 Search 寻找新仓库(候选 {repo_count} 个)…")
        exclude = set(known_repos) | _load_skipped()
        repos = self._search_repos(exclude=exclude, max_repos=repo_count)
        repos.sort(key=lambda r: -(r.get("stars") or 0))
        if not repos:
            errors.append("Search 暂无可拉取的新仓库(已全部入库、已翻页到底或未发现免编译仓库)")
            return new_entries, errors

        for i, repo in enumerate(repos, 1):
            fn, branch = repo["full_name"], repo.get("branch") or "main"
            stars = repo.get("stars") or 0
            if progress:
                progress(f"拉取新仓库 {i}/{len(repos)}: {fn} (★{stars})")
            files = self._tree(fn, branch)
            if files is None:
                if not self.rate_limited:
                    errors.append(f"{fn}: 跳过(无法枚举或需编译)")
                    skipped.add(fn)
                continue
            plans = self._plan_repo(repo, files)
            if not plans:
                if not self.rate_limited:
                    errors.append(f"{fn}: 未发现免编译可玩游戏")
                    skipped.add(fn)
                continue
            repo_ok = True
            for plan in plans:
                if progress:
                    progress(f'正在下载源码: {plan["title"]}')
                if not self._download_plan(fn, branch, plan, progress):
                    errors.append(f'{plan["title"]}: 部分文件失败,已跳过')
                    repo_ok = False
                    continue
                plan["cover"] = self._pick_cover_local(GAMES_DIR / _sanitize(fn), plan["rel"]) or ""
                new_entries.append({"repo": fn, "title": plan["title"],
                                    "rel": plan["rel"], "thumb_rel": plan["cover"]})
            if not repo_ok and not self.rate_limited:
                # 下载失败过的仓库也标记为已见过,保证后续绝不重复碰
                skipped.add(fn)
            if self.rate_limited:
                errors.append("GitHub API 限流(可在设置中配置 api_key 提升配额)")
                break

        if skipped:
            _save_skipped(_load_skipped() | skipped)
        return new_entries, errors


def _load_cache() -> list[dict]:
    try:
        with open(CACHE_FILE, encoding="utf-8") as f:
            d = json.load(f)
        if d.get("manifest") != MANIFEST_ID:
            return []
        valid = []
        for e in d.get("games", []):
            if (GAMES_DIR / _sanitize(e["repo"]) / e["rel"]).exists():
                valid.append(e)
        return valid
    except (OSError, json.JSONDecodeError):
        return []


def _save_cache(entries: list[dict]) -> None:
    CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = CACHE_FILE.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump({"manifest": MANIFEST_ID, "games": entries}, f, ensure_ascii=False, indent=2)
    os.replace(tmp, CACHE_FILE)


def _all_referenced_paths(root: Path, entry: dict) -> set[str]:
    """该游戏的入口 HTML 引用到的所有仓库相对路径(用于删除时的文件归集)。"""
    paths: set[str] = {entry["rel"]}
    if entry.get("thumb_rel"):
        paths.add(entry["thumb_rel"])
    try:
        text = (root / entry["rel"]).read_text("utf-8", errors="ignore")
    except OSError:
        return paths
    for ref in _iter_local_refs(text):
        target = _resolve_ref(entry["rel"], ref)
        if target is not None:
            paths.add(target)
    return paths


def _remove_entry_files(root: Path, removed: list[dict], remaining: list[dict]) -> None:
    """删除已移除条目的本地文件。同一仓库仍有别的游戏时,只删独占文件;
    整仓清空则直接删除整个目录。"""
    if not root.exists():
        return
    remain_rels = {e["rel"] for e in remaining}
    if not remaining:
        shutil.rmtree(root, ignore_errors=True)
        return
    # 仍保留的游戏用到的文件集合(粗略:入口引用 + 封面)
    keep_paths: set[str] = set()
    for e in remaining:
        keep_paths |= _all_referenced_paths(root, e)
    for e in removed:
        subfolder = e["rel"].rsplit("/", 1)[0] if "/" in e["rel"] else ""
        if subfolder:
            # “一游戏一目录”:该目录无剩余游戏时整目录删除
            folder_dir = root / subfolder
            if folder_dir.is_dir() and not any(r.startswith(subfolder + "/") for r in remain_rels):
                shutil.rmtree(folder_dir, ignore_errors=True)
                continue
        for p in _all_referenced_paths(root, e):
            if p in keep_paths:
                continue
            fp = root / p
            try:
                if fp.is_dir():
                    shutil.rmtree(fp, ignore_errors=True)
                elif fp.exists():
                    fp.unlink(missing_ok=True)
            except OSError:
                pass


def delete_local_games(items: list[dict]) -> tuple[list[dict], int]:
    """删除指定游戏(按 repo+rel 匹配),同步清理本地文件,返回 (新缓存, 删除数)。"""
    wanted = {(e["repo"], e["rel"]) for e in items if e.get("repo") and e.get("rel")}
    cache = _load_cache()
    removed: list[dict] = []
    remaining: list[dict] = []
    for e in cache:
        if (e["repo"], e["rel"]) in wanted:
            removed.append(e)
        else:
            remaining.append(e)
    if not removed:
        return cache, 0
    by_repo: dict[str, tuple[list, list]] = {}
    for e in remaining:
        by_repo.setdefault(e["repo"], ([], []))[0].append(e)
    for e in removed:
        by_repo.setdefault(e["repo"], ([], []))[1].append(e)
    for repo, (rm, rem) in by_repo.items():
        _remove_entry_files(GAMES_DIR / _sanitize(repo), rem, rm)
    _save_cache(remaining)
    return remaining, len(removed)


def verify_local_games() -> dict:
    """本地可玩性巡检:剔除引用缺失(无法开箱即玩)的游戏,统计远程依赖;限本地扫描、不快照网络。"""
    cache = _load_cache()
    removed: list[dict] = []
    remaining: list[dict] = []
    network_dependent: list[str] = []
    for e in cache:
        root = GAMES_DIR / _sanitize(e["repo"])
        missing, remote = _scan_local_refs(root, e["rel"])
        if missing:
            removed.append(e)
            continue
        if remote:
            network_dependent.append(e["title"])
        remaining.append(e)
    if removed:
        by_repo: dict[str, tuple[list, list]] = {}
        for e in remaining:
            by_repo.setdefault(e["repo"], ([], []))[0].append(e)
        for e in removed:
            by_repo.setdefault(e["repo"], ([], []))[1].append(e)
        for repo, (rm, rem) in by_repo.items():
            _remove_entry_files(GAMES_DIR / _sanitize(repo), rem, rm)
        _save_cache(remaining)
    return {
        "checked": len(cache),
        "removed": [{"title": e["title"], "repo": e["repo"]} for e in removed],
        "network": sorted(set(network_dependent)),
    }


def repair_local_games(progress=None) -> dict:
    """全网修复:对本地引用缺失的游戏,尝试从 GitHub 补齐资源(jsdelivr→contents),
    补齐后仍缺失的才剔除。返回 {checked, fixed, removed}。"""
    cache = _load_cache()
    src = GitHubSource(None)
    fixed: list[str] = []
    removed: list[dict] = []
    for idx, e in enumerate(cache, 1):
        root = GAMES_DIR / _sanitize(e["repo"])
        missing, _ = _scan_local_refs(root, e["rel"])
        if not missing:
            continue
        if progress:
            progress(f"修复 [{idx}/{len(cache)}] {e['title']}")
        src._download_extra(e["repo"], None, {"rel": e["rel"], "files": [e["rel"]]}, extra_cap=150)
        missing, _ = _scan_local_refs(root, e["rel"])
        if missing:
            if not src.rate_limited:
                removed.append(e)
        else:
            fixed.append(e["title"])
        if src.rate_limited:
            pass  # 保留待下一轮重试,不剔除
    removed_keys = {(r["repo"], r["rel"]) for r in removed}
    remaining = [e for e in cache if (e["repo"], e["rel"]) not in removed_keys]
    if removed:
        by_repo: dict[str, tuple[list, list]] = {}
        for e in remaining:
            by_repo.setdefault(e["repo"], ([], []))[0].append(e)
        for r in removed:
            by_repo.setdefault(r["repo"], ([], []))[1].append(r)
        for repo, (rm, rem) in by_repo.items():
            _remove_entry_files(GAMES_DIR / _sanitize(repo), rem, rm)
        _save_cache(remaining)
    return {
        "checked": len(cache),
        "fixed": fixed,
        "removed": [{"title": r["title"], "repo": r["repo"]} for r in removed],
    }


def _load_skipped() -> set[str]:
    """已评估过、因“需编译/无免编译游戏”被放弃的仓库(full_name),增量拉取时不再重复探测。"""
    try:
        with open(SKIPPED_FILE, encoding="utf-8") as f:
            d = json.load(f)
        if isinstance(d, list):
            return {x for x in d if isinstance(x, str)}
    except (OSError, json.JSONDecodeError):
        pass
    return set()


def _save_skipped(skipped: set[str]) -> None:
    SKIPPED_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = SKIPPED_FILE.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(sorted(skipped), f, ensure_ascii=False, indent=2)
    os.replace(tmp, SKIPPED_FILE)


def _load_cursor() -> dict:
    """每条 Search 查询的翻页游标:{"queries": {query: 下轮起始页| -1表示已到底}}。"""
    try:
        with open(CURSOR_FILE, encoding="utf-8") as f:
            d = json.load(f)
        if isinstance(d, dict) and isinstance(d.get("queries"), dict):
            return d
    except (OSError, json.JSONDecodeError):
        pass
    return {"queries": {}}


def _save_cursor(cursor: dict) -> None:
    CURSOR_FILE.parent.mkdir(parents=True, exist_ok=True)
    tmp = CURSOR_FILE.with_suffix(".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(cursor, f, ensure_ascii=False, indent=2)
    os.replace(tmp, CURSOR_FILE)


def _entry_to_ui(e: dict) -> dict:
    disk = _sanitize(e["repo"])
    return {
        "title": e["title"],
        "thumb": local_server.url(disk, e["thumb_rel"]) if e.get("thumb_rel") else "",
        "url": local_server.url(disk, e["rel"]),
        "repo": e["repo"],
        "rel": e["rel"],
    }


def get_github_games(token: str | None = None, refresh: bool = False, progress=None,
                     repo_count: int | None = None) -> dict:
    cached = _load_cache()
    if not refresh and cached:
        return {"ok": True, "games": [_entry_to_ui(e) for e in cached], "source": "local"}
    try:
        src = GitHubSource(token)
        if refresh and cached:
            # 已有本地库:增量拉取 —— 探测 repo_count 个新仓库,
            # 把其中能直接游玩的游戏全部加入;见过的仓库绝不重复碰到
            known = {e["repo"] for e in cached}
            new_entries, errors = src.collect_new(known, progress, repo_count=repo_count)
            if new_entries:
                _save_cache(cached + new_entries)
            error = "; ".join(errors[:4]) if errors else ""
            return {
                "ok": bool(cached or new_entries),
                "games": [_entry_to_ui(e) for e in cached] + [_entry_to_ui(e) for e in new_entries],
                "source": "online",
                "added": len(new_entries),
                "error": error,
            }
        # 首次/初始化:精选合集 + Search,全量建立本地库
        entries, errors = src.collect(progress)
        if entries:
            _save_cache(entries)
        error = "; ".join(errors[:4]) if errors else ""
        prev = {(e["repo"], e["rel"]) for e in cached}
        return {
            "ok": bool(entries),
            "games": [_entry_to_ui(e) for e in entries],
            "source": "online",
            "added": sum(1 for e in entries if (e["repo"], e["rel"]) not in prev),
            "error": error,
        }
    except Exception as exc:
        return {
            "ok": bool(cached),
            "games": [_entry_to_ui(e) for e in cached],
            "source": "local",
            "error": f"{type(exc).__name__}: {exc}",
        }


if __name__ == "__main__":
    port = local_server.start(GAMES_DIR)
    print("local server on:", port)
    token = os.environ.get("GITHUB_TOKEN")
    result = get_github_games(token, refresh=True, progress=lambda m: print("  ", m, flush=True))
    print("ok:", result["ok"], "| count:", len(result["games"]))
    if result.get("error"):
        print("error:", result["error"])
    for g in result["games"][:15]:
        print(" -", g["title"], "|", (g["thumb"] or "")[:50], "|", g["url"])