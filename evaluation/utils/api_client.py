"""
CrossRow API client for evaluation.
Handles auth, SSE parsing, and all eval endpoint calls.
"""
import json
import time
import requests
import sseclient
from typing import Optional

import config


class CrossRowClient:
    def __init__(self, base_url: str = None, token: str = None):
        self.base_url = (base_url or config.API_BASE_URL).rstrip("/")
        self.token = token
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    def login(self, username: str = None, password: str = None) -> str:
        resp = self.session.post(
            f"{self.base_url}/auth/login",
            json={
                "username": username or config.AUTH_USERNAME,
                "password": password or config.AUTH_PASSWORD,
            },
        )
        resp.raise_for_status()
        data = resp.json()
        self.token = data.get("token") or data.get("data", {}).get("token", "")
        self.session.headers["Authorization"] = f"Bearer {self.token}"
        return self.token

    # ---- Eval endpoints (auth-free) ----

    def eval_rag(self, domain: str, question: str) -> dict:
        resp = self.session.get(
            f"{self.base_url}/eval/rag",
            params={"domain": domain, "question": question},
            timeout=60,
        )
        resp.raise_for_status()
        return resp.json()

    def eval_rag_batch(self, items: list[dict]) -> list[dict]:
        resp = self.session.post(
            f"{self.base_url}/eval/rag/batch",
            json=items,
            timeout=300,
        )
        resp.raise_for_status()
        return resp.json()

    def eval_routing(self, question: str) -> dict:
        resp = self.session.get(
            f"{self.base_url}/eval/routing",
            params={"question": question},
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()

    def eval_routing_batch(self, questions: list[str]) -> list[dict]:
        resp = self.session.post(
            f"{self.base_url}/eval/routing/batch",
            json=questions,
            timeout=120,
        )
        resp.raise_for_status()
        return resp.json()

    def eval_agent_sync(self, message: str, timeout: int = 120) -> dict:
        resp = self.session.get(
            f"{self.base_url}/eval/agent/sync",
            params={"message": message},
            timeout=timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def eval_expert_sync(self, message: str, timeout: int = 120) -> dict:
        """Call expert sync endpoint (auth-free) for quality evaluation."""
        resp = self.session.get(
            f"{self.base_url}/eval/expert/sync",
            params={"message": message},
            timeout=timeout,
        )
        resp.raise_for_status()
        return resp.json()

    # ---- SSE endpoint (for expert chat) ----

    def expert_chat_sse(self, message: str, chat_id: str, user_id: str) -> str:
        """Call expert SSE endpoint and collect full response text."""
        resp = self.session.post(
            f"{self.base_url}/crossrow/expert/chat",
            json={"message": message, "chatId": chat_id, "userId": user_id, "media": []},
            headers=self._headers(),
            stream=True,
            timeout=120,
        )
        resp.raise_for_status()
        client = sseclient.SSEClient(resp)
        text_parts = []
        for event in client.events():
            if event.event in ("message", "step"):
                text_parts.append(event.data)
        return "".join(text_parts)

    def agent_chat_sse(self, message: str, chat_id: str, user_id: str) -> str:
        """Call agent SSE endpoint and collect full response text."""
        resp = self.session.get(
            f"{self.base_url}/crossrow/agent/chat",
            params={
                "message": message,
                "chatId": chat_id,
                "userId": user_id,
                "enableReview": "false",
            },
            headers=self._headers(),
            stream=True,
            timeout=config.GAIA_TIMEOUT_SECONDS,
        )
        resp.raise_for_status()
        client = sseclient.SSEClient(resp)
        text_parts = []
        for event in client.events():
            if event.event in ("message", "step"):
                text_parts.append(event.data)
        return "".join(text_parts)

    def simple_chat_sync(self, message: str, chat_id: str, user_id: str) -> str:
        resp = self.session.get(
            f"{self.base_url}/crossrow/chat/simple/sync",
            params={"message": message, "chatId": chat_id, "userId": user_id},
            headers=self._headers(),
            timeout=60,
        )
        resp.raise_for_status()
        return resp.text
