#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "usage: $0 VERSION | --tag-derived" >&2
  exit 2
}

[[ $# -eq 1 ]] || usage
mode=$1
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
cd "$repo_root"

if [[ "$mode" == "--tag-derived" ]]; then
  sbt -batch -no-colors clean releaseSourceCourt releaseUnsigned
  expected_version=""
else
  [[ "$mode" =~ ^0\.1\.[0-9]+(-((M|RC)[1-9][0-9]*))?$ ]] || {
    echo "release court requires a 0.1 release, milestone, or RC version; got $mode" >&2
    exit 1
  }
  expected_version=$mode
  sbt -batch -no-colors clean \
    "set ThisBuild / version := \"$expected_version\"" \
    releaseSourceCourt releaseUnsigned
fi

staging=target/sona-staging
[[ -d "$staging" ]] || { echo "release court did not create $staging" >&2; exit 1; }

version_root="$staging/io/github/canardlapin/locus4s-core_3"
[[ -d "$version_root" ]] || {
  echo "release court did not stage locus4s-core_3" >&2
  exit 1
}
versions=()
while IFS= read -r version; do
  versions+=("$version")
done < <(find "$version_root" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)
[[ "${#versions[@]}" -eq 1 ]] || {
  echo "release court expected one staged version, found ${#versions[@]}" >&2
  exit 1
}
staged_version=${versions[0]}
if [[ -n "$expected_version" && "$staged_version" != "$expected_version" ]]; then
  echo "release court staged $staged_version, expected $expected_version" >&2
  exit 1
fi

bundle_tree=$(mktemp -d)
trap 'rm -rf "$bundle_tree"' EXIT
cp -R "$staging"/. "$bundle_tree"/
(cd "$bundle_tree" && zip -X -q -r "$repo_root/target/central-bundle.zip" .)

tools/verify-central-bundle.sh target/central-bundle.zip
tools/verify-release-consumer.sh "$staged_version" "$staging"
echo "Release court passed for all six locus4s artifacts at $staged_version"
