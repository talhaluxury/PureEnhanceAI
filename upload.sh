#!/data/data/com.termux/files/usr/bin/sh
# Termux se GitHub par push: sh upload.sh "message"
set -e
cd "$(dirname "$0")"
[ -d .git ] || git init -b main
git config user.name "talhaluxury"
git config user.email "talhaluxury@users.noreply.github.com"
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/talhaluxury/PureEnhanceAI.git
git add -A
git commit -m "${1:-Update}" || echo "Koi nayi tabdeeli nahi."
git push -u origin main
