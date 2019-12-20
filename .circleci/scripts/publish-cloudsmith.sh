#!/bin/bash

set -e
set -o pipefail

REPO=""
case "${CIRCLE_BRANCH}" in
  develop)
    REPO="horizon-develop"
    ;;
  foundation-*)
    REPO="horizon-${CIRCLE_BRANCH}"
    ;;
  release-*)
    REPO="horizon-rc"
    ;;
  master)
    REPO="stable"
    ;;
  ranger/cloudsmith)
    REPO="horizon-foundation-2019"
    ;;
  *)
    echo "This branch is not eligible for deployment: ${CIRCLE_BRANCH}"
    exit 0
    ;;
esac

find target -type f | sort -u

for FILE in target/rpm/RPMS/noarch/*.rpm; do
  # give it 3 tries then die
  cloudsmith push rpm "opennms/$REPO/el/5" "$FILE" ||
  cloudsmith push rpm "opennms/$REPO/el/5" "$FILE" ||
  cloudsmith push rpm "opennms/$REPO/el/5" "$FILE" || exit 1
done
for FILE in target/debs/*.deb; do
  # give it 3 tries then die
  cloudsmith push deb "opennms/$REPO/debian/etch" "$FILE" ||
  cloudsmith push deb "opennms/$REPO/debian/etch" "$FILE" ||
  cloudsmith push deb "opennms/$REPO/debian/etch" "$FILE" || exit 1
done
