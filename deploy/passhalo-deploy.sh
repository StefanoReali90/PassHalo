#!/usr/bin/env bash
set -Eeuo pipefail

release_id="${1:-}"
if [[ ! "$release_id" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'Expected a 40-character Git commit SHA.' >&2
  exit 2
fi

base=/opt/passhalo
archive="$base/incoming/$release_id.tar.gz"
release="$base/releases/$release_id"
current="$base/current"

if [[ ! -f "$archive" ]]; then
  echo "Release archive is missing: $archive" >&2
  exit 1
fi

if [[ ! -d "$release" ]]; then
  install -d -m 755 "$release"
  tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$release"
fi

if [[ ! -f "$release/backend.jar" || ! -f "$release/web/index.html" ]]; then
  echo 'The release is missing the backend JAR or web build.' >&2
  exit 1
fi

previous=''
if [[ -L "$current" ]]; then
  previous="$(readlink -f "$current")"
fi

activate() {
  local target="$1"
  ln -sfn "$target" "$base/current.next"
  mv -Tf "$base/current.next" "$current"
}

rollback() {
  if [[ -n "$previous" && -d "$previous" ]]; then
    activate "$previous"
    systemctl restart passhalo || true
    echo "Rolled back to $previous" >&2
  else
    rm -f "$current"
    echo 'No previous release was available for rollback.' >&2
  fi
}

activate "$release"
if ! systemctl restart passhalo; then
  rollback
  exit 1
fi

healthy=false
for _ in {1..40}; do
  if curl --fail --silent --show-error --max-time 2 http://127.0.0.1:8080/events > /dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 2
done

if [[ "$healthy" != true ]]; then
  rollback
  echo 'New release failed its backend health check.' >&2
  exit 1
fi

rm -f "$archive"
echo "PassHalo release $release_id is active."
