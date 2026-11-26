#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || "$1" != *.tgz ]]; then
  printf 'Usage: %s OUTPUT.tgz\n' "$0" >&2
  exit 2
fi

output="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
repo="$(cd "$(dirname "$0")/.." && pwd)"
staging="$(mktemp -d "${TMPDIR:-/tmp}/logsleuth-mcp-package.XXXXXX")"
trap 'rm -rf -- "$staging"' EXIT

cd "$repo/mcp"
npm ci --ignore-scripts
npm run build
npm test
npm pack --ignore-scripts --pack-destination "$staging"
packages=("$staging"/logsleuth-mcp-*.tgz)
[[ ${#packages[@]} -eq 1 && -f "${packages[0]}" ]]

npm install --global --offline --ignore-scripts --prefix "$staging/install" \
  --cache "$staging/cache" "${packages[0]}"
directory="$(XDG_CONFIG_HOME="$staging/config" "$staging/install/bin/logsleuth-mcp" files)"
[[ "$directory" == "$staging/config/logsleuth-mcp/files" && -d "$directory" ]]
cp "${packages[0]}" "$output"
printf '%s\n' "$output"
