#!/bin/bash
set -e

if [ "${1:0:4}" == '-jar' ]; then
    JAVA_OPTS='-Xmx4096m -Xms4096m -server -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.region=CN -Djava.security.egd=file:/dev/./urandom'
    set -- java ${JAVA_OPTS} "$@"
fi

umask 0077

exec "$@"
