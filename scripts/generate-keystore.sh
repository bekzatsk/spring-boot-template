#!/bin/bash
# Generates a PKCS12 keystore for JWT RSA signing
# Usage: ./scripts/generate-keystore.sh [output-path]

set -euo pipefail

KEYSTORE_PATH="${1:-src/main/resources/jwt-keystore.p12}"
ALIAS="jwt"
STOREPASS="changeit"
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
echo "  1. Add $KEYSTORE_PATH to .gitignore (should already be excluded by *.p12 rule)"
echo "  2. For production, copy the keystore out of src/main/resources/"
echo "  3. Configure via environment variables:"
echo "     JWT_KEYSTORE_LOCATION=jwt-keystore.p12"
echo "     JWT_KEYSTORE_PASSWORD=changeit"
echo "     JWT_KEY_ALIAS=jwt"
