"""
LLM-as-Judge Evaluation for CrossRow.

Two evaluation dimensions:
1. Routing accuracy - does the orchestrator pick the right expert?
2. Response quality - scored on relevance, professionalism, completeness, clarity, empathy

Uses Gemini as the judge model, following the methodology from
"Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena" (Zheng et al., 2023).

Usage:
    python judge_eval.py                    # Run all
    python judge_eval.py --routing-only     # Just routing
    python judge_eval.py --quality-only     # Just quality
"""
import argparse
import json
import os
import time
import uuid

from google import genai

import config
from utils.api_client import CrossRowClient

QUALITY_JUDGE_PROMPT = """You are an expert evaluator for a multi-agent counseling system called CrossRow.
The system has three expert domains: philosophy, psychology, and sociology.

Evaluate the following AI response on these 5 dimensions. Score each dimension from 1-5.

## Scoring Rubric

### Relevance (相关性) [1-5]
5: Directly addresses the user's core concern
3: Partially relevant, some drift
1: Completely off-topic

### Professionalism (专业性) [1-5]
5: Cites specific theories/frameworks from the domain, deep analysis
3: Uses some domain terminology, moderate depth
1: No professional content, generic response

### Completeness (完整性) [1-5]
5: Multi-perspective analysis + concrete advice + follow-up guidance
3: Basic analysis with some suggestions
1: Severely incomplete

### Clarity (清晰度) [1-5]
5: Well-structured, logical, easy to follow
3: Understandable but somewhat disorganized
1: Confusing and hard to follow

### Empathy (共情度) [1-5]
5: Deeply understands user's emotions, warm and supportive
3: Shows some understanding
1: Cold, mechanical, dismissive

## Input

**User Question**: {question}
**Expected Domain**: {expected_domain}
**AI Response**:
{response}

## Output Format
Output ONLY a JSON object, no markdown code blocks:
{{"relevance": <1-5>, "professionalism": <1-5>, "completeness": <1-5>, "clarity": <1-5>, "empathy": <1-5>, "reasoning": "<brief explanation in Chinese>"}}"""


def load_routing_tests() -> list[dict]:
    path = os.path.join(os.path.dirname(__file__), "datasets", "routing_tests.json")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)["tests"]


def load_quality_tests() -> list[dict]:
    path = os.path.join(os.path.dirname(__file__), "datasets", "quality_tests.json")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)["tests"]


def evaluate_routing(client: CrossRowClient) -> dict:
    """Test expert routing accuracy."""
    print("\n--- Routing Accuracy Evaluation ---")
    tests = load_routing_tests()
    print(f"Loaded {len(tests)} routing test cases")

    questions = [t["question"] for t in tests]
    print("Sending batch routing request...")
    results = client.eval_routing_batch(questions)

    correct = 0
    acceptable_correct = 0
    per_category = {}
    per_domain = {"philosophy": {"correct": 0, "total": 0},
                  "psychology": {"correct": 0, "total": 0},
                  "sociology": {"correct": 0, "total": 0}}
    details = []

    for test, result in zip(tests, results):
        routed = result["routed_to"]
        expected = test["expected"]
        acceptable = test.get("acceptable", [expected])
        is_exact = routed == expected
        is_acceptable = routed in acceptable

        if is_exact:
            correct += 1
        if is_acceptable:
            acceptable_correct += 1

        if expected in per_domain:
            per_domain[expected]["total"] += 1
            if is_exact:
                per_domain[expected]["correct"] += 1

        cat = test.get("category", "unknown")
        if cat not in per_category:
            per_category[cat] = {"correct": 0, "total": 0}
        per_category[cat]["total"] += 1
        if is_acceptable:
            per_category[cat]["correct"] += 1

        status = "EXACT" if is_exact else ("OK" if is_acceptable else "WRONG")
        details.append({
            "question": test["question"],
            "expected": expected,
            "routed_to": routed,
            "status": status,
            "category": cat,
        })
        mark = "V" if is_exact else ("~" if is_acceptable else "X")
        print(f"  [{mark}] {test['question'][:40]}... -> {routed} (expected: {expected})")

    total = len(tests)
    accuracy = correct / total if total else 0
    acceptable_accuracy = acceptable_correct / total if total else 0

    print(f"\n  Exact accuracy: {correct}/{total} = {accuracy:.1%}")
    print(f"  Acceptable accuracy: {acceptable_correct}/{total} = {acceptable_accuracy:.1%}")

    domain_acc = {}
    for d, counts in per_domain.items():
        if counts["total"] > 0:
            acc = counts["correct"] / counts["total"]
            domain_acc[d] = acc
            print(f"  {d}: {counts['correct']}/{counts['total']} = {acc:.1%}")

    return {
        "total": total,
        "correct": correct,
        "accuracy": f"{accuracy:.1%}",
        "accuracy_pct": accuracy * 100,
        "acceptable_accuracy": f"{acceptable_accuracy:.1%}",
        "per_domain": domain_acc,
        "details": details,
    }


def evaluate_quality(client: CrossRowClient) -> dict:
    """Test response quality using LLM-as-Judge."""
    print("\n--- Response Quality Evaluation (LLM-as-Judge) ---")
    tests = load_quality_tests()
    print(f"Loaded {len(tests)} quality test cases")

    judge_client = genai.Client(api_key=config.GOOGLE_API_KEY)

    all_scores = []
    details = []

    for i, test in enumerate(tests):
        question = test["question"]
        expected_domain = test["expected_domain"]
        print(f"\n  [{i+1}/{len(tests)}] {question[:50]}...")

        # Get response from CrossRow expert endpoint (auth-free sync)
        print(f"    Getting expert response...", end=" ", flush=True)
        t0 = time.time()
        try:
            resp = client.eval_expert_sync(question)
            response = resp.get("answer", "")
            routed_domain = resp.get("domain", "unknown")
            elapsed = time.time() - t0
            print(f"OK ({elapsed:.1f}s, {len(response)} chars, routed to {routed_domain})")
        except Exception as e:
            elapsed = time.time() - t0
            print(f"FAIL ({elapsed:.1f}s): {e}")
            response = f"Error: {e}"

        # Judge the response
        print(f"    Judging with {config.JUDGE_MODEL}...", end=" ", flush=True)
        prompt = QUALITY_JUDGE_PROMPT.format(
            question=question,
            expected_domain=expected_domain,
            response=response[:4000],
        )

        try:
            judge_resp = judge_client.models.generate_content(model=config.JUDGE_MODEL, contents=prompt)
            raw_judge = judge_resp.text.strip()
            # Clean markdown if present
            if raw_judge.startswith("```"):
                raw_judge = raw_judge.split("\n", 1)[1] if "\n" in raw_judge else raw_judge[3:]
                if raw_judge.endswith("```"):
                    raw_judge = raw_judge[:-3]
                raw_judge = raw_judge.strip()

            scores = json.loads(raw_judge)
            reasoning = scores.pop("reasoning", "")
            print(f"OK: {scores}")

            all_scores.append(scores)
            details.append({
                "question": question,
                "domain": expected_domain,
                "scores": scores,
                "reasoning": reasoning,
                "response_preview": response[:300],
            })
        except Exception as e:
            print(f"JUDGE ERROR: {e}")
            details.append({
                "question": question,
                "domain": expected_domain,
                "scores": {},
                "reasoning": f"Error: {e}",
                "response_preview": response[:300],
            })

        time.sleep(1)

    if not all_scores:
        return {"overall": 0, "dimensions": {}, "details": details}

    # Aggregate scores
    dims = ["relevance", "professionalism", "completeness", "clarity", "empathy"]
    avg_scores = {}
    for dim in dims:
        values = [s[dim] for s in all_scores if dim in s]
        avg_scores[dim] = sum(values) / len(values) if values else 0

    # Map 1-5 scale to 0-100 for the rubric
    # Weights from EVALUATION_CRITERIA.md: relevance 20%, professionalism 15%, completeness 15%, clarity 15%, empathy 15%
    weights = {"relevance": 0.20, "professionalism": 0.15, "completeness": 0.15, "clarity": 0.15, "empathy": 0.15}
    # Response quality is 50% of total in the rubric; route is 40%, stability is 10%
    # Here we just report the quality portion (out of 50 -> normalized to 100)
    weighted_sum = sum(avg_scores.get(d, 0) / 5 * w for d, w in weights.items())
    total_weight = sum(weights.values())
    overall = (weighted_sum / total_weight) * 100 if total_weight else 0

    print(f"\n  Overall quality score: {overall:.1f}/100")
    for dim, score in avg_scores.items():
        print(f"  {dim}: {score:.2f}/5")

    dim_display = {d: round(s / 5 * (weights[d] / total_weight * 100), 1) for d, s in avg_scores.items()}

    return {
        "overall": round(overall, 1),
        "dimensions": avg_scores,
        "dimensions_weighted": dim_display,
        "details": details,
    }


def run(routing_only: bool = False, quality_only: bool = False) -> dict:
    print("=" * 60)
    print("LLM-as-Judge Evaluation for CrossRow")
    print("=" * 60)

    client = CrossRowClient()
    output = {}

    if not quality_only:
        output["routing"] = evaluate_routing(client)

    if not routing_only:
        output["quality"] = evaluate_quality(client)

    results_path = os.path.join(os.path.dirname(__file__), "reports")
    os.makedirs(results_path, exist_ok=True)
    with open(os.path.join(results_path, "judge_raw.json"), "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, default=str)
    print(f"\nRaw results saved to reports/judge_raw.json")

    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LLM-as-Judge evaluation for CrossRow")
    parser.add_argument("--routing-only", action="store_true")
    parser.add_argument("--quality-only", action="store_true")
    args = parser.parse_args()
    run(routing_only=args.routing_only, quality_only=args.quality_only)
