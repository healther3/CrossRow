"""
RAGAS Evaluation for CrossRow RAG Pipeline.

Evaluates retrieval + generation quality across all three domains
using the RAGAS framework (faithfulness, answer_relevancy).

Usage:
    python ragas_eval.py                    # Run all domains
    python ragas_eval.py --domain philosophy  # Single domain
    python ragas_eval.py --dry-run          # Just fetch data, skip RAGAS scoring
"""
import argparse
import json
import os
import sys
import time

import pandas as pd
from ragas import evaluate, EvaluationDataset, RunConfig
from ragas.metrics import Faithfulness, ResponseRelevancy
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings

import config
from utils.api_client import CrossRowClient


def load_test_data(domain: str = None) -> list[dict]:
    path = os.path.join(os.path.dirname(__file__), "datasets", "ragas_tests.json")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    tests = data["tests"]
    if domain:
        tests = [t for t in tests if t["domain"] == domain]
    return tests


def collect_rag_data(client: CrossRowClient, tests: list[dict]) -> list[dict]:
    """Call CrossRow eval/rag endpoint and collect question/answer/contexts."""
    results = []
    for i, test in enumerate(tests):
        print(f"  [{i+1}/{len(tests)}] {test['domain']}: {test['question'][:40]}...", end=" ", flush=True)
        t0 = time.time()
        try:
            resp = client.eval_rag(test["domain"], test["question"])
            elapsed = time.time() - t0
            contexts = [c["text"] for c in resp.get("contexts", [])]
            results.append({
                "question": test["question"],
                "answer": resp.get("answer", ""),
                "contexts": contexts,
                "domain": test["domain"],
                "retrieval_time": elapsed,
                "num_contexts": len(contexts),
            })
            print(f"OK ({elapsed:.1f}s, {len(contexts)} docs)")
        except Exception as e:
            print(f"FAIL: {e}")
            results.append({
                "question": test["question"],
                "answer": f"Error: {e}",
                "contexts": [],
                "domain": test["domain"],
                "retrieval_time": 0,
                "num_contexts": 0,
            })
    return results


def run_ragas(collected: list[dict]) -> dict:
    """Run RAGAS evaluation on collected data."""
    valid = [r for r in collected if r["contexts"] and r["answer"] and not r["answer"].startswith("Error")]
    if not valid:
        print("  [WARN] No valid results to evaluate!")
        return {"scores": {}, "details": []}

    df = pd.DataFrame({
        "user_input": [r["question"] for r in valid],
        "response": [r["answer"] for r in valid],
        "retrieved_contexts": [r["contexts"] for r in valid],
    })
    dataset = EvaluationDataset.from_pandas(df)

    evaluator_llm = LangchainLLMWrapper(ChatGoogleGenerativeAI(
        model=config.JUDGE_MODEL,
        google_api_key=config.GOOGLE_API_KEY,
        timeout=120,
        max_retries=3,
    ))
    evaluator_embeddings = LangchainEmbeddingsWrapper(GoogleGenerativeAIEmbeddings(
        model="gemini-embedding-001",
        google_api_key=config.GOOGLE_API_KEY,
    ))

    run_config = RunConfig(
        max_workers=2,
        max_wait=180,
        max_retries=3,
    )

    result = evaluate(
        dataset=dataset,
        metrics=[Faithfulness(), ResponseRelevancy()],
        llm=evaluator_llm,
        embeddings=evaluator_embeddings,
        run_config=run_config,
    )

    result_df = result.to_pandas()
    score_cols = [c for c in result_df.columns if c not in ("user_input", "response", "retrieved_contexts", "reference")]
    scores = {col: float(result_df[col].mean()) for col in score_cols if result_df[col].notna().any()}
    details = result_df.to_dict(orient="records")

    return {"scores": scores, "details": details}


def run(domain: str = None, dry_run: bool = False) -> dict:
    print("=" * 60)
    print("RAGAS Evaluation for CrossRow")
    print("=" * 60)

    client = CrossRowClient()
    tests = load_test_data(domain)
    print(f"\nLoaded {len(tests)} test questions" + (f" (domain={domain})" if domain else " (all domains)"))

    print("\n--- Collecting RAG data from backend ---")
    collected = collect_rag_data(client, tests)

    if dry_run:
        print("\n[DRY RUN] Skipping RAGAS scoring")
        return {"collected": collected}

    print("\n--- Running RAGAS evaluation ---")

    domains = set(r["domain"] for r in collected)
    per_domain = {}
    for d in sorted(domains):
        domain_data = [r for r in collected if r["domain"] == d]
        print(f"\n  Domain: {d} ({len(domain_data)} questions)")
        per_domain[d] = run_ragas(domain_data)

    print("\n--- Overall RAGAS scores ---")
    all_result = run_ragas(collected)

    for metric, score in all_result.get("scores", {}).items():
        print(f"  {metric}: {score:.4f}")

    output = {
        "scores": all_result.get("scores", {}),
        "per_domain": {d: v.get("scores", {}) for d, v in per_domain.items()},
        "details": all_result.get("details", []),
        "total_questions": len(collected),
        "valid_questions": len([r for r in collected if r["contexts"]]),
    }

    results_path = os.path.join(os.path.dirname(__file__), "reports")
    os.makedirs(results_path, exist_ok=True)
    with open(os.path.join(results_path, "ragas_raw.json"), "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, default=str)
    print(f"\nRaw results saved to reports/ragas_raw.json")

    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RAGAS evaluation for CrossRow RAG")
    parser.add_argument("--domain", type=str, choices=["philosophy", "psychology", "sociology"])
    parser.add_argument("--dry-run", action="store_true", help="Collect data only, skip scoring")
    args = parser.parse_args()
    run(domain=args.domain, dry_run=args.dry_run)
