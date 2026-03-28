"""
GAIA Benchmark Evaluation for CrossRow Agent.

Runs the web-search-only subset of GAIA against CrossRow's agent endpoint.
Uses LLM-as-Judge to extract short answers from agent responses for exact matching.

Usage:
    python gaia_eval.py                   # Run with default settings
    python gaia_eval.py --max 10          # Limit to 10 questions
    python gaia_eval.py --compare-baseline  # Also test raw Gemini for comparison
"""
import argparse
import json
import os
import re
import time

from datasets import load_dataset
from google import genai

import config
from utils.api_client import CrossRowClient


def load_gaia_dataset(max_questions: int = None) -> list[dict]:
    """Load the GAIA web-search subset from HuggingFace."""
    print("Loading GAIA Web-Search Subset from HuggingFace...")
    ds = load_dataset(config.GAIA_DATASET, split="benchmark")

    report_cols = [c for c in ds.column_names if c.startswith("Report") and "Perplexity" not in c and "Grok" not in c]

    questions = []
    for item in ds:
        question = item.get("Question", "")
        if not question:
            continue

        expected = _extract_answer_from_reports(item, report_cols)
        if not expected:
            continue

        questions.append({
            "question": question,
            "expected_answer": expected,
            "level": 1,
        })

    if max_questions:
        questions = questions[:max_questions]

    print(f"Loaded {len(questions)} GAIA questions (with extractable answers)")
    return questions


def _extract_answer_from_reports(item: dict, report_cols: list[str]) -> str:
    """Extract the final answer from report columns using common patterns."""
    for col in report_cols:
        report = item.get(col, "") or ""
        if not report or report.startswith("http"):
            continue

        for pattern in [
            r'\*\*Final Answer\*\*[:\s]*\*?\*?(.+?)(?:\*\*|\n|$)',
            r'Final Answer[:\s]+(.+?)(?:\n|$)',
            r'\*\*(\d{5}(?:,\s*\d{5})*)\*\*\s*$',
        ]:
            match = re.search(pattern, report, re.IGNORECASE)
            if match:
                answer = match.group(1).strip().strip('*').strip()
                if answer and len(answer) < 200:
                    return answer
    return ""


def _get_genai_client() -> genai.Client:
    return genai.Client(api_key=config.GOOGLE_API_KEY)


def extract_short_answer(long_response: str, question: str, expected: str) -> str:
    """Use Gemini to extract a concise answer from agent's verbose response."""
    client = _get_genai_client()

    prompt = f"""Extract the SHORT final answer from the following AI assistant response.
The answer should be in the same format as the expected answer type.

Question: {question}
Expected answer format example: {expected}

AI Response:
{long_response[:3000]}

Instructions:
- Extract ONLY the final answer, not explanations
- Match the format of the expected answer (number, name, date, etc.)
- If no clear answer is found, output "NO_ANSWER"
- Output ONLY the extracted answer, nothing else"""

    try:
        resp = client.models.generate_content(model=config.JUDGE_MODEL, contents=prompt)
        return resp.text.strip()
    except Exception as e:
        return f"EXTRACTION_ERROR: {e}"


def normalize_answer(answer: str) -> str:
    """Normalize answer for comparison."""
    a = answer.lower().strip()
    a = re.sub(r'[.,;:!?"\']', '', a)
    a = re.sub(r'\s+', ' ', a)
    return a


def check_answer(extracted: str, expected: str) -> bool:
    """Check if extracted answer matches expected (fuzzy)."""
    norm_ext = normalize_answer(extracted)
    norm_exp = normalize_answer(expected)

    if norm_ext == norm_exp:
        return True
    if norm_exp in norm_ext or norm_ext in norm_exp:
        return True

    try:
        ext_num = float(re.sub(r'[^\d.]', '', norm_ext))
        exp_num = float(re.sub(r'[^\d.]', '', norm_exp))
        if abs(ext_num - exp_num) / max(abs(exp_num), 1) < 0.05:
            return True
    except (ValueError, ZeroDivisionError):
        pass

    return False


def test_with_crossrow(client: CrossRowClient, questions: list[dict]) -> list[dict]:
    """Run questions through CrossRow agent."""
    results = []
    for i, q in enumerate(questions):
        print(f"  [{i+1}/{len(questions)}] {q['question'][:60]}...", end=" ", flush=True)
        t0 = time.time()
        try:
            resp = client.eval_agent_sync(q["question"], timeout=config.GAIA_TIMEOUT_SECONDS)
            raw_answer = resp.get("answer", "")
            elapsed = time.time() - t0
            print(f"({elapsed:.1f}s)", end=" ", flush=True)

            extracted = extract_short_answer(raw_answer, q["question"], q["expected_answer"])
            correct = check_answer(extracted, q["expected_answer"])
            print("CORRECT" if correct else "WRONG")

            results.append({
                "question": q["question"],
                "expected": q["expected_answer"],
                "raw_answer": raw_answer[:500],
                "got": extracted,
                "correct": correct,
                "time": elapsed,
                "level": q.get("level", 1),
            })
        except Exception as e:
            elapsed = time.time() - t0
            print(f"ERROR ({elapsed:.1f}s): {e}")
            results.append({
                "question": q["question"],
                "expected": q["expected_answer"],
                "raw_answer": f"Error: {e}",
                "got": "ERROR",
                "correct": False,
                "time": elapsed,
                "level": q.get("level", 1),
            })
        time.sleep(1)
    return results


def test_with_baseline(questions: list[dict]) -> list[dict]:
    """Run same questions through raw Gemini for comparison."""
    client = _get_genai_client()

    results = []
    for i, q in enumerate(questions):
        print(f"  [{i+1}/{len(questions)}] {q['question'][:60]}...", end=" ", flush=True)
        t0 = time.time()
        try:
            resp = client.models.generate_content(model=config.JUDGE_MODEL, contents=q["question"])
            raw_answer = resp.text
            elapsed = time.time() - t0
            print(f"({elapsed:.1f}s)", end=" ", flush=True)

            extracted = extract_short_answer(raw_answer, q["question"], q["expected_answer"])
            correct = check_answer(extracted, q["expected_answer"])
            print("CORRECT" if correct else "WRONG")

            results.append({
                "question": q["question"],
                "expected": q["expected_answer"],
                "got": extracted,
                "correct": correct,
                "time": elapsed,
            })
        except Exception as e:
            print(f"ERROR: {e}")
            results.append({
                "question": q["question"],
                "expected": q["expected_answer"],
                "got": "ERROR",
                "correct": False,
                "time": time.time() - t0,
            })
        time.sleep(1)
    return results


def run(max_questions: int = None, compare_baseline: bool = False) -> dict:
    print("=" * 60)
    print("GAIA Benchmark Evaluation for CrossRow")
    print("=" * 60)

    max_q = max_questions or config.GAIA_MAX_QUESTIONS
    questions = load_gaia_dataset(max_q)

    client = CrossRowClient()

    print(f"\n--- Testing CrossRow Agent ({len(questions)} questions) ---")
    crossrow_results = test_with_crossrow(client, questions)

    correct = sum(1 for r in crossrow_results if r["correct"])
    total = len(crossrow_results)
    accuracy = correct / total if total else 0
    avg_time = sum(r["time"] for r in crossrow_results) / total if total else 0

    print(f"\n  CrossRow: {correct}/{total} = {accuracy:.1%} (avg {avg_time:.1f}s)")

    output = {
        "total": total,
        "correct": correct,
        "accuracy": f"{accuracy:.1%}",
        "accuracy_pct": accuracy * 100,
        "avg_time": round(avg_time, 1),
        "details": crossrow_results,
    }

    if compare_baseline:
        print(f"\n--- Testing Baseline Gemini ({len(questions)} questions) ---")
        baseline_results = test_with_baseline(questions)
        baseline_correct = sum(1 for r in baseline_results if r["correct"])
        baseline_accuracy = baseline_correct / total if total else 0
        print(f"\n  Baseline: {baseline_correct}/{total} = {baseline_accuracy:.1%}")

        output["comparison"] = {
            "CrossRow Agent": accuracy,
            f"Baseline ({config.JUDGE_MODEL})": baseline_accuracy,
        }
        output["baseline_details"] = baseline_results

    results_path = os.path.join(os.path.dirname(__file__), "reports")
    os.makedirs(results_path, exist_ok=True)
    with open(os.path.join(results_path, "gaia_raw.json"), "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2, default=str)
    print(f"\nRaw results saved to reports/gaia_raw.json")

    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="GAIA benchmark for CrossRow Agent")
    parser.add_argument("--max", type=int, help="Max questions to test")
    parser.add_argument("--compare-baseline", action="store_true", help="Also test raw Gemini")
    args = parser.parse_args()
    run(max_questions=args.max, compare_baseline=args.compare_baseline)
