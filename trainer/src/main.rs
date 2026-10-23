//! `trainer`: runs a solver on a game and reports how close it gets to an equilibrium.
//!
//!   trainer solve --game leduc --algo dcfr --iterations 10000 [--points 40] [--seed 1]
//!                 [--csv runs/leduc-dcfr.csv] [--strategy runs/leduc-dcfr.txt]
//!   trainer info --game leduc
//!
//! Algorithms: cfr, cfr+, lcfr, dcfr (whole tree); mccfr, lmccfr (external sampling).

use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::path::Path;
use std::process::ExitCode;
use std::time::Instant;

use holdem_trainer::game::Game;
use holdem_trainer::games::{Kuhn, Leduc};
use holdem_trainer::solver::best_response::{expected_values, exploitability};
use holdem_trainer::solver::cfr::{Cfr, Variant};
use holdem_trainer::solver::mccfr::ExternalSampling;
use holdem_trainer::solver::{Solver, Uniform};

const USAGE: &str = "usage:
  trainer solve --game kuhn|leduc --algo cfr|cfr+|lcfr|dcfr|mccfr|lmccfr --iterations N
                [--points P] [--seed S] [--csv FILE] [--strategy FILE]
  trainer info --game kuhn|leduc";

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match run(&args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(message) => {
            eprintln!("{message}\n\n{USAGE}");
            ExitCode::FAILURE
        }
    }
}

fn run(args: &[String]) -> Result<(), String> {
    let (command, rest) = args.split_first().ok_or("no command given")?;
    let options = parse_options(rest)?;
    let game = options.get("game").map(String::as_str).ok_or("--game is required")?;
    match (command.as_str(), game) {
        ("info", "kuhn") => info(|| Kuhn),
        ("info", "leduc") => info(|| Leduc),
        ("solve", "kuhn") => solve(|| Kuhn, &options),
        ("solve", "leduc") => solve(|| Leduc, &options),
        (_, "kuhn" | "leduc") => Err(format!("unknown command: {command}")),
        _ => Err(format!("unknown game: {game}")),
    }
}

fn parse_options(args: &[String]) -> Result<HashMap<String, String>, String> {
    let mut options = HashMap::new();
    let mut i = 0;
    while i < args.len() {
        let name =
            args[i].strip_prefix("--").ok_or_else(|| format!("expected an option, got {}", args[i]))?;
        let value = args.get(i + 1).ok_or_else(|| format!("--{name} needs a value"))?;
        options.insert(name.to_string(), value.clone());
        i += 2;
    }
    Ok(options)
}

fn number(options: &HashMap<String, String>, name: &str, default: u64) -> Result<u64, String> {
    match options.get(name) {
        None => Ok(default),
        Some(text) => text.replace('_', "").parse().map_err(|_| format!("--{name} must be a whole number")),
    }
}

fn info<G: Game>(make: impl Fn() -> G) -> Result<(), String> {
    // One iteration of CFR visits every information set once.
    let mut solver = Cfr::new(make(), Variant::Vanilla, false);
    solver.iterate();
    let game = make();
    println!("game: {}", game.name());
    println!("information sets: {}", solver.table().nodes.len());
    println!("exploitability of uniform random play: {:.6}", exploitability(&game, &Uniform));
    Ok(())
}

fn solve<G: Game + 'static>(make: impl Fn() -> G, options: &HashMap<String, String>) -> Result<(), String> {
    let algo = options.get("algo").map(String::as_str).ok_or("--algo is required")?;
    let iterations = number(options, "iterations", 1000)?;
    let points = number(options, "points", 40)?.max(1);
    let seed = number(options, "seed", 1)?;
    let keep_labels = options.contains_key("strategy");
    let mut solver: Box<dyn Solver> = match algo {
        "cfr" => Box::new(Cfr::new(make(), Variant::Vanilla, keep_labels)),
        "cfr+" => Box::new(Cfr::new(make(), Variant::Plus, keep_labels)),
        "lcfr" => Box::new(Cfr::new(make(), Variant::LINEAR, keep_labels)),
        "dcfr" => Box::new(Cfr::new(make(), Variant::DCFR, keep_labels)),
        "mccfr" => Box::new(ExternalSampling::new(make(), seed, false, keep_labels)),
        "lmccfr" => Box::new(ExternalSampling::new(make(), seed, true, keep_labels)),
        _ => return Err(format!("unknown algorithm: {algo}")),
    };
    let game = make();
    let checkpoints = log_spaced(iterations, points);

    let mut csv = match options.get("csv") {
        Some(path) => Some(create(path)?),
        None => None,
    };
    if let Some(out) = csv.as_mut() {
        writeln!(out, "game,algo,iteration,seconds,exploitability,value_p0,infosets").map_err(io)?;
    }
    println!(
        "{:>12} {:>10} {:>16} {:>12} {:>9}",
        "iteration", "seconds", "exploitability", "value p0", "infosets"
    );

    let started = Instant::now();
    let mut training = 0.0;
    for checkpoint in checkpoints {
        let lap = Instant::now();
        while solver.iterations() < checkpoint {
            solver.iterate();
        }
        training += lap.elapsed().as_secs_f64();
        let table = solver.table();
        let gap = exploitability(&game, table);
        let value = expected_values(&game, table)[0];
        println!(
            "{:>12} {:>10.2} {:>16.6} {:>12.5} {:>9}",
            checkpoint,
            training,
            gap,
            value,
            table.nodes.len()
        );
        if let Some(out) = csv.as_mut() {
            writeln!(
                out,
                "{},{algo},{checkpoint},{training:.4},{gap:.8},{value:.8},{}",
                game.name(),
                table.nodes.len()
            )
            .map_err(io)?;
        }
    }
    eprintln!(
        "done in {:.1}s (training {:.1}s, the rest measuring)",
        started.elapsed().as_secs_f64(),
        training
    );

    if let Some(path) = options.get("strategy") {
        let mut out = create(path)?;
        solver.table().write_strategy(&mut out).map_err(io)?;
        eprintln!("strategy written to {path}");
    }
    Ok(())
}

/// About `points` iteration counts from 1 to `last`, evenly spaced on a log scale, so a
/// log-log chart of the run has its points spread out.
fn log_spaced(last: u64, points: u64) -> Vec<u64> {
    let mut marks: Vec<u64> = (0..points)
        .map(|i| {
            let fraction = if points == 1 { 1.0 } else { i as f64 / (points - 1) as f64 };
            (last as f64).powf(fraction).round() as u64
        })
        .collect();
    marks.push(last);
    marks.sort_unstable();
    marks.dedup();
    marks.retain(|m| *m >= 1);
    marks
}

fn create(path: &str) -> Result<BufWriter<File>, String> {
    if let Some(folder) = Path::new(path).parent() {
        fs::create_dir_all(folder).map_err(io)?;
    }
    File::create(path).map(BufWriter::new).map_err(io)
}

fn io(error: std::io::Error) -> String {
    format!("could not write: {error}")
}
