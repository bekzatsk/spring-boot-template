#!/bin/bash
# Publish auth-spring-boot-starter to GitHub Packages.
#
# Usage:
#   ./scripts/publish.sh                    # deploy current pom.xml version
#   ./scripts/publish.sh --skip-tests       # skip tests during deploy
#   ./scripts/publish.sh --release X.Y.Z    # bump pom to X.Y.Z (no -SNAPSHOT), deploy, tag, push
#   ./scripts/publish.sh --snapshot X.Y.Z   # bump pom to X.Y.Z-SNAPSHOT, deploy
#
# Auth:
#   Reads GitHub PAT from $GITHUB_TOKEN (preferred) or from ~/.m2/settings.xml server id=github.
#   PAT needs write:packages (and read:packages) scopes.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

SKIP_TESTS=false
RELEASE_VERSION=""
SNAPSHOT_VERSION=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --release)
      RELEASE_VERSION="${2:-}"
      if [ -z "$RELEASE_VERSION" ]; then
        echo "error: --release requires a version argument (e.g. 0.0.2)" >&2
        exit 1
      fi
      shift 2
      ;;
    --snapshot)
      SNAPSHOT_VERSION="${2:-}"
      if [ -z "$SNAPSHOT_VERSION" ]; then
        echo "error: --snapshot requires a version argument (e.g. 0.0.3)" >&2
        exit 1
      fi
      shift 2
      ;;
    -h|--help)
      sed -n '2,15p' "$0"
      exit 0
      ;;
    *)
      echo "error: unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

if [ -n "$RELEASE_VERSION" ] && [ -n "$SNAPSHOT_VERSION" ]; then
  echo "error: pass only one of --release or --snapshot" >&2
  exit 1
fi

# Resolve PAT: env var wins; otherwise rely on ~/.m2/settings.xml
MVN_AUTH_ARGS=()
if [ -n "${GITHUB_TOKEN:-}" ]; then
  GITHUB_USER="${GITHUB_USER:-bekzatsk}"
  MVN_AUTH_ARGS=(
    "-Dgithub.username=$GITHUB_USER"
    "-Dgithub.token=$GITHUB_TOKEN"
    "--settings" "$SCRIPT_DIR/maven-settings-github.xml"
  )
  # Write a local settings file that pulls credentials from system properties.
  cat >"$SCRIPT_DIR/maven-settings-github.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>github</id>
      <username>${github.username}</username>
      <password>${github.token}</password>
    </server>
  </servers>
</settings>
XML
  trap 'rm -f "$SCRIPT_DIR/maven-settings-github.xml"' EXIT
else
  if ! grep -q "<id>github</id>" "$HOME/.m2/settings.xml" 2>/dev/null; then
    echo "error: no GITHUB_TOKEN env var set and no <server><id>github</id> in ~/.m2/settings.xml" >&2
    echo "       export GITHUB_TOKEN=ghp_... (PAT with write:packages) and re-run, or edit ~/.m2/settings.xml" >&2
    exit 1
  fi
  echo "Using credentials from ~/.m2/settings.xml (server id=github)"
fi

# Bump version if requested
if [ -n "$RELEASE_VERSION" ]; then
  echo ">> bumping pom version to $RELEASE_VERSION"
  ./mvnw -q versions:set -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
elif [ -n "$SNAPSHOT_VERSION" ]; then
  echo ">> bumping pom version to ${SNAPSHOT_VERSION}-SNAPSHOT"
  ./mvnw -q versions:set -DnewVersion="${SNAPSHOT_VERSION}-SNAPSHOT" -DgenerateBackupPoms=false
fi

CURRENT_VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)
echo ">> publishing kz.innlab:auth-spring-boot-starter:$CURRENT_VERSION"

DEPLOY_ARGS=()
if [ "$SKIP_TESTS" = true ]; then
  DEPLOY_ARGS+=("-DskipTests")
fi

./mvnw clean deploy "${DEPLOY_ARGS[@]}" "${MVN_AUTH_ARGS[@]}"

# Tag + push for releases (non-SNAPSHOT)
if [ -n "$RELEASE_VERSION" ]; then
  TAG="v$RELEASE_VERSION"
  if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo ">> tag $TAG already exists, skipping git tag"
  else
    echo ">> tagging $TAG"
    git add pom.xml
    git commit -m "release: $RELEASE_VERSION" || true
    git tag -a "$TAG" -m "Release $RELEASE_VERSION"
    git push origin HEAD
    git push origin "$TAG"
  fi
fi

echo ">> done. Package: https://github.com/bekzatsk/spring-boot-template/packages"
