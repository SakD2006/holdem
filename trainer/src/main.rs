//! `trainer`: runs a solver on a game and reports how close it gets to an equilibrium.
//!
//!   trainer solve --game leduc --algo dcfr --iterations 10000 [--points 40] [--seed 1]
//!                 [--csv runs/leduc-dcfr.csv] [--strategy runs/leduc-dcfr.txt]
//!   trainer info --game leduc
//!   trainer abstraction --out runs/abstraction-50.bin [--buckets 50]
//!   trainer holdem --abstraction runs/abstraction-50.bin --iterations 1000000 [--points 6]
//!                  [--deals 20000] [--lbr-deals 5000] [--seed 1] [--threads N] [--table-bits 21]
//!                  [--epoch 100000] [--save runs/blueprint.bin]
//!   trainer lbr --abstraction runs/abstraction-50.bin --blueprint runs/blueprint.bin [--deals 50000]
//!
//! Hold'em progress is reported as winnings against an always-call player, and as what local
//! best response (LBR) wins against the strategy, in milli-big-blinds per hand (lower is better).
//!
//! Algorithms: cfr, cfr+, lcfr, dcfr (whole tree); mccfr, lmccfr (external sampling).

use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::path::Path;
use std::process::ExitCode;
use std::sync::Arc;
use std::time::Instant;

use holdem_trainer::game::Game;
use holdem_trainer::games::{Kuhn, Leduc};
use holdem_trainer::holdem::abstraction::{Abstraction, Bucketer, BuildOptions, CategoryBuckets};
use holdem_trainer::holdem::game::Holdem;
use holdem_trainer::holdem::lbr::{local_best_response, LbrOptions};
use holdem_trainer::holdem::play::{duel, CallingStation, TableAgent};
use holdem_trainer::solver::best_response::{expected_values, exploitability};
use holdem_trainer::solver::cfr::{Cfr, Variant};
use holdem_trainer::solver::mccfr::ExternalSampling;
use holdem_trainer::solver::parallel::{Blueprint, ParallelMccfr};
use holdem_trainer::solver::{Solver, Uniform};

const USAGE: &str = "usage:
  trainer solve --game kuhn|leduc --algo cfr|cfr+|lcfr|dcfr|mccfr|lmccfr --iterations N
                [--points P] [--seed S] [--csv FILE] [--strategy FILE]
  trainer info --game kuhn|leduc
  trainer abstraction --out FILE [--buckets B] [--seed S]
  trainer holdem --abstraction FILE|categories --iterations N [--points P] [--deals D]
                 [--lbr-deals L] [--seed S] [--threads T] [--table-bits B] [--epoch E] [--save FILE]
  trainer lbr --abstraction FILE|categories --blueprint FILE [--deals D] [--seed S] [--threads T]
  trainer duel --abstraction FILE|categories --a FILE --b FILE [--deals D] [--seed S]";

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
    if command == "abstraction" {
        return build_abstraction(&options);
    }
    if command == "holdem" {
        return train_holdem(&options);
    }
    if command == "lbr" {
        return measure_lbr(&options);
    }
    if command == "duel" {
        return duel_blueprints(&options);
    }
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

fn build_abstraction(options: &HashMap<String, String>) -> Result<(), String> {
    let out = options.get("out").ok_or("--out is required")?;
    let buckets = number(options, "buckets", 50)? as usize;
    if !(2..=255).contains(&buckets) {
        return Err("--buckets must be between 2 and 255".into());
    }
    let build = BuildOptions {
        flop_buckets: buckets,
        turn_buckets: buckets,
        river_buckets: buckets,
        seed: number(options, "seed", 1)?,
        ..BuildOptions::default()
    };
    let started = Instant::now();
    let abstraction =
        Abstraction::build(&build, |step| eprintln!("[{:>6.1}s] {step}", started.elapsed().as_secs_f64()));
    abstraction.save(Path::new(out)).map_err(io)?;
    eprintln!("[{:>6.1}s] written to {out}", started.elapsed().as_secs_f64());
    Ok(())
}

/// Trains heads-up Hold'em with linear MCCFR and, at each checkpoint, plays duplicate matches
/// against an always-call player and a random player.
fn train_holdem(options: &HashMap<String, String>) -> Result<(), String> {
    let source = options.get("abstraction").ok_or("--abstraction is required (a file, or `categories`)")?;
    let started = Instant::now();
    let buckets: Arc<dyn Bucketer> = if source == "categories" {
        Arc::new(CategoryBuckets)
    } else {
        Arc::new(Abstraction::load(Path::new(source)).map_err(|e| format!("could not read {source}: {e}"))?)
    };
    eprintln!("abstraction ready in {:.1}s: buckets {:?}", started.elapsed().as_secs_f64(), buckets.sizes());
    let iterations = number(options, "iterations", 1_000_000)?;
    let points = number(options, "points", 6)?.max(1);
    let deals = number(options, "deals", 20_000)?;
    let lbr_deals = number(options, "lbr-deals", 5_000)?;
    let seed = number(options, "seed", 1)?;
    let cores = std::thread::available_parallelism().map_or(4, |n| n.get()) as u64;
    let threads = number(options, "threads", cores)? as usize;
    let table_bits = number(options, "table-bits", 21)? as u32;
    let epoch = number(options, "epoch", 100_000)?;
    let mut solver = ParallelMccfr::new(Holdem::new(buckets.clone()), table_bits, threads, epoch, seed);
    let game = Holdem::new(buckets);
    eprintln!(
        "{threads} threads; table of {} information sets ({:.1} GB); discount every {epoch} iterations",
        solver.table().capacity(),
        solver.table().capacity() as f64 * 128.0 / 1e9
    );

    println!(
        "{:>12} {:>9} {:>10} {:>10} {:>22} {:>22}",
        "iteration", "seconds", "iter/s", "infosets", "vs always-call bb/100", "LBR wins mbb/hand"
    );
    let mut training = 0.0;
    for checkpoint in log_spaced(iterations, points) {
        let lap = Instant::now();
        let before = solver.iterations();
        solver.run_until(checkpoint);
        let seconds = lap.elapsed().as_secs_f64();
        training += seconds;
        let agent = TableAgent(solver.table());
        let calls = duel(&game, &agent, &CallingStation, deals, seed ^ 0xCA11);
        let lbr = local_best_response(
            &game,
            solver.table(),
            &LbrOptions { deals: lbr_deals, seed: seed ^ 0x1B12, threads },
        );
        println!(
            "{:>12} {:>9.1} {:>10.0} {:>10} {:>14.1} ±{:>6.1} {:>14.0} ±{:>6.0}",
            checkpoint,
            training,
            (checkpoint - before) as f64 / seconds.max(1e-9),
            solver.table().len(),
            calls.bb_per_100,
            calls.margin,
            lbr.bb_per_100 * 10.0,
            lbr.margin * 10.0
        );
    }
    if let Some(path) = options.get("save") {
        let written = solver.table().save_strategy(Path::new(path)).map_err(io)?;
        eprintln!("{written} information sets written to {path}");
    }
    Ok(())
}

fn load_buckets(options: &HashMap<String, String>) -> Result<Arc<dyn Bucketer>, String> {
    let source = options.get("abstraction").ok_or("--abstraction is required (a file, or `categories`)")?;
    Ok(if source == "categories" {
        Arc::new(CategoryBuckets)
    } else {
        Arc::new(Abstraction::load(Path::new(source)).map_err(|e| format!("could not read {source}: {e}"))?)
    })
}

/// Plays two saved strategies against each other on duplicate deals.
fn duel_blueprints(options: &HashMap<String, String>) -> Result<(), String> {
    let game = Holdem::new(load_buckets(options)?);
    let read = |name: &str| -> Result<Blueprint, String> {
        let path = options.get(name).ok_or(format!("--{name} is required"))?;
        Blueprint::load(Path::new(path)).map_err(|e| format!("could not read {path}: {e}"))
    };
    let (a, b) = (read("a")?, read("b")?);
    let deals = number(options, "deals", 100_000)?;
    let result = duel(&game, &TableAgent(&a), &TableAgent(&b), deals, number(options, "seed", 1)?);
    println!(
        "a wins {:.1} ±{:.1} bb/100 against b over {} hands",
        result.bb_per_100, result.margin, result.hands
    );
    Ok(())
}

/// Measures a saved strategy with local best response.
fn measure_lbr(options: &HashMap<String, String>) -> Result<(), String> {
    let game = Holdem::new(load_buckets(options)?);
    let path = options.get("blueprint").ok_or("--blueprint is required")?;
    let blueprint = Blueprint::load(Path::new(path)).map_err(|e| format!("could not read {path}: {e}"))?;
    let cores = std::thread::available_parallelism().map_or(4, |n| n.get()) as u64;
    let lbr_options = LbrOptions {
        deals: number(options, "deals", 50_000)?,
        seed: number(options, "seed", 1)?,
        threads: number(options, "threads", cores)? as usize,
    };
    let started = Instant::now();
    let result = local_best_response(&game, &blueprint, &lbr_options);
    let calls = duel(&game, &TableAgent(&blueprint), &CallingStation, lbr_options.deals, lbr_options.seed);
    println!("{path}: {} information sets", blueprint.len());
    println!(
        "LBR wins {:.0} ±{:.0} mbb/hand over {} hands ({:.0}s)",
        result.bb_per_100 * 10.0,
        result.margin * 10.0,
        result.hands,
        started.elapsed().as_secs_f64()
    );
    println!("against always-call: {:.1} ±{:.1} bb/100", calls.bb_per_100, calls.margin);
    Ok(())
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
