#!/usr/bin/env bash
./mvnw -B install -DskipTests -Dsigning.disabled=true --no-transfer-progress
