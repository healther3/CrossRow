import React, { useState, useEffect } from 'react';
import { ChevronDown, ChevronRight, Wrench, Terminal, Loader2, CheckCircle, XCircle, ShieldAlert, ShieldCheck } from 'lucide-react';
// 专门用来渲染单个 Tool Call 并管理伪加载进度的子组件
// 专门用来渲染单个 Tool Call 并管理伪加载进度的子组件
const ToolCallItem = ({ tool }) => {
    const [progress, setProgress] = useState(0);
    const isPending = tool.status === 'pending';
    const isError = tool.status === 'error';

    // 伪加载进度条逻辑
    useEffect(() => {
        let interval;
        if (isPending) {
            setProgress(0);
            interval = setInterval(() => {
                // 随机增加进度，最高卡在 99%
                setProgress(p => (p < 90 ? p + Math.floor(Math.random() * 15) + 1 : 99));
            }, 300);
        } else {
            setProgress(100); // 一旦变成 success/error，瞬间填满 100%
        }
        return () => clearInterval(interval);
    }, [isPending]);

    return (
        <div className={`p-3 rounded-lg border space-y-2 mt-2 group transition-all duration-300 ${
            isPending ? 'bg-blue-950/20 border-blue-500/30 shadow-[0_0_15px_rgba(59,130,246,0.1)]' :
                isError ? 'bg-red-950/20 border-red-500/50' :
                    'bg-green-950/20 border-green-500/30' // 【修改点1】：颜色变浅，完全对齐 Review 的色值和透明度
        }`}>
            {/* 1. 头部：只保留工具名称与执行进度 */}
            <div className="flex items-center justify-between">
                <div className={`font-bold flex items-center gap-2 text-sm ${
                    isPending ? 'text-blue-400' : 'text-slate-400'
                }`}>
                    {isPending ? <Loader2 size={16} className="animate-spin text-blue-400" /> : <Terminal size={14} className="text-slate-500" />}
                    <span className="tracking-wider uppercase">
                        {tool.toolName}
                    </span>
                </div>

                <div className="text-xs flex items-center gap-3">
                    {isPending && <span className="text-blue-400 font-mono tracking-widest">{progress}%</span>}
                    {/* 移除了原本右上角的 success 小标签 */}
                </div>
            </div>

            {/* 2. 调用参数 (对齐 Review 的 pl-6 缩进样式) */}
            <div className={`text-xs font-mono pl-6 ${isPending ? 'text-blue-400/70' : 'text-slate-400'}`}>
                <span className="opacity-60">Args: </span> {tool.arguments}
            </div>

            {/* 3. 结果展示 (加上左侧缩进，与上下文排版对齐) */}
            {!isPending && tool.result && (
                <div className={`text-xs mt-3 ml-6 p-3 rounded-md font-mono border ${
                    isError ? 'bg-red-950/40 border-red-500/20 text-red-400/90' : 'bg-green-950/10 border-green-500/20 text-green-400/80 line-clamp-4 group-hover:line-clamp-none cursor-pointer'
                }`}>
                    {tool.result}
                </div>
            )}

            {/* 4. 【修改点2】底部状态栏：结果渲染完后，在最下方显示大号的 SUCCESS / FAILED */}
            {!isPending && (
                <div className={`flex items-center justify-between mt-3 pt-3 border-t pl-2 ${
                    isError ? 'border-red-500/20' : 'border-green-500/20'
                }`}>
                    <div className={`flex items-center gap-2 font-bold text-sm ${
                        isError ? 'text-red-400' : 'text-green-400'
                    }`}>
                        {isError ? <XCircle size={16} /> : <CheckCircle size={16} />}
                        <span className="tracking-wider">
                            {isError ? '[SUPERVISOR]: APPROVED' : '[SUPERVISOR]: REJECTED(ERROR)'}
                        </span>
                    </div>
                    {tool.elapsedMs && <span className="text-slate-500 text-xs font-mono">{tool.elapsedMs}ms</span>}
                </div>
            )}
        </div>
    );
};


export default function AgentTracePanel({ steps, tokenUsage }) {
    // 默认展开，因为有动画，用户看着会很爽
    const [expanded, setExpanded] = useState(true);

    if (!steps || steps.length === 0) return null;

    return (
        <div className="mb-4 bg-slate-900/60 border border-slate-700/50 rounded-xl overflow-hidden font-sans text-sm shadow-lg backdrop-blur-sm animate-fade-in">
            {/* 面板头部 */}
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full flex items-center justify-between p-3 px-4 bg-slate-800/80 hover:bg-slate-700/80 transition-colors text-slate-300 border-b border-transparent hover:border-slate-600 cursor-pointer"
            >
                <span className="flex items-center gap-2 font-bold tracking-wide">
                    {expanded ? <ChevronDown size={16} className="text-cyan-400"/> : <ChevronRight size={16} className="text-cyan-400"/>}
                    <Terminal size={14} className="text-slate-400" />
                    Agent Trace Logs ({steps.length} steps)
                </span>
                {tokenUsage && (
                    <span className="text-xs text-green-400/80 bg-green-900/20 px-2 py-1 rounded border border-green-500/20 font-mono">
                        Tokens: {tokenUsage.totalTokens}
                    </span>
                )}
            </button>

            {/* 面板内容 */}
            {expanded && (
                <div className="p-4 space-y-6 text-slate-300">
                    {steps.map((step, idx) => (
                        <div key={idx} className="relative space-y-3 border-l-2 border-slate-600 pl-4 py-1">
                            {/* 常规 Step 的圆点 */}
                            {step.stepNumber && step.stepType !== 'review_pending' && step.stepType !== 'review_result' && (
                                <>
                                    <div className="absolute -left-[5px] top-2 w-2 h-2 rounded-full bg-cyan-500 shadow-[0_0_8px_rgba(6,182,212,0.8)]"></div>
                                    <div className="text-xs text-slate-500 uppercase tracking-widest font-bold">Step {step.stepNumber}</div>
                                </>
                            )}

                            {/* --- 极其显眼的思考过程展示 --- */}
                            {step.thinking && (
                                <div className="bg-slate-800/50 p-3 rounded-lg border border-slate-700/50 text-slate-300 font-sans text-sm leading-relaxed shadow-inner">
                                    <span className="text-cyan-400 font-mono font-bold mr-2 tracking-wider select-none">
                                        [DECISION]
                                    </span>
                                    {step.thinking}
                                </div>
                            )}

                            {/* 工具调用列表 */}
                            {step.toolCalls && step.toolCalls.map((tool, tIdx) => (
                                <ToolCallItem key={tIdx} tool={tool} />
                            ))}

                            {/* 渲染 Review 结果 */}
                            {step.stepType === 'review_result' && (
                                <div className={`p-3 rounded-lg border mt-2 group transition-all duration-300 ${
                                    step.approved
                                        ? 'bg-green-950/20 border-green-500/30'
                                        : 'bg-red-950/20 border-red-500/30 shadow-[0_0_15px_rgba(239,68,68,0.1)]'
                                }`}>
                                    {/* 头部状态：APPROVED / REJECTED */}
                                    <div className={`font-bold flex items-center gap-2 text-sm ${
                                        step.approved ? 'text-green-400' : 'text-red-400'
                                    }`}>
                                        {step.approved ? <CheckCircle size={16} /> : <XCircle size={16} />}
                                        <span className="tracking-wider uppercase">
                            [SUPERVISOR]: {step.approved ? 'APPROVED' : 'REJECTED'}
                        </span>
                                    </div>

                                    {/* 核心修复：独立渲染 reason 模块，不再受限于 approved 状态 */}
                                    {step.reason && (
                                        <div className={`text-xs mt-3 ml-6 p-3 rounded-md font-mono border ${
                                            step.approved
                                                ? 'bg-green-950/10 border-green-500/20 text-green-400/80'
                                                : 'bg-red-950/20 border-red-500/30 text-red-400/90'
                                        }`}>
                                            <span className="opacity-60">Reason: </span> {step.reason}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}