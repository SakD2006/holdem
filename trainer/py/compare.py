"""Runs several solvers on one game and charts how fast each approaches an equilibrium.

    python3 trainer/py/compare.py --game leduc --iterations 10000
    python3 trainer/py/compare.py --game leduc --algos dcfr,lmccfr --iterations 20000

Writes trainer/runs/<game>-compare.csv and trainer/runs/<game>-compare.svg (exploitability against
training time, both on log scales). Needs only the Python standard library and cargo.
"""

import argparse
import csv
import math
import subprocess
from pathlib import Path

TRAINER = Path(__file__).resolve().parent.parent
RUNS = TRAINER / "runs"

# Sampled solvers do a tiny fraction of the work per iteration, so they get more iterations.
SAMPLED = {"mccfr", "lmccfr"}
SAMPLED_FACTOR = 500

COLOURS = ["#2a6fdb", "#d1495b", "#2e933c", "#e0a100", "#7b4bb7", "#00798c"]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game", default="leduc", choices=["kuhn", "leduc"])
    parser.add_argument("--algos", default="cfr,cfr+,lcfr,dcfr,lmccfr")
    parser.add_argument("--iterations", type=int, default=5000, help="for the whole-tree solvers")
    parser.add_argument("--points", type=int, default=30)
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()

    subprocess.run(["cargo", "build", "--release", "--quiet"], cwd=TRAINER, check=True)
    binary = TRAINER / "target" / "release" / "trainer"
    RUNS.mkdir(exist_ok=True)

    rows = []
    for algo in args.algos.split(","):
        iterations = args.iterations * (SAMPLED_FACTOR if algo in SAMPLED else 1)
        out = RUNS / f"{args.game}-{algo}.csv"
        print(f"{algo}: {iterations:,} iterations", flush=True)
        subprocess.run(
            [binary, "solve", "--game", args.game, "--algo", algo, "--iterations", str(iterations),
             "--points", str(args.points), "--seed", str(args.seed), "--csv", out],
            check=True, stdout=subprocess.DEVNULL)
        with open(out, newline="") as f:
            rows.extend(csv.DictReader(f))

    combined = RUNS / f"{args.game}-compare.csv"
    with open(combined, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    chart = RUNS / f"{args.game}-compare.svg"
    chart.write_text(svg_chart(rows, args.game))
    print_summary(rows)
    print(f"\nwrote {combined.relative_to(TRAINER.parent)} and {chart.relative_to(TRAINER.parent)}")


def print_summary(rows):
    last = {}
    for row in rows:
        last[row["algo"]] = row
    print(f"\n{'algorithm':<10} {'iterations':>12} {'seconds':>9} {'exploitability':>16}")
    for algo, row in last.items():
        print(f"{algo:<10} {int(row['iteration']):>12,} {float(row['seconds']):>9.2f} "
              f"{float(row['exploitability']):>16.6f}")


def svg_chart(rows, game, width=760, height=460):
    """A log-log line chart of exploitability against seconds of training, one line per solver."""
    points = [(float(r["seconds"]), float(r["exploitability"]), r["algo"]) for r in rows
              if float(r["seconds"]) > 0 and float(r["exploitability"]) > 0]
    xs = [math.log10(p[0]) for p in points]
    ys = [math.log10(p[1]) for p in points]
    x0, x1 = math.floor(min(xs)), math.ceil(max(xs))
    y0, y1 = math.floor(min(ys)), math.ceil(max(ys))
    left, right, top, bottom = 70, 150, 40, 50

    def sx(v):
        return left + (math.log10(v) - x0) / (x1 - x0) * (width - left - right)

    def sy(v):
        return height - bottom - (math.log10(v) - y0) / (y1 - y0) * (height - top - bottom)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'font-family="Helvetica, Arial, sans-serif" font-size="12">',
        f'<rect width="{width}" height="{height}" fill="white"/>',
        f'<text x="{left}" y="22" font-size="14" font-weight="bold">'
        f'{game.capitalize()}: exploitability against training time</text>',
    ]
    for e in range(x0, x1 + 1):
        x = sx(10 ** e)
        parts.append(f'<line x1="{x:.1f}" y1="{top}" x2="{x:.1f}" y2="{height - bottom}" stroke="#e5e5e5"/>')
        parts.append(f'<text x="{x:.1f}" y="{height - bottom + 18}" text-anchor="middle">{label(e)}</text>')
    for e in range(y0, y1 + 1):
        y = sy(10 ** e)
        parts.append(f'<line x1="{left}" y1="{y:.1f}" x2="{width - right}" y2="{y:.1f}" stroke="#e5e5e5"/>')
        parts.append(f'<text x="{left - 8}" y="{y + 4:.1f}" text-anchor="end">{label(e)}</text>')
    parts.append(f'<text x="{(left + width - right) / 2}" y="{height - 12}" text-anchor="middle">'
                 f'seconds of training (log scale)</text>')
    parts.append(f'<text transform="translate(16,{(top + height - bottom) / 2}) rotate(-90)" '
                 f'text-anchor="middle">exploitability, chips per hand (log scale)</text>')

    algos = list(dict.fromkeys(p[2] for p in points))
    for i, algo in enumerate(algos):
        colour = COLOURS[i % len(COLOURS)]
        line = " ".join(f"{sx(s):.1f},{sy(e):.1f}" for s, e, a in points if a == algo)
        parts.append(f'<polyline points="{line}" fill="none" stroke="{colour}" stroke-width="2"/>')
        y = top + 10 + i * 20
        parts.append(f'<line x1="{width - right + 14}" y1="{y}" x2="{width - right + 34}" y2="{y}" '
                     f'stroke="{colour}" stroke-width="3"/>')
        parts.append(f'<text x="{width - right + 40}" y="{y + 4}">{algo}</text>')
    parts.append("</svg>")
    return "\n".join(parts)


def label(exponent):
    value = 10 ** exponent
    return f"{value:g}" if -3 <= exponent <= 4 else f"1e{exponent}"


if __name__ == "__main__":
    main()
