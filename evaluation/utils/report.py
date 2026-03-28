"""
Report generation utilities.
Produces Markdown reports from evaluation results.
"""
import os
import json
from datetime import datetime
from tabulate import tabulate

import config


def ensure_report_dir():
    os.makedirs(config.REPORT_DIR, exist_ok=True)


def generate_report(results: dict, filename: str = None) -> str:
    """
    Generate a combined Markdown report from all evaluation results.

    results dict may contain keys: "ragas", "gaia", "routing", "quality"
    """
    ensure_report_dir()
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = filename or f"eval_report_{ts}.md"
    path = os.path.join(config.REPORT_DIR, filename)

    lines = [
        f"# CrossRow 评测报告",
        f"",
        f"**生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"**API 地址**: `{config.API_BASE_URL}`",
        f"",
        f"---",
        f"",
    ]

    # ---- RAGAS ----
    if "ragas" in results:
        r = results["ragas"]
        lines.append("## 1. RAG 质量评估 (RAGAS)")
        lines.append("")
        if "scores" in r:
            table = [[k, f"{v:.4f}"] for k, v in r["scores"].items()]
            lines.append(tabulate(table, headers=["指标", "分数"], tablefmt="github"))
        lines.append("")
        if "per_domain" in r:
            for domain, scores in r["per_domain"].items():
                lines.append(f"### {domain.capitalize()}")
                table = [[k, f"{v:.4f}"] for k, v in scores.items()]
                lines.append(tabulate(table, headers=["指标", "分数"], tablefmt="github"))
                lines.append("")
        if "details" in r:
            lines.append("<details><summary>详细结果</summary>\n")
            lines.append("```json")
            lines.append(json.dumps(r["details"], ensure_ascii=False, indent=2))
            lines.append("```\n</details>\n")
        lines.append("---\n")

    # ---- GAIA ----
    if "gaia" in results:
        g = results["gaia"]
        lines.append("## 2. GAIA Benchmark (Web-Search 子集)")
        lines.append("")
        lines.append(f"- **总题数**: {g.get('total', 'N/A')}")
        lines.append(f"- **正确数**: {g.get('correct', 'N/A')}")
        lines.append(f"- **准确率**: {g.get('accuracy', 'N/A')}")
        lines.append(f"- **平均耗时**: {g.get('avg_time', 'N/A')}s")
        lines.append("")
        if "comparison" in g:
            lines.append("### CrossRow Agent vs Baseline")
            table = [[k, f"{v:.1%}"] for k, v in g["comparison"].items()]
            lines.append(tabulate(table, headers=["系统", "准确率"], tablefmt="github"))
            lines.append("")
        if "details" in g:
            lines.append("<details><summary>逐题结果</summary>\n")
            headers = ["#", "Question (截取)", "Expected", "Got", "Correct"]
            table = [
                [i + 1, d["question"][:60], d["expected"], d["got"][:60], "Y" if d["correct"] else "N"]
                for i, d in enumerate(g["details"])
            ]
            lines.append(tabulate(table, headers=headers, tablefmt="github"))
            lines.append("\n</details>\n")
        lines.append("---\n")

    # ---- Routing ----
    if "routing" in results:
        rt = results["routing"]
        lines.append("## 3. 路由准确性 (LLM-as-Judge)")
        lines.append("")
        lines.append(f"- **总题数**: {rt.get('total', 'N/A')}")
        lines.append(f"- **准确率**: {rt.get('accuracy', 'N/A')}")
        lines.append("")
        if "per_domain" in rt:
            table = [[d, f"{s:.1%}"] for d, s in rt["per_domain"].items()]
            lines.append(tabulate(table, headers=["领域", "准确率"], tablefmt="github"))
            lines.append("")
        if "boundary" in rt:
            lines.append(f"- **边界问题准确率**: {rt['boundary']}")
        lines.append("---\n")

    # ---- Quality ----
    if "quality" in results:
        q = results["quality"]
        lines.append("## 4. 响应质量 (LLM-as-Judge)")
        lines.append("")
        if "overall" in q:
            lines.append(f"**综合得分: {q['overall']:.1f} / 100**\n")
        if "dimensions" in q:
            table = [[k, f"{v:.1f}"] for k, v in q["dimensions"].items()]
            lines.append(tabulate(table, headers=["维度", "得分"], tablefmt="github"))
            lines.append("")
        if "details" in q:
            lines.append("<details><summary>逐题评分</summary>\n")
            lines.append("```json")
            lines.append(json.dumps(q["details"], ensure_ascii=False, indent=2))
            lines.append("```\n</details>\n")
        lines.append("---\n")

    # ---- Summary ----
    lines.append("## 综合评价\n")
    grade = _compute_grade(results)
    lines.append(f"| 维度 | 得分 | 等级 |")
    lines.append(f"|------|------|------|")
    for dim, info in grade.items():
        lines.append(f"| {dim} | {info['score']} | {info['grade']} |")
    lines.append("")

    content = "\n".join(lines)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"\n[Report] Saved to {path}")
    return path


def _compute_grade(results: dict) -> dict:
    grade_map = {}

    def letter(score):
        if score >= 90: return "A (优秀)"
        if score >= 80: return "B (良好)"
        if score >= 60: return "C (及格)"
        return "D (不及格)"

    if "ragas" in results and "scores" in results["ragas"]:
        avg = sum(results["ragas"]["scores"].values()) / max(len(results["ragas"]["scores"]), 1) * 100
        grade_map["RAG 质量"] = {"score": f"{avg:.1f}%", "grade": letter(avg)}

    if "gaia" in results:
        acc = results["gaia"].get("accuracy_pct", 0)
        grade_map["Agent 能力 (GAIA)"] = {"score": f"{acc:.1f}%", "grade": letter(acc)}

    if "routing" in results:
        acc = results["routing"].get("accuracy_pct", 0)
        grade_map["路由准确性"] = {"score": f"{acc:.1f}%", "grade": letter(acc)}

    if "quality" in results:
        score = results["quality"].get("overall", 0)
        grade_map["响应质量"] = {"score": f"{score:.1f}", "grade": letter(score)}

    return grade_map
