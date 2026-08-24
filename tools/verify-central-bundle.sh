#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 [--require-signatures] BUNDLE.zip" >&2
  exit 2
}

require_signatures=0
if [[ "${1:-}" == "--require-signatures" ]]; then
  require_signatures=1
  shift
fi
[[ $# -eq 1 ]] || usage
bundle=$1
[[ -f "$bundle" ]] || { echo "bundle does not exist: $bundle" >&2; exit 1; }

scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
unzip -q "$bundle" -d "$scratch"

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
manifest="$repo_root/project/release-artifacts.tsv"
[[ -f "$manifest" ]] || { echo "missing release manifest: $manifest" >&2; exit 1; }

artifacts=()
while IFS=$'\t' read -r project coordinate platform status; do
  [[ "$project" == "project" ]] && continue
  [[ -n "$project" ]] || continue
  [[ "$status" == "admitted" ]] || {
    echo "non-admitted entry in release artifact manifest: $project ($status)" >&2
    exit 1
  }
  [[ "${coordinate%%:*}" == "io.github.canardlapin" ]] || {
    echo "unexpected Maven group in release artifact manifest: $coordinate" >&2
    exit 1
  }
  artifact=${coordinate#*:}
  [[ -n "$artifact" && "$artifact" != "$coordinate" ]] || {
    echo "malformed Maven coordinate in release artifact manifest: $coordinate" >&2
    exit 1
  }
  artifacts+=("$artifact")
done < "$manifest"

[[ "${#artifacts[@]}" -eq 6 ]] || {
  echo "release artifact manifest must admit exactly six coordinates" >&2
  exit 1
}

group_root="$scratch/io/github/canardlapin"
[[ -d "$group_root" ]] || {
  echo "missing io.github.canardlapin Maven group in Central bundle" >&2
  exit 1
}

unexpected_poms=$(find "$scratch" -type f -name '*.pom' ! -path "$group_root/*" -print)
[[ -z "$unexpected_poms" ]] || {
  echo "Central bundle contains coordinates outside io.github.canardlapin" >&2
  printf '%s\n' "$unexpected_poms" >&2
  exit 1
}

expected_artifacts=$(printf '%s\n' "${artifacts[@]}" | sort)
actual_artifacts=$(
  find "$group_root" -type f -name '*.pom' -exec sh -c '
    for pom do
      basename "$(dirname "$(dirname "$pom")")"
    done
  ' sh {} + | sort -u
)
[[ "$actual_artifacts" == "$expected_artifacts" ]] || {
  echo "Central bundle artifact set differs from project/release-artifacts.tsv" >&2
  diff -u <(printf '%s\n' "$expected_artifacts") <(printf '%s\n' "$actual_artifacts") >&2 || true
  exit 1
}

verify_checksum() {
  local file=$1
  local algorithm=$2
  local checksum="$file.$algorithm"
  local expected
  local actual

  [[ -f "$checksum" ]] || {
    echo "missing $algorithm checksum for $file" >&2
    exit 1
  }
  expected=$(tr -d '[:space:]' < "$checksum")
  case "$algorithm" in
    md5)
      if command -v md5sum >/dev/null 2>&1; then
        actual=$(md5sum "$file" | awk '{print $1}')
      else
        actual=$(md5 -q "$file")
      fi
      ;;
    sha1)
      if command -v sha1sum >/dev/null 2>&1; then
        actual=$(sha1sum "$file" | awk '{print $1}')
      else
        actual=$(shasum -a 1 "$file" | awk '{print $1}')
      fi
      ;;
    *)
      echo "unsupported checksum algorithm: $algorithm" >&2
      exit 1
      ;;
  esac
  [[ "$expected" == "$actual" ]] || {
    echo "$algorithm checksum mismatch for $file" >&2
    exit 1
  }
}

verify_pom_metadata() {
  local pom=$1
  local artifact=$2
  local version=$3
  python3 - "$pom" "$artifact" "$version" <<'PY'
import sys
import xml.etree.ElementTree as ET

pom, expected_artifact, expected_version = sys.argv[1:]
root = ET.parse(pom).getroot()
for element in root.iter():
    if "}" in element.tag:
        element.tag = element.tag.split("}", 1)[1]

def required(path, expected=None):
    element = root.find(path)
    value = "" if element is None or element.text is None else element.text.strip()
    if not value:
        raise SystemExit(f"{pom}: missing POM metadata {path}")
    if expected is not None and value != expected:
        raise SystemExit(
            f"{pom}: expected {path}={expected!r}, found {value!r}"
        )
    return value

required("groupId", "io.github.canardlapin")
required("artifactId", expected_artifact)
required("version", expected_version)
required("name")
required("description")
required("url", "https://github.com/canardlapin/locus4s")
required("licenses/license/name")
required("licenses/license/url")
required("developers/developer/id", "canardlapin")
required("developers/developer/name")
required("developers/developer/email")
required("developers/developer/url")
required("scm/url", "https://github.com/canardlapin/locus4s")
required("scm/connection")
required("scm/developerConnection")

if root.find("repositories") is not None:
    raise SystemExit(f"{pom}: release POM must not publish repository declarations")
for dependency in root.findall("dependencies/dependency"):
    dep_version = dependency.findtext("version", default="").strip()
    if "SNAPSHOT" in dep_version.upper():
        raise SystemExit(f"{pom}: snapshot dependency {dep_version}")
    if dependency.find("systemPath") is not None:
        raise SystemExit(f"{pom}: filesystem systemPath dependency")
PY
}

bundle_version=""
for artifact in "${artifacts[@]}"; do
  poms=$(find "$scratch" -type f -path "*/$artifact/*/$artifact-*.pom" | sort)
  pom_count=$(printf '%s\n' "$poms" | sed '/^$/d' | wc -l | tr -d ' ')
  [[ "$pom_count" -eq 1 ]] || {
    echo "expected exactly one POM for $artifact, found $pom_count" >&2
    exit 1
  }

  pom=$(printf '%s\n' "$poms" | sed -n '1p')
  version=$(basename "$(dirname "$pom")")
  [[ "$version" != *SNAPSHOT* ]] || {
    echo "snapshot version in Central bundle: $artifact:$version" >&2
    exit 1
  }
  if [[ -z "$bundle_version" ]]; then
    bundle_version=$version
  elif [[ "$version" != "$bundle_version" ]]; then
    echo "mixed versions in Central bundle: expected $bundle_version, found $artifact:$version" >&2
    exit 1
  fi

  verify_pom_metadata "$pom" "$artifact" "$version"

  directory=$(dirname "$pom")
  for suffix in pom jar sources.jar javadoc.jar; do
    file="$directory/$artifact-$version"
    case "$suffix" in
      pom) file+=".pom" ;;
      jar) file+=".jar" ;;
      sources.jar) file+="-sources.jar" ;;
      javadoc.jar) file+="-javadoc.jar" ;;
    esac
    [[ -f "$file" ]] || { echo "missing $suffix for $artifact:$version" >&2; exit 1; }
    verify_checksum "$file" md5
    verify_checksum "$file" sha1
    if (( require_signatures )); then
      [[ -f "$file.asc" ]] || { echo "missing signature for $file" >&2; exit 1; }
      verify_checksum "$file.asc" md5
      verify_checksum "$file.asc" sha1
    fi
  done
done

echo "Central bundle verified: ${#artifacts[@]} admitted artifacts, version $bundle_version"
