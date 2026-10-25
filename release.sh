#!/usr/bin/env bash
# Builds a signed release APK and publishes it as a GitHub Release.
# Usage: ./release.sh 0.1.0   (needs SOFA_KEYSTORE / SOFA_KEYSTORE_PASSWORD in the environment)
set -euo pipefail
v="${1:?version, e.g. 0.1.0}"
: "${SOFA_KEYSTORE:?path to the signing keystore}" "${SOFA_KEYSTORE_PASSWORD:?keystore password}"
export SOFA_VERSION_NAME="$v"
export SOFA_VERSION_CODE="$(( $(git tag -l 'v*' | wc -l) + 1 ))"
./gradlew --console=plain testReleaseUnitTest assembleRelease
out=build/release; rm -rf "$out"; mkdir -p "$out"
cp app/build/outputs/apk/release/app-release.apk "$out/sofa.apk"
cp "$out/sofa.apk" "$out/sofa-$v.apk"
sha256sum "$out/sofa.apk" | cut -d' ' -f1 > "$out/sofa.apk.sha256"
git tag "v$v"
git push origin "v$v"
gh release create "v$v" "$out/sofa.apk" "$out/sofa.apk.sha256" "$out/sofa-$v.apk" --title "Sofa $v" --generate-notes
