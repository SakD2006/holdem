# IEEE Access manuscript

The combined paper: a laptop-trained MCCFR blueprint (trainer/), local best response by betting
round, the seeding pitfall, and the bounded opponent-adaptation layer. Built from the official
IEEE Access LaTeX template (class, fonts and logos copied unchanged). Text in red brackets
(`\pending{...}`) marks results or details still to come.

Build with pdfLaTeX (not XeTeX or Tectonic: the class loads its own Type 1 fonts). The class
clashes with TikZ, so figures are separate files compiled first:

```bash
cd docs/paper-access/figures && for f in system leduc streets; do pdflatex $f.tex; done
cd .. && pdflatex main && pdflatex main
```

Or upload this folder to Overleaf (compiler: pdfLaTeX) after building the figure PDFs.

Before submitting (IEEE Access checklist):
- every `\pending{...}` resolved, including the biography and an author photo (1 x 1.25 in)
- every reference checked against the published version
- public ORCID iD for the submitting author
- the AI-use statement in the acknowledgment matches actual use
- the `.tex` source and the PDF submitted together, with identical content
