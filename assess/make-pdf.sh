#!/usr/bin/env bash
# Renders the sample deliverables to PDF: pandoc (Markdown to HTML) then headless Chrome (HTML to PDF).
# Needs pandoc and Google Chrome or Chromium; Mermaid diagrams are drawn by mermaid.js from a CDN, so it needs network.
# PDFs embed a creation date, so they are NOT part of the byte-identical check in CI. Run it by hand after run-all.sh.
#   ./assess/make-pdf.sh
set -euo pipefail
cd "$(dirname "$0")/.."

CHROME="${CHROME:-}"
for c in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" google-chrome chromium chromium-browser; do
  [ -n "$CHROME" ] && break
  if [ -x "$c" ] || command -v "$c" >/dev/null 2>&1; then CHROME="$c"; fi
done
[ -n "$CHROME" ] || { echo "Chrome or Chromium not found; set CHROME=/path/to/binary" >&2; exit 1; }
command -v pandoc >/dev/null || { echo "pandoc not found" >&2; exit 1; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/head.html" <<'H'
<style>
  body { font: 10pt/1.45 -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; color: #1b1f24; max-width: none; margin: 0; }
  h1 { font-size: 20pt; border-bottom: 2px solid #1b1f24; padding-bottom: 4pt; }
  h2 { font-size: 14pt; margin-top: 18pt; border-bottom: 1px solid #d0d7de; padding-bottom: 2pt; break-after: avoid; }
  h3 { font-size: 11pt; break-after: avoid; }
  table { border-collapse: collapse; width: 100%; margin: 8pt 0; font-size: 8.5pt; }
  th, td { border: 1px solid #d0d7de; padding: 3pt 5pt; vertical-align: top; }
  th { background: #f3f4f6; text-align: left; }
  tr { break-inside: avoid; }
  code { font: 8.5pt ui-monospace, Menlo, monospace; background: #f3f4f6; padding: 0 2pt; }
  blockquote { margin: 8pt 0; padding: 4pt 10pt; border-left: 3pt solid #d0d7de; color: #4b5563; }
  .mermaid { text-align: center; margin: 10pt 0; break-inside: avoid; }
  @page { size: A4; margin: 16mm 14mm; }
</style>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<script>
  window.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("pre.mermaid").forEach(function (pre) {
      var d = document.createElement("div"); d.className = "mermaid"; d.textContent = pre.textContent; pre.replaceWith(d);
    });
    mermaid.initialize({ startOnLoad: false, theme: "neutral" });
    mermaid.run({ querySelector: ".mermaid" });
  });
</script>
H

for name in QUICK_ASSESSMENT ASSESSMENT_AND_ROADMAP; do
  src="sample-deliverable/$name.md"
  [ -f "$src" ] || continue
  pandoc "$src" --from gfm --to html5 --standalone --metadata title="$name" --metadata pagetitle="$name" \
    --include-in-header="$TMP/head.html" -o "$TMP/$name.html"
  # The title block pandoc adds would repeat the document's own heading.
  sed -i.bak '/<header id="title-block-header">/,/<\/header>/d' "$TMP/$name.html"
  "$CHROME" --headless=new --disable-gpu --no-pdf-header-footer --virtual-time-budget=30000 \
    --print-to-pdf="$PWD/sample-deliverable/$name.pdf" "file://$TMP/$name.html" >/dev/null 2>&1
  echo "wrote sample-deliverable/$name.pdf"
done
