## SSE 接口规范

### 普通聊天接口 (Flux)
- `/crossrow/chat/simple/sse`
- `/crossrow/chat/auto-route/sse`
- `/crossrow/chat/model/sse`
- `/crossrow/chat/multimodal/sse`

**事件类型**:
| event | data 格式 | 说明 |
|-------|----------|------|
| `message` | `string` | 聊天内容片段 |
| `session_title` | `{"title":"xxx"}` | 会话标题（仅新会话） |

---

### Agent 接口 (SseEmitter)
- `/crossrow/agent/chat`
- `/crossrow/expert/chat`
- `/crossrow/agent/chat/multimodal`

**事件类型**:
| event | data 格式 | 说明 |
|-------|----------|------|
| `message` | `string` | 聊天内容片段 |
| `session_title` | `{"title":"xxx"}` | 会话标题 |
| `tool_call` | `{"name":"xxx","args":{}}` | 工具调用（如有） |
| `thinking` | `string` | 思考过程（如有） |
| `done` | `{}` | 流结束标志 |