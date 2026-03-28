"""
CrossRow Evaluation Suite - Main Runner.

Runs all three evaluation benchmarks and generates a combined report.

Usage:
    python run_all.py                   # Run all evaluations
    python run_all.py --only ragas      # Run RAGAS only
    python run_all.py --only gaia       # Run GAIA only
    python run_all.py --only judge      # Run LLM-as-Judge only
    python run_all.py --skip gaia       # Skip GAIA (run RAGAS + Judge)
"""
import argparse
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

import config
from utils.report import generate_report


def main():
    parser = argparse.ArgumentParser(description="CrossRow Evaluation Suite")
    parser.add_argument("--only", type=str, choices=["ragas", "gaia", "judge"],
                        help="Run only one evaluation")
    parser.add_argument("--skip", type=str, nargs="+", choices=["ragas", "gaia", "judge"],
                        default=[], help="Skip specific evaluations")
    parser.add_argument("--gaia-max", type=int, default=None, help="Max GAIA questions")
    parser.add_argument("--gaia-compare", action="store_true", help="Compare with baseline Gemini")
    parser.add_argument("--ragas-domain", type=str, choices=["philosophy", "psychology", "sociology"],
                        help="RAGAS: test single domain only")
    parser.add_argument("--judge-routing-only", action="store_true")
    parser.add_argument("--judge-quality-only", action="store_true")
    args = parser.parse_args()

    if not config.GOOGLE_API_KEY:
        print("[ERROR] GOOGLE_API_KEY not set. Export it or add to config_local.py")
        print("  export GOOGLE_API_KEY=your-key-here")
        sys.exit(1)

    should_run = lambda name: (args.only is None or args.only == name) and name not in args.skip
    results = {}

    print("\n" + "=" * 60)
    print("  CrossRow Evaluation Suite")
    print("=" * 60)
    print(f"  API: {config.API_BASE_URL}")
    print(f"  Judge model: {config.JUDGE_MODEL}")
    print(f"  Evaluations: ", end="")
    names = [n for n in ["ragas", "gaia", "judge"] if should_run(n)]
    print(", ".join(names) if names else "none")
    print("=" * 60)

    # ---- Step 1: RAGAS ----
    if should_run("ragas"):
        print("\n\n" + "=" * 60)
        print("  STEP 1/3: RAGAS Evaluation")
        print("=" * 60)
        from ragas_eval import run as run_ragas
        results["ragas"] = run_ragas(domain=args.ragas_domain)

    # ---- Step 2: GAIA ----
    if should_run("gaia"):
        print("\n\n" + "=" * 60)
        print("  STEP 2/3: GAIA Benchmark")
        print("=" * 60)
        from gaia_eval import run as run_gaia
        results["gaia"] = run_gaia(
            max_questions=args.gaia_max,
            compare_baseline=args.gaia_compare,
        )

    # ---- Step 3: LLM-as-Judge ----
    if should_run("judge"):
        print("\n\n" + "=" * 60)
        print("  STEP 3/3: LLM-as-Judge")
        print("=" * 60)
        from judge_eval import run as run_judge
        judge_result = run_judge(
            routing_only=args.judge_routing_only,
            quality_only=args.judge_quality_only,
        )
        if "routing" in judge_result:
            results["routing"] = judge_result["routing"]
        if "quality" in judge_result:
            results["quality"] = judge_result["quality"]

    # ---- Generate Report ----
    if results:
        print("\n\n" + "=" * 60)
        print("  Generating Report")
        print("=" * 60)
        report_path = generate_report(results)

        print("\n" + "=" * 60)
        print("  EVALUATION COMPLETE")
        print("=" * 60)

        if "ragas" in results and "scores" in results["ragas"]:
            scores = results["ragas"]["scores"]
            print(f"  RAGAS:   faithfulness={scores.get('faithfulness', 'N/A'):.4f}  "
                  f"answer_relevancy={scores.get('answer_relevancy', 'N/A'):.4f}")

        if "gaia" in results:
            print(f"  GAIA:    {results['gaia'].get('accuracy', 'N/A')} "
                  f"({results['gaia'].get('correct', '?')}/{results['gaia'].get('total', '?')})")

        if "routing" in results:
            print(f"  Routing: {results['routing'].get('accuracy', 'N/A')} "
                  f"({results['routing'].get('correct', '?')}/{results['routing'].get('total', '?')})")

        if "quality" in results:
            print(f"  Quality: {results['quality'].get('overall', 'N/A')}/100")

        print(f"\n  Report: {report_path}")
        print("=" * 60)
    else:
        print("\nNo evaluations were run.")


if __name__ == "__main__":
    main()
