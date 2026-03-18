#!/bin/bash
set -euo pipefail

# =============================================================================
# Spring Boot Project Renamer
# =============================================================================
# Usage: ./rename-project.sh <new_package> <new_project_name>
# Example: ./rename-project.sh com.innlab.cakeup cakeup
#
# This will:
#   - Move source directories to match new package
#   - Update all package/import declarations in Kotlin files
#   - Rename TemplateApplication -> <ProjectName>Application
#   - Update pom.xml (groupId, artifactId, name)
#   - Update application.yaml, docker-compose.yml, .env files
#   - Update .idea config files
# =============================================================================

OLD_PACKAGE="kz.innlab.template"
OLD_GROUP="kz.innlab"
OLD_ARTIFACT="template"
OLD_APP_CLASS="TemplateApplication"

# --- Validate args ---
if [ $# -ne 2 ]; then
    echo "Usage: $0 <new_package> <new_project_name>"
    echo "Example: $0 com.innlab.cakeup cakeup"
    exit 1
fi

NEW_PACKAGE="$1"
NEW_PROJECT="$2"

# Derive values from new package
# e.g. com.innlab.cakeup -> group=com.innlab, last_segment=cakeup
NEW_GROUP="${NEW_PACKAGE%.*}"
NEW_ARTIFACT="$NEW_PROJECT"

# PascalCase for Application class: cakeup -> Cakeup, cake-up -> CakeUp
NEW_APP_CLASS=""
IFS='-' read -ra PARTS <<< "$NEW_PROJECT"
for part in "${PARTS[@]}"; do
    NEW_APP_CLASS+="$(echo "${part:0:1}" | tr '[:lower:]' '[:upper:]')${part:1}"
done
NEW_APP_CLASS+="Application"

# Directory paths
OLD_PATH="${OLD_PACKAGE//./\/}"
NEW_PATH="${NEW_PACKAGE//./\/}"

echo "============================================="
echo "  Spring Boot Project Renamer"
echo "============================================="
echo ""
echo "  Package:     $OLD_PACKAGE -> $NEW_PACKAGE"
echo "  Group:       $OLD_GROUP -> $NEW_GROUP"
echo "  Artifact:    $OLD_ARTIFACT -> $NEW_ARTIFACT"
echo "  App Class:   $OLD_APP_CLASS -> $NEW_APP_CLASS"
echo "  Directories: $OLD_PATH -> $NEW_PATH"
echo ""
echo "============================================="
echo ""

read -p "Continue? (y/n): " CONFIRM
if [ "$CONFIRM" != "y" ]; then
    echo "Aborted."
    exit 0
fi

echo ""

# --- Helper ---
replace_in_file() {
    local file="$1"
    local from="$2"
    local to="$3"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|${from}|${to}|g" "$file"
    else
        sed -i "s|${from}|${to}|g" "$file"
    fi
}

# =============================================================================
# 1. Move source directories
# =============================================================================
echo "[1/6] Moving source directories..."

for base in src/main/kotlin src/test/kotlin; do
    if [ -d "$base/$OLD_PATH" ]; then
        mkdir -p "$base/$NEW_PATH"
        # Copy contents (not the directory itself)
        cp -R "$base/$OLD_PATH/"* "$base/$NEW_PATH/" 2>/dev/null || true
        # Remove old directory tree from the root of old package
        OLD_ROOT="${OLD_PACKAGE%%.*}"
        rm -rf "$base/$OLD_ROOT"
        echo "  Moved $base/$OLD_PATH -> $base/$NEW_PATH"
    fi
done

# =============================================================================
# 2. Update package/import declarations in all Kotlin files
# =============================================================================
echo "[2/6] Updating Kotlin package and import declarations..."

KOTLIN_COUNT=0
while IFS= read -r -d '' file; do
    replace_in_file "$file" "$OLD_PACKAGE" "$NEW_PACKAGE"
    replace_in_file "$file" "$OLD_APP_CLASS" "$NEW_APP_CLASS"
    KOTLIN_COUNT=$((KOTLIN_COUNT + 1))
done < <(find src -name "*.kt" -print0 2>/dev/null)

echo "  Updated $KOTLIN_COUNT Kotlin files"

# Rename TemplateApplication.kt -> <NewName>Application.kt
for base in src/main/kotlin src/test/kotlin; do
    OLD_FILE="$base/$NEW_PATH/${OLD_APP_CLASS}.kt"
    NEW_FILE="$base/$NEW_PATH/${NEW_APP_CLASS}.kt"
    if [ -f "$OLD_FILE" ]; then
        mv "$OLD_FILE" "$NEW_FILE"
        echo "  Renamed $OLD_FILE -> $NEW_FILE"
    fi
    # Also handle test class
    OLD_TEST="$base/$NEW_PATH/${OLD_APP_CLASS}Tests.kt"
    NEW_TEST="$base/$NEW_PATH/${NEW_APP_CLASS}Tests.kt"
    if [ -f "$OLD_TEST" ]; then
        mv "$OLD_TEST" "$NEW_TEST"
        echo "  Renamed $OLD_TEST -> $NEW_TEST"
    fi
done

# =============================================================================
# 3. Update pom.xml
# =============================================================================
echo "[3/6] Updating pom.xml..."

if [ -f pom.xml ]; then
    replace_in_file pom.xml "<groupId>${OLD_GROUP}</groupId>" "<groupId>${NEW_GROUP}</groupId>"
    replace_in_file pom.xml "<artifactId>${OLD_ARTIFACT}</artifactId>" "<artifactId>${NEW_ARTIFACT}</artifactId>"
    replace_in_file pom.xml "<name>${OLD_ARTIFACT}</name>" "<name>${NEW_ARTIFACT}</name>"
    echo "  Updated pom.xml"
fi

# =============================================================================
# 4. Update application configs
# =============================================================================
echo "[4/6] Updating application configs..."

# application.yaml (main)
MAIN_YAML="src/main/resources/application.yaml"
if [ -f "$MAIN_YAML" ]; then
    replace_in_file "$MAIN_YAML" "name: ${OLD_ARTIFACT}" "name: ${NEW_ARTIFACT}"
    replace_in_file "$MAIN_YAML" "DB_NAME:${OLD_ARTIFACT}" "DB_NAME:${NEW_ARTIFACT}"
    echo "  Updated $MAIN_YAML"
fi

# .env
if [ -f .env ]; then
    replace_in_file .env "DB_NAME=${OLD_ARTIFACT}" "DB_NAME=${NEW_ARTIFACT}"
    echo "  Updated .env"
fi

# .env.example
if [ -f .env.example ]; then
    replace_in_file .env.example "DB_NAME=${OLD_ARTIFACT}" "DB_NAME=${NEW_ARTIFACT}"
    echo "  Updated .env.example"
fi

# =============================================================================
# 5. Update docker-compose.yml
# =============================================================================
echo "[5/6] Updating docker-compose.yml..."

if [ -f docker-compose.yml ]; then
    replace_in_file docker-compose.yml "${OLD_ARTIFACT}-postgres" "${NEW_ARTIFACT}-postgres"
    replace_in_file docker-compose.yml "POSTGRES_DB: \${DB_NAME:-${OLD_ARTIFACT}}" "POSTGRES_DB: \${DB_NAME:-${NEW_ARTIFACT}}"
    replace_in_file docker-compose.yml "POSTGRES_USER: \${DB_USERNAME:-${OLD_ARTIFACT}}" "POSTGRES_USER: \${DB_USERNAME:-${NEW_ARTIFACT}}"
    replace_in_file docker-compose.yml "pg_isready -U ${OLD_ARTIFACT}" "pg_isready -U ${NEW_ARTIFACT}"
    echo "  Updated docker-compose.yml"
fi

# =============================================================================
# 6. Update .idea files (optional)
# =============================================================================
echo "[6/6] Updating .idea config files..."

if [ -d .idea ]; then
    for idea_file in .idea/compiler.xml .idea/workspace.xml; do
        if [ -f "$idea_file" ]; then
            replace_in_file "$idea_file" "\"${OLD_ARTIFACT}\"" "\"${NEW_ARTIFACT}\""
            replace_in_file "$idea_file" "${OLD_PACKAGE}.${OLD_APP_CLASS}" "${NEW_PACKAGE}.${NEW_APP_CLASS}"
            replace_in_file "$idea_file" "name=\"${OLD_APP_CLASS}\"" "name=\"${NEW_APP_CLASS}\""
            echo "  Updated $idea_file"
        fi
    done
fi

# =============================================================================
# Clean up target directory
# =============================================================================
if [ -d target ]; then
    rm -rf target
    echo ""
    echo "Cleaned target/ directory"
fi

echo ""
echo "============================================="
echo "  Done! Project renamed successfully."
echo "============================================="
echo ""
echo "Next steps:"
echo "  1. Review changes:  git diff"
echo "  2. Create the DB:   createdb ${NEW_ARTIFACT}"
echo "  3. Build:           ./mvnw clean compile"
echo "  4. Run tests:       ./mvnw test"
echo ""
