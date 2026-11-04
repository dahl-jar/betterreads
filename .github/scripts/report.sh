#!/usr/bin/env bash

failures=0

report() {
  if [ "$2" = "$3" ]; then
    echo "ok - $1"
  else
    echo "not ok - $1: expected '$2', got '$3'"
    failures=$((failures + 1))
  fi
}
