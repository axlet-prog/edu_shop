#!/usr/bin/env bash
# Таблица покрытия JaCoCo по модулям в формате Markdown для $GITHUB_STEP_SUMMARY.
#   bash .github/scripts/coverage-summary.sh <отчёт> <заголовок>
# <отчёт> — каталог в */build/reports/jacoco: test (unit-тесты) или integrationTest
set -euo pipefail

report="$1"
title="$2"

echo "### ${title}"
echo
echo "| Module | Lines | Branches |"
echo "|--------|------:|---------:|"
for csv in */build/reports/jacoco/"${report}"/*.csv; do
  [ -f "$csv" ] || continue
  awk -F, -v module="${csv%%/*}" '
    NR > 1 { bm += $6; bc += $7; lm += $8; lc += $9 }
    END {
      lines = (lm + lc) ? sprintf("%.1f%% (%d/%d)", 100 * lc / (lm + lc), lc, lm + lc) : "n/a"
      branches = (bm + bc) ? sprintf("%.1f%% (%d/%d)", 100 * bc / (bm + bc), bc, bm + bc) : "n/a"
      printf "| %s | %s | %s |\n", module, lines, branches
    }' "$csv"
done
