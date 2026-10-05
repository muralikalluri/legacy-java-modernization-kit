# Records docs/img/strangler-demo.gif. Needs the stack running (docker compose up -d --build) and vhs, ttyd, ffmpeg.
#   vhs docs/demo.tape
Output docs/img/strangler-demo.gif
Set Shell "bash"
Set FontSize 15
Set Width 1200
Set Height 700
Set Padding 16
Set Theme "Dracula"
Set TypingSpeed 35ms

Hide
Type "export PS1='$ '" Enter
Type "scripts/reset-db.sh > /dev/null" Enter
Sleep 3s
Type "clear" Enter
Show

Type "# one gateway, two apps: which one served each page?" Enter
Sleep 1s
Type "scripts/smoke-strangler.sh | grep -E 'served by|still served|prefix|passed='" Enter
Sleep 9s
Type "# the assessment, generated from the code" Enter
Type "grep -A11 '^| Rank' sample-deliverable/QUICK_ASSESSMENT.md | cut -d'|' -f2-4 | cut -c1-130" Enter
Sleep 7s
Hide
Type "scripts/reset-db.sh > /dev/null" Enter
Sleep 2s
