#!/bin/bash
set -euo pipefail

project_json=$(curl --fail --silent --show-error --location \
  'https://api.modrinth.com/v2/project/findmyitems')
downloads=$(printf '%s' "$project_json" | ruby -rjson -e 'puts JSON.parse(STDIN.read).fetch("downloads")')

ruby - "$downloads" <<'RUBY'
path = 'README.md'
downloads = ARGV.fetch(0)
readme = File.read(path)
updated = readme.sub(/^findmyitems downloads: \d+$/, "findmyitems downloads: #{downloads}")
abort 'download marker not found' if updated == readme && !readme.match?(/^findmyitems downloads: \d+$/)
File.write(path, updated) unless updated == readme
RUBY
