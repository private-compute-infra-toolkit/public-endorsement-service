#!/bin/bash
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


set -euo pipefail

lib="$1"

symbols="$(objdump -T "$lib" | grep '\.text' | awk '{print $7}')"

# These are the expected exported symbols. They are essentially all the symbols
# we are overriding. See preload.cc for more details.
expected_symbols='
__res_init
__res_ninit
accept
accept4
bind
connect
epoll_ctl
getsockopt
ioctl
listen
setsockopt'


missing=0
for sym in $expected_symbols; do
  [[ -z "$sym" ]] && continue
  if ! echo "$symbols" | awk -v sym="$sym" '$0 == sym {found=1} END {exit !found}'; then
    echo "Missing expected symbol: $sym"
    missing=1
  fi
done

if [[ $missing -ne 0 ]]; then
  echo "!! FAILURE !! missing required symbols"
  echo "==== Expected Symbols: ===="
  echo "$expected_symbols"
  echo "==== Actual Symbols: ===="
  echo "$symbols" | cat -A
  exit 1
fi

echo "All expected symbols are present."
