#!/usr/bin/env python3
"""Draft the release notes skeleton for the next tag.

The release notes are not written from memory: they are the merged pull
requests since the last tag, rewritten as user-visible sentences. This script
collects that list and prints a skeleton in the format
`docs/releases/vX.Y.Z.md` expects, in both English and Chinese.

Usage:

    python3 scripts/draft_release_notes.py            # skeleton for the next version
    python3 scripts/draft_release_notes.py --since v0.3.5
    python3 scripts/draft_release_notes.py --list      # only the merged PRs

It needs nothing but git; `gh` is not required.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"

# Squash merges put the pull request title in the subject line:
#   "Count paused lines through the filter, add a scroll rail (#124)"
PR_SUBJECT = re.compile(r"^(?P<title>.+?)\s+\(#(?P<pr>\d+)\)\s*$")

VERSION_NAME = re.compile(r'^\s*versionName\s*=\s*"([^"]+)"', re.MULTILINE)
VERSION_CODE = re.compile(r"^\s*versionCode\s*=\s*(\d+)", re.MULTILINE)

SECTION_SKELETON = (
    ("Added", "## Added", "## 新增"),
    ("Improved", "## Improved", "## 改进"),
    ("Fixed", "## Fixed", "## 修复"),
)


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout


def latest_tag() -> str | None:
    tags = git("tag", "--sort=-v:refname", "--list", "v*").split()
    return tags[0] if tags else None


def version_pair() -> tuple[str, int]:
    text = GRADLE.read_text(encoding="utf-8")
    name = VERSION_NAME.search(text)
    code = VERSION_CODE.search(text)
    if not name or not code:
        sys.exit("Could not read versionName/versionCode from app/build.gradle.kts")
    return name.group(1), int(code.group(1))


def merged_pulls(since: str | None) -> list[tuple[int, str]]:
    """Squash-merged PRs since the given tag, oldest first."""
    rev = f"{since}..HEAD" if since else "HEAD"
    out: list[tuple[int, str]] = []
    for line in git("log", rev, "--first-parent", "--pretty=format:%s").splitlines():
        match = PR_SUBJECT.match(line.strip())
        if not match:
            continue
        out.append((int(match.group("pr")), match.group("title").strip()))
    out.reverse()
    return out


def render(version: str, code: int, pulls: list[tuple[int, str]]) -> str:
    lines = [
        f"**LogSleuth {version} — one line on what changed, in English.**",
        "",
        f"`versionName` {version}, `versionCode` {code}.",
        "",
        "Requires Android 8.0 (API 26)+.",
        "",
    ]
    for _, en, zh in SECTION_SKELETON:
        lines += [en, "", "- **What the user gets** — the behaviour, not the diff. (#0)", ""]
    lines += [
        "<!-- zh-CN -->",
        "",
        f"**LogSleuth {version} —— 中文一句话说明这一版改了什么。**",
        "",
        f"`versionName` {version}，`versionCode` {code}。",
        "",
        "需要 Android 8.0（API 26）及以上。",
        "",
    ]
    for _, _, zh in SECTION_SKELETON:
        lines += [zh, "", "- **用户能拿到什么** —— 写行为，不写 diff。(#0)", ""]

    if pulls:
        lines += ["<!--", "", "Merged pull requests since the last tag:", ""]
        for pr, title in pulls:
            lines.append(f"- #{pr} {title}")
        lines.append("-->")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--since", help="tag to compare against (default: the newest tag)")
    parser.add_argument("--list", action="store_true", help="only print the merged PRs")
    args = parser.parse_args()

    version, code = version_pair()
    since = args.since if args.since is not None else latest_tag()
    pulls = merged_pulls(since)

    if args.list:
        for pr, title in pulls:
            print(f"#{pr} {title}")
        return

    print(render(version, code, pulls))
    print(
        f"# Draft for {version} (versionCode {code})"
        + (f", merged PRs since {since}" if since else ", all merged PRs")
        + ".",
        file=sys.stderr,
    )
    print(
        f"# Save as docs/releases/v{version}.md and commit it in the version-bump PR.",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
