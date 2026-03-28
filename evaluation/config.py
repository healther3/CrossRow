"""
CrossRow Evaluation Configuration.
Copy this file to config_local.py and fill in your actual values.
"""
import os

# ---------- CrossRow Backend ----------
API_BASE_URL = os.getenv("CROSSROW_API_URL", "http://localhost:8123/api")
# Auth credentials (used to obtain JWT if eval endpoints require auth)
AUTH_USERNAME = os.getenv("CROSSROW_USER", "eval-user")
AUTH_PASSWORD = os.getenv("CROSSROW_PASS", "eval-pass")

# ---------- LLM Judge (Gemini) ----------
# Used by RAGAS internally and by judge_eval.py
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "")
JUDGE_MODEL = os.getenv("JUDGE_MODEL", "gemini-2.5-flash")

# ---------- GAIA ----------
GAIA_DATASET = "Intelligent-Internet/GAIA-Subset-Benchmark"
GAIA_MAX_QUESTIONS = int(os.getenv("GAIA_MAX_QUESTIONS", "20"))
GAIA_TIMEOUT_SECONDS = 120

# ---------- Output ----------
REPORT_DIR = os.path.join(os.path.dirname(__file__), "reports")

# ---------- Convenience ----------
try:
    from config_local import *  # noqa: F401,F403
except ImportError:
    pass
