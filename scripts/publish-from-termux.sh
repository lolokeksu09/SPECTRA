#!/usr/bin/env bash
set -euo pipefail

repo='lolokeksu09/SPECTRA'
if ! command -v gh >/dev/null || ! command -v git >/dev/null; then
  echo 'Install git and gh first: pkg install git gh'
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo 'Connect your GitHub account first: gh auth login'
  exit 1
fi
if gh repo view "$repo" >/dev/null 2>&1; then
  echo "Repository $repo already exists; stopping to avoid modifying any existing project."
  exit 1
fi
if [ -d .git ]; then
  echo 'Folder already has a Git repository; refusing to overwrite it.'
  exit 1
fi

git init -b main
git add .
git -c user.name='lolokeksu09' \
  -c user.email='325224375+lolokeksu09@users.noreply.github.com' \
  commit -m 'feat: initial SPECTRA 0.1 Android digital twin'
gh repo create "$repo" --private --source=. --remote=origin --push
printf '\nSPECTRA uploaded. Check https://github.com/%s/actions and /releases for build results.\n' "$repo"
