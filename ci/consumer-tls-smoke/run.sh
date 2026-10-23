#!/usr/bin/env bash
# Build this outside-consumer project against a locally installed (or Central)
# spiceai artifact, assert a single Netty 4.x version, and open a TLS channel
# to flight.spiceai.io. A dummy API key must produce an auth error, not
# NoClassDefFoundError.
set -euo pipefail
cd "$(dirname "$0")"

SPICEAI_VERSION="${SPICEAI_VERSION:-0.8.1}"
echo "Consumer depending on ai.spice:spiceai:${SPICEAI_VERSION}"

mvn -B -q -Dspiceai.version="${SPICEAI_VERSION}" \
    compile dependency:build-classpath -Dmdep.outputFile=cp.txt

echo "======== dependency:tree (io.netty) ========"
TREE="$(mvn -B -Dspiceai.version="${SPICEAI_VERSION}" dependency:tree -Dincludes=io.netty)"
echo "${TREE}"

# tcnative is a 2.x OpenSSL binding, not the Netty 4.x line we must converge.
VERSIONS="$(printf '%s\n' "${TREE}" \
    | grep 'io.netty:netty-' \
    | grep -v tcnative \
    | grep -oE '4\.[0-9]+\.[0-9]+\.Final' \
    | sort -u)"
COUNT="$(printf '%s\n' "${VERSIONS}" | sed '/^$/d' | wc -l | tr -d ' ')"
echo "======== resolved Netty 4.x versions ========"
echo "${VERSIONS}"
if [ "${COUNT}" != "1" ]; then
    echo "ERROR: expected a single Netty 4.x version, found ${COUNT}" >&2
    exit 1
fi

echo "======== Cloud TLS connection ========"
java --add-opens=java.base/java.nio=ALL-UNNAMED \
     --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
     -cp "target/classes:$(cat cp.txt)" x.Main
rm -f cp.txt
