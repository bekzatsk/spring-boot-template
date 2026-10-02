#!/bin/bash
# Generates a PKCS12 keystore for JWT RSA signing
# Usage: ./scripts/generate-keystore.sh [output-path]

set -euo pipefail

# Default outside src/: anything under src/main/resources is packaged into the jar, and a signing
# key in a published artifact lets anyone mint valid tokens.
KEYSTORE_PATH="${1:-secrets/jwt-keystore.p12}"
ALIAS="jwt"
# A fixed default password ("changeit") protects nothing; generate one unless one is given.
STOREPASS="${JWT_KEYSTORE_PASSWORD:-$(openssl rand -base64 24)}"

case "$KEYSTORE_PATH" in
  src/*|*/src/main/resources/*)
    echo "Refusing to write a signing key under src/: it would be packaged into the jar." >&2
    exit 1
    ;;
esac
mkdir -p "$(dirname "$KEYSTORE_PATH")"
VALIDITY=3650
KEYSIZE=2048

if [ -f "$KEYSTORE_PATH" ]; then
    echo "Keystore already exists at $KEYSTORE_PATH"
    echo "Delete it first if you want to regenerate."
    exit 1
fi

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize "$KEYSIZE" \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$STOREPASS" \
  -validity "$VALIDITY" \
  -dname "CN=localhost, OU=Dev, O=Template, L=Unknown, ST=Unknown, C=US" \
  -noprompt

echo ""
echo "Keystore generated at $KEYSTORE_PATH"
echo ""
echo "IMPORTANT:"
echo "  1. Keep $KEYSTORE_PATH out of git (secrets/ and jwt-keystore.* are ignored)"
echo "  2. Store the password in your secret manager; it is shown only now"
echo "  3. Configure via environment variables:"
echo "     JWT_KEYSTORE_LOCATION=file:$(cd "$(dirname "$KEYSTORE_PATH")" && pwd)/$(basename "$KEYSTORE_PATH")"
echo "     JWT_KEYSTORE_PASSWORD=$STOREPASS"
echo "     JWT_KEY_ALIAS=$ALIAS"
