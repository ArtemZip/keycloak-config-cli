#!/usr/bin/env bash

set -xeo pipefail

export SPRING_PROFILES_ACTIVE=dev

./target/keycloak-config-cli-native --keycloak.availability-check.enabled=true
./target/keycloak-config-cli-native
./target/keycloak-config-cli-native --import.force=true

./target/keycloak-config-cli-native \
  -Dkcc.junit.display-name=DISPLAYNAME \
  -Dkcc.junit.verify-email=true \
  -Dkcc.junit.not-before=1200 \
  -Dkcc.junit.browser-security-headers="{\"xRobotsTag\":\"noindex\"}" \
  --import.path="src/test/resources/import-files/realm-substitution/0_create_realm.json" \
  --import.var-substitution=true

while read -r file; do
  ./target/keycloak-config-cli-native --import.path="${file}"
done < <(
  find src/test/resources/import-files \
    -type f \
    -name '*.json' \
    ! -path '*/cli/*' \
    -and ! -path '*exported-realm*' \
    -and ! -path '*parallel*' \
    -and ! -path '*realm-substitution*' \
    -and ! -path '*realm-file-type/yaml*' \
    -and ! -path '*realm-file-type/json*' \
    -and ! -path '*realm-file-type/invalid*' \
    -and ! -path '*realm-file-type/syntax-error*' \
    -and ! -name '*invalid*' \
    -and ! -name '*try*' | sort -n
)
