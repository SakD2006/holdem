# Paper

One text, two layouts. Edit `abstract.tex` and `body.tex`; both versions pick up the change.

| File | Layout | For |
|---|---|---|
| `main.tex` → `main.pdf` | Sage `sagej.cls`, double spaced, line numbers | ICGA Journal submission (ScholarOne) |
| `arxiv.tex` → `arxiv.pdf` | Plain `article`, single spaced, no journal branding | arXiv preprint |

Build (Tectonic, or `pdflatex` twice; there is no BibTeX step, the references are written out in `body.tex`):

```bash
tectonic -X compile main.tex
tectonic -X compile arxiv.tex
```

arXiv wants the LaTeX source, not the PDF. Upload `arxiv-upload.zip` (`arxiv.tex`, `abstract.tex`, `body.tex`);
arXiv compiles it with pdflatex.
