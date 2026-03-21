import React, { useState, useEffect, useRef } from 'react';
import { useSettings } from '../context/SettingsContext';
import { useTransition } from '../context/TransitionContext';
import { useAuth } from '../context/AuthContext';
import { Menu, Image as ImageIcon, X } from 'lucide-react';

import SessionSidebar from '../components/SessionSidebar';
import AgentTracePanel from '../components/AgentTracePanel';
import ImageModal from '../components/ImageModal';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useNavigate } from 'react-router-dom';

const TYPE_SPEED_MS = 20;
const CHARS_PER_TICK = 3;
const MODES = ['PREFERRED', 'AUTO', 'AGENT', 'EXPERT'];

export default function ChatPage() {
    const { navigateWithTransition } = useTransition();
    const bottomRef = useRef(null);
    const [input, setInput] = useState('');
    const [isSending, setIsSending] = useState(false);
    const [modalImage, setModalImage] = useState(null);
    const [agentQuestion, setAgentQuestion] = useState(null);
    const [dynamicTitleUpdate, setDynamicTitleUpdate] = useState(null);

    const [chatModeIndex, setChatModeIndex] = useState(0);
    const chatMode = MODES[chatModeIndex];
    const [preferredModel, setPreferredModel] = useState('STANDARD');

    const [pendingImages, setPendingImages] = useState([]); // 存放准备发送的图片文件
    const fileInputRef = useRef(null); // 指向隐藏的文件选择框

    const [isSidebarOpen, setIsSidebarOpen] = useState(true);
    const [chatId, setChatId] = useState(null);

    const { bgConfig } = useSettings();
    const { userId, token } = useAuth();

    const [isBlurEnabled] = useState(() => localStorage.getItem('crossrow_bg_blur') !== 'false');
    const [isReviewEnabled, setIsReviewEnabled] = useState(() => localStorage.getItem('crossrow_enable_review') === 'true');
    const [messages, setMessages] = useState([]);

    const targetTextRef = useRef("");
    const displayedIndexRef = useRef(0);
    const currentMsgIdRef = useRef(null);
    const eventSourceRef = useRef(null);
    const stepContentsRef = useRef(new Map()); // 用于拼接流式数据

    const [bgUrl, setBgUrl] = useState(null);
    const baseUrlAPI = import.meta.env.PROD ? '' : 'http://localhost:8123';
    // 获取偏好模型
    useEffect(() => {
        const fetchPref = async () => {
            if(!token) return;
            try {
                const res = await fetch(`${baseUrlAPI}/api/user/model-preference`, { headers: {'Authorization': `Bearer ${token}`} });
                if(res.ok) {
                    const data = await res.json();
                    if(data.currentModel) setPreferredModel(data.currentModel);
                }
            } catch(e) {}
        };
        fetchPref();
    }, [token, baseUrlAPI]);

    // 加载历史记录
    const loadChatHistory = async (targetChatId) => {
        try {
            const response = await fetch(`${baseUrlAPI}/api/sessions/${targetChatId}/history`, { headers: { 'Authorization': `Bearer ${token}` } });
            if (response.ok) {
                const historyData = await response.json();
                if (!historyData || historyData.length === 0) {
                    setMessages([
                        { id: 1, text: "Wait... where am I?", sender: "user" },
                        { id: 2, text: "You have arrived. This is the boundary of consciousness.", sender: "ai" }
                    ]);
                    return;
                }
                const formattedMessages = historyData.map((item, index) => ({
                    id: Date.now() + index,
                    text: item.content,
                    sender: item.role === 'user' ? 'user' : 'ai',
                    steps: [],
                    tokenUsage: null
                }));
                setMessages(formattedMessages);
            } else {
                setMessages([{ id: Date.now(), text: "[System]: Error loading memory.", sender: "system" }]);
            }
        } catch (error) {
            setMessages([{ id: Date.now(), text: "[System]: Network anomaly detected.", sender: "system" }]);
        }
    };

    // 侧边栏回调
    const handleSessionSelect = (selectedId) => {
        const actualCurrentId = chatId?.id || chatId;
        if (actualCurrentId === selectedId) return;

        // 【核心修复 1】：安全清理旧请求，兼容 EventSource 和 AbortController
        if (eventSourceRef.current) {
            if (typeof eventSourceRef.current.abort === 'function') {
                eventSourceRef.current.abort();
            } else if (typeof eventSourceRef.current.close === 'function') {
                eventSourceRef.current.close();
            }
            eventSourceRef.current = null;
        }

        setIsSending(false); setAgentQuestion(null); currentMsgIdRef.current = null; targetTextRef.current = ""; displayedIndexRef.current = 0; stepContentsRef.current.clear();

        setChatId(selectedId);
        setMessages([{ id: Date.now(), text: "[System]: Accessing memory archives...", sender: "system" }]);
        loadChatHistory(selectedId);
    };

    const handleNewSession = (newId) => {
        // 【核心修复 1】：同样加入安全清理机制
        if (eventSourceRef.current) {
            if (typeof eventSourceRef.current.abort === 'function') {
                eventSourceRef.current.abort();
            } else if (typeof eventSourceRef.current.close === 'function') {
                eventSourceRef.current.close();
            }
            eventSourceRef.current = null;
        }

        setIsSending(false); setAgentQuestion(null); currentMsgIdRef.current = null; targetTextRef.current = ""; displayedIndexRef.current = 0; stepContentsRef.current.clear();

        setChatId(newId);
        setMessages([
            { id: 1, text: "Wait... where am I?", sender: "user" },
            { id: 2, text: "You have arrived. This is the boundary of consciousness.", sender: "ai" }
        ]);
    };

    // 背景逻辑
    // ==========================================
    // 背景逻辑 (修复双重请求)
    // ==========================================
    // 1. 新增一个 Ref，用来记录上一次成功发起的请求特征
    const lastBgRequestRef = useRef('');

    useEffect(() => {
        if (!token || !userId) return;

        // 2. 生成当前请求的“唯一指纹” (模式 + 坐标)
        const currentConfigKey = `${bgConfig?.mode}-${bgConfig?.coords?.lat || ''}-${bgConfig?.coords?.lng || ''}`;

        // 3. 终极拦截：如果当前指纹和上次一样，说明是 React 的多余渲染，直接拦截！
        if (lastBgRequestRef.current === currentConfigKey) {
            return;
        }

        // 记录这次的指纹
        lastBgRequestRef.current = currentConfigKey;

        const fetchBackgroundUrl = async () => {
            try {
                let mode = bgConfig?.mode || 'DEFAULT';
                if (mode === 'USER' && (!bgConfig?.coords || !bgConfig.coords.lat)) mode = 'RANDOM';
                if (mode === 'CUSTOM') {
                    const response = await fetch(`${baseUrlAPI}/api/user/background`, { headers: { 'Authorization': `Bearer ${token}` } });
                    if (response.ok) setBgUrl(await response.text());
                    return;
                }

                const baseUrl = `${baseUrlAPI}/api/crossrow/image/background`;
                const params = new URLSearchParams();
                params.append("userId", userId);
                params.append("mode", mode);
                if (mode === 'USER' && bgConfig?.coords) {
                    params.append("lat", bgConfig.coords.lat);
                    params.append("lng", bgConfig.coords.lng);
                }

                const response = await fetch(`${baseUrl}?${params.toString()}`, { headers: { 'Authorization': `Bearer ${token}` } });
                if (response.ok) {
                    const urlString = await response.text();
                    if (urlString) {
                        if (urlString.startsWith('http')) {
                            setBgUrl(urlString);
                        } else if (urlString.startsWith('/')) {
                            setBgUrl(`${baseUrlAPI}${urlString}`);
                        }
                    }
                }
            } catch (error) {
                console.error("Failed to fetch background:", error);
                // 如果请求失败了，清空锁，允许下次重试
                lastBgRequestRef.current = '';
            }
        };
        fetchBackgroundUrl();
    }, [bgConfig, userId, token, baseUrlAPI]);

    useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages]);
    useEffect(() => { return () => { if (eventSourceRef.current) eventSourceRef.current.close(); }; }, []);

    // 打字机
    useEffect(() => {
        const timer = setInterval(() => {
            if (currentMsgIdRef.current && displayedIndexRef.current < targetTextRef.current.length) {
                const nextIndex = Math.min(displayedIndexRef.current + CHARS_PER_TICK, targetTextRef.current.length);
                const displayText = targetTextRef.current.slice(0, nextIndex);
                setMessages(prev => {
                    const newMessages = [...prev];
                    const targetIndex = newMessages.findIndex(m => m.id === currentMsgIdRef.current);
                    if (targetIndex !== -1) { newMessages[targetIndex] = { ...newMessages[targetIndex], text: displayText }; }
                    return newMessages;
                });
                displayedIndexRef.current = nextIndex;
            }
        }, TYPE_SPEED_MS);
        return () => clearInterval(timer);
    }, []);

    // 【新增】：处理本地图片选择
    const handleImageSelect = (e) => {
        if (e.target.files && e.target.files.length > 0) {
            const filesArray = Array.from(e.target.files);
            setPendingImages(prev => [...prev, ...filesArray]);
        }
        // 重置 input，允许重复选择同一张图
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    // 【新增】：处理 Ctrl+V 剪贴板粘贴图片
    const handlePaste = (e) => {
        const items = e.clipboardData?.items;
        if (!items) return;

        const newImages = [];
        for (let i = 0; i < items.length; i++) {
            // 如果粘贴的是图片
            if (items[i].type.startsWith('image/')) {
                const file = items[i].getAsFile();
                if (file) newImages.push(file);
            }
        }

        if (newImages.length > 0) {
            setPendingImages(prev => [...prev, ...newImages]);
            // 如果纯粹是为了粘图片，可以选择不阻止默认事件，这样如果同时粘贴了图文，文字也能进输入框
        }
    };

    // 【新增】：移除不想发送的图片
    const removePendingImage = (index) => {
        setPendingImages(prev => prev.filter((_, i) => i !== index));
    };

    // 处理图片上传：调用后端的内部上传接口，换取 GCS URL
    async function uploadImages(files, userId, token) {
        const formData = new FormData();

        // 支持单个文件或多个文件
        const fileArray = Array.isArray(files) ? files : [files];
        fileArray.forEach(file => {
            formData.append('files', file);
        });
        formData.append('userId', userId);

        try{
        const uploadRes = await fetch(`${baseUrlAPI}/api/media/image/upload`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`
            },
            body: formData
        });

        if (!uploadRes.ok) {
            throw new Error(`Failed to upload image: ${uploadRes.status}`);
        }

        // 返回格式: [{ type: "image", mimeType: "image/png", url: "https://storage.googleapis.com/..." }]
        const mediaList = await uploadRes.json();
        return mediaList;

        } catch (error) {
            console.error("Image upload error:", error);
            throw error; // 抛出错误，让 handleSend 里的 try-catch 捕获并停止发送
        }
    }
    // 核心发送逻辑
/// 核心发送逻辑
    const handleSend = async (e) => {
        // 【核心修改 1】：判断是否有内容（有文字 OR 有图片，都算有内容）
        const hasContent = input.trim().length > 0 || pendingImages.length > 0;

        // 将拦截条件中的 input.trim() 替换为 hasContent
        if (e.key === 'Enter' && hasContent && !isSending && !e.nativeEvent.isComposing) {
            if (!chatId) return;

            setIsSending(true); setAgentQuestion(null);
            const userText = input.trim(); setInput('');

            const imagesToUpload = [...pendingImages];
            setPendingImages([]);

            const userMsgId = Date.now();
            const aiMsgId = userMsgId + 1;

            currentMsgIdRef.current = aiMsgId; targetTextRef.current = ""; displayedIndexRef.current = 0; stepContentsRef.current.clear();

            // 清理上一个请求
            if (eventSourceRef.current && typeof eventSourceRef.current.abort === 'function') {
                eventSourceRef.current.abort();
            }

            // 【核心修改 2】：UI 体验优化
            // 如果用户只发了图片没打字，前端聊天气泡里总得显示点什么，不然是个空气泡
            const displayUserText = userText || (imagesToUpload.length > 0 ? "[Attached Visual Asset]" : "");

            setMessages(prev => [
                ...prev,
                { id: userMsgId, text: displayUserText, sender: "user" }, // 使用优化后的显示文本
                {
                    id: aiMsgId,
                    text: "",
                    sender: "ai",
                    steps: [],
                    tokenUsage: null,
                    systemLog: imagesToUpload.length > 0 ? `[System Log]: Uploading ${imagesToUpload.length} visual asset(s) to secure storage...` : null
                }
            ]);

            const actualId = chatId?.id || chatId;
            let finalUrl = '';

            // ... 下面的 try { ... } 逻辑保持完全不变

            try {
                // ==========================================
                // 【阶段 1】：调用你的批量上传函数
                // ==========================================
                let mediaPayload = []; // 改用 let，方便直接赋值

                if (imagesToUpload.length > 0) {
                    // 直接一次性把所有图片发给你的上传函数
                    mediaPayload = await uploadImages(imagesToUpload, userId, token);

                    // 图片上传完毕，清空 systemLog 中的处理提示
                    setMessages(prev => prev.map(msg => msg.id === aiMsgId ? { ...msg, systemLog: null } : msg));
                }

                // ==========================================
                // 【阶段 2】：URL 路由构建 (优化分流)
                // ==========================================

                if (chatMode === 'EXPERT') {
                    const preRes = await fetch(`${baseUrlAPI}/api/crossrow/expert/preview?message=${encodeURIComponent(userText)}`, { headers: { 'Authorization': `Bearer ${token}` } });
                    if(preRes.ok) {
                        const expertName = await preRes.text();
                        setMessages(prev => prev.map(msg => msg.id === aiMsgId ? { ...msg, systemLog: `[System Log]: Routing query to specialist [ ${expertName.toUpperCase()} ]` } : msg));
                    }
                    finalUrl = `${baseUrlAPI}/api/crossrow/expert/chat?enableReview=${isReviewEnabled}&maxReviewRetries=2`;
                } else if (chatMode === 'AUTO') {
                    const decisionRes = await fetch(`${baseUrlAPI}/api/crossrow/route/decision?message=${encodeURIComponent(userText)}`, { headers: { 'Authorization': `Bearer ${token}` } });
                    if(decisionRes.ok) {
                        const decision = await decisionRes.json();
                        const model = decision.selectedModel || 'Optimal Framework';
                        const review = decision.review || {};
                        const traceLog = `[Auto-Selection Trace]: Category: [ ${(review.category || 'GENERAL').toUpperCase()} ] | Complexity: [ ${review.is_complex ? 'HIGH' : 'LOW'} ] | Analysis: ${review.reason || 'Task Evaluated'} | Deployed Core: [ ${model.toUpperCase()} ]`;
                        setMessages(prev => prev.map(msg => msg.id === aiMsgId ? { ...msg, systemLog: traceLog } : msg));
                    }
                    finalUrl = `${baseUrlAPI}/api/crossrow/chat/auto-route/sse`;
                } else if (chatMode === 'PREFERRED') {
                    // 统一使用多模态对话接口，后端会自动做降级处理
                    finalUrl = `${baseUrlAPI}/api/crossrow/chat/multimodal/sse`;
                } else {
                    // AGENT 模式：统一使用多模态对话接口，后端会自动做降级处理
                    finalUrl = `${baseUrlAPI}/api/crossrow/agent/chat/multimodal?enableReview=${isReviewEnabled}&maxReviewRetries=2`;
                }

                // ==========================================
                // 【阶段 3】：统一的事件处理函数抽象
                // ==========================================
                let chunkCounter = 0;
                const processedImageUrls = new Set();
                const processedQuestions = new Set();

                const processDataWithHiddenActions = (fullText) => {
                    let cleanedData = fullText;
                    const actionRegex = /<hidden_action\s+type=['"]show_image['"]\s+url=['"](.*?)['"]\s*\/>/gi;
                    let match;
                    while ((match = actionRegex.exec(cleanedData)) !== null) {
                        const gcsUrl = match[1];
                        if (!processedImageUrls.has(gcsUrl)) { processedImageUrls.add(gcsUrl); setTimeout(() => { setModalImage(gcsUrl); }, 800); }
                    }
                    cleanedData = cleanedData.replace(actionRegex, '');

                    const askRegex = /<hidden_action\s+type=['"]ask_human['"]\s+question=['"](.*?)['"]\s*\/>/gi;
                    let askMatch;
                    while ((askMatch = askRegex.exec(cleanedData)) !== null) {
                        const questionText = askMatch[1];
                        if (!processedQuestions.has(questionText)) { processedQuestions.add(questionText); setTimeout(() => { setAgentQuestion(questionText); }, 500); }
                    }
                    cleanedData = cleanedData.replace(askRegex, '');
                    cleanedData = cleanedData.replace('(Waiting for user input...)', '');
                    return cleanedData;
                };

                const handleRawData = (data) => {
                    let cleanedData = data;
                    if (cleanedData.startsWith('"') && cleanedData.endsWith('"')) { try { cleanedData = JSON.parse(cleanedData); } catch(e) {} }
                    stepContentsRef.current.set(chunkCounter++, cleanedData);
                    const fullRawText = Array.from(stepContentsRef.current.entries()).sort((a, b) => Number(a[0]) - Number(b[0])).map(([_, content]) => content).join("");
                    targetTextRef.current = processDataWithHiddenActions(fullRawText.replace(/\\n/g, '\n'));
                };

                const handleStep = (data) => {
                    try {
                        const dto = JSON.parse(data);
                        if (dto && (dto.stepType || dto.stepNumber !== undefined)) {
                            if (dto.toolCalls && Array.isArray(dto.toolCalls)) {
                                dto.toolCalls.forEach(tool => { if (tool.result && typeof tool.result === 'string') { tool.result = processDataWithHiddenActions(tool.result); } });
                            }
                            setMessages(prev => prev.map(msg => {
                                if (msg.id === aiMsgId) {
                                    const newMsg = { ...msg };
                                    if (dto.stepType === "final_answer") {
                                        targetTextRef.current = processDataWithHiddenActions(dto.finalAnswer || "");
                                        if (dto.tokenUsage) newMsg.tokenUsage = dto.tokenUsage;
                                    } else {
                                        const existingSteps = newMsg.steps || [];
                                        const stepIndex = existingSteps.findIndex(s => s.stepNumber === dto.stepNumber);
                                        if (stepIndex !== -1) {
                                            const updatedSteps = [...existingSteps]; updatedSteps[stepIndex] = dto; newMsg.steps = updatedSteps;
                                        } else { newMsg.steps = [...existingSteps, dto]; }
                                    }
                                    return newMsg;
                                }
                                return msg;
                            }));
                        } else { handleRawData(data); }
                    } catch (e) { handleRawData(data); }
                };

                const handleReview = (data) => {
                    try {
                        const dto = JSON.parse(data);
                        setMessages(prev => prev.map(msg => {
                            if (msg.id === aiMsgId) {
                                const newMsg = { ...msg };
                                const existingSteps = newMsg.steps || [];
                                const lastStepIndex = existingSteps.length - 1;
                                if (dto.stepType === "review_result" && lastStepIndex >= 0 && existingSteps[lastStepIndex].stepType === "review_pending") {
                                    const updatedSteps = [...existingSteps]; updatedSteps[lastStepIndex] = dto; newMsg.steps = updatedSteps;
                                } else { newMsg.steps = [...existingSteps, dto]; }
                                return newMsg;
                            }
                            return msg;
                        }));
                    } catch (e) { console.error(e); }
                };

                const handleTitle = (data) => {
                    try {
                        const titleData = JSON.parse(data);
                        // 优先使用后端传回的 chatId，如果不存在则回退到当前页面的 actualId
                        const targetId = titleData.chatId || actualId;
                        if (titleData && titleData.title) {
                            setDynamicTitleUpdate({ id: targetId, title: titleData.title });
                        }
                    } catch (e) { console.error("Title sync error:", e); }
                };

                // ==========================================
                // 【阶段 4】：统一使用 fetchEventSource 发起 POST
                // ==========================================
                const ctrl = new AbortController();
                eventSourceRef.current = ctrl;

                await fetchEventSource(finalUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    // 完美对应后端的 MultimodalChatRequestDTO 结构
                    body: JSON.stringify({
                        message: userText || (mediaPayload.length > 0 ? "Please analyze the attached image." : ""),
                        media: mediaPayload.length > 0 ? mediaPayload : null, // 如果没图，传 null 或 []，后端会自动降级
                        chatId: actualId,
                        userId: userId
                    }),
                    signal: ctrl.signal,
                    onmessage(ev) {
                        // 调试：打印每一个到达的事件，无论名称是什么
                        console.log(`[Raw SSE]: event=${ev.event || 'message'} data=${ev.data}`);

                        if (ev.event === 'step') handleStep(ev.data);
                        else if (ev.event === 'review') handleReview(ev.data);
                        else if (ev.event === 'session_title') {
                            handleTitle(ev.data);
                        }
                        else if (ev.event === 'complete') setIsSending(false);
                        else if (ev.event === 'error') {
                            try { const errDto = JSON.parse(ev.data); setMessages(prev => [...prev, { id: Date.now(), text: `[System Error]: ${errDto.message}`, sender: "system" }]); } catch(e){}
                            ctrl.abort(); setIsSending(false);
                        }
                        else {
                            // 兜底逻辑：如果是一个没有名字的 message 事件，但数据里包含了 title，说明是后端没传 event 名字
                            if (!ev.event || ev.event === 'message') {
                                try {
                                    const json = JSON.parse(ev.data);
                                    if (json && json.title && json.chatId) {
                                        console.log("[Detected Title in Message Event]");
                                        handleTitle(ev.data);
                                        return; // 处理完标题后直接返回，不要当做普通聊天文字处理
                                    }
                                } catch (e) {
                                    // 不是 JSON 或者不含标题，按原样处理
                                }
                            }
                            handleRawData(ev.data);
                        }
                    },
                    onclose() { setIsSending(false); },
                    onerror(err) { setIsSending(false); throw err; }
                });

            } catch (err) {
                setIsSending(false);
                console.error(err);
            }
        }
    };
    // UI 颜色计算
    let modeLabel = ''; let modeStyle = ''; let inputBorder = ''; let inputArrow = '';
    switch(chatMode) {
        case 'PREFERRED': modeLabel = `✧ ${preferredModel.toUpperCase()}`; modeStyle = 'border-slate-500/50 text-slate-400 hover:text-slate-200 hover:border-slate-400'; inputBorder = 'border-white/10'; inputArrow = 'text-white/50'; break;
        case 'AUTO': modeLabel = '⟳ AUTO SELECTION'; modeStyle = 'border-cyan-500 text-cyan-300 bg-cyan-500/20 shadow-[0_0_10px_rgba(6,182,212,0.4)]'; inputBorder = 'border-cyan-500/50'; inputArrow = 'text-cyan-400'; break;
        case 'AGENT': modeLabel = '◈ AGENT'; modeStyle = 'border-blue-500 text-blue-300 bg-blue-500/20 shadow-[0_0_10px_rgba(59,130,246,0.4)]'; inputBorder = 'border-blue-500/50'; inputArrow = 'text-blue-400'; break;
        case 'EXPERT': modeLabel = '✦ EXPERT COUNCIL'; modeStyle = 'border-purple-500 text-purple-300 bg-purple-500/20 shadow-[0_0_10px_rgba(168,85,247,0.4)]'; inputBorder = 'border-purple-500/50'; inputArrow = 'text-purple-400'; break;
    }

    return (
        <div className="relative min-h-screen font-vn overflow-hidden flex">
            {/* ========================================== */}
            {/* 核心重构：背景渲染层 (极致高清优化)          */}
            {/* ========================================== */}
            <div className="absolute inset-0 z-0 bg-slate-900">
                {bgUrl && (
                    <img
                        src={bgUrl}
                        alt="Background"
                        // 强制浏览器使用最高质量的图像渲染算法，抗锯齿和像素拉伸优化
                        style={{imageRendering: 'high-quality'}}
                        className={`w-full h-full object-cover transition-all duration-1000 animate-fade-in ${
                            isBlurEnabled
                                ? 'opacity-60 filter brightness-75 contrast-125'
                                : 'opacity-100 filter-none' // 【核心修改】：100% 不透明度，完全剥离所有滤镜
                        }`}
                        onError={(e) => {
                            e.target.style.display = 'none';
                        }}
                    />
                )}

                {/* 1. Cinematic 模式：厚重的渐变毛玻璃，极客赛博风 */}
                {isBlurEnabled && (
                    <div
                        className="absolute inset-0 bg-gradient-to-b from-slate-900/60 via-slate-900/40 to-slate-900/90 backdrop-blur-[1px]"/>
                )}

                {/* 2. Clear Reality 模式：无边框高清体验 */}
                {!isBlurEnabled && (
                    // 【核心修改】：去掉全屏黑罩！只在屏幕下半部分（输入框和最新文字处）打一层极其柔和的底部黑影，
                    // 既保证了画面 100% 的通透锐利，又保证了白色聊天文字绝对能看清！
                    <div
                        className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent pointer-events-none"/>
                )}
            </div>

            <SessionSidebar isSidebarOpen={isSidebarOpen}
                            currentChatId={chatId?.id || chatId}
                            onSessionSelect={handleSessionSelect}
                            onNewSession={handleNewSession}
                            token={token}
                            dynamicTitleUpdate={dynamicTitleUpdate}/>

            <div className="relative z-10 flex flex-col flex-1 h-screen">
                <div className="flex justify-between items-center p-6 w-full">
                    <button onClick={() => setIsSidebarOpen(!isSidebarOpen)}
                            className="text-white/40 hover:text-white transition-colors p-2 hover:bg-white/10 rounded-lg cursor-pointer">
                        <Menu size={24}/>
                    </button>

                    {/* --- 新增：右上角导航按钮组 --- */}
                    <div className="flex items-center gap-6">
                        <button
                            onClick={() => navigate('/options')} // 【核心修改】：绕过动画直接路由
                            className="text-white/40 hover:text-white vn-text-shadow text-sm transition-colors cursor-pointer font-sans tracking-widest uppercase"
                        >
                            [ CONFIG ]
                        </button>
                        <button
                            onClick={() => navigate('/')} // 【核心修改】：绕过动画直接路由
                            className="text-white/40 hover:text-white vn-text-shadow text-sm transition-colors cursor-pointer font-sans tracking-widest uppercase"
                        >
                            [ RETURN ]
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-hidden flex flex-col w-full max-w-5xl mx-auto px-8 md:px-16 pb-12">
                    <div className="flex-1 overflow-y-auto custom-scrollbar pb-8 pr-2">
                        <div className="flex flex-col space-y-6">
                            {messages.map((msg) => (
                                <div key={msg.id} className="animate-fade-in w-full text-left">

                                    {/* 1. 最顶层：渲染专属的 System Log (路由信息) */}
                                    {msg.systemLog && (
                                        <div
                                            className="text-green-400/80 font-mono text-sm border-l-2 border-green-500/50 pl-3 mb-4 shadow-[0_0_10px_rgba(34,197,94,0.1)]">
                                            {msg.systemLog}
                                        </div>
                                    )}

                                    {/* 2. 第二层：优雅地使用抽离出来的 AgentTracePanel 组件 (思考与工具) */}
                                    {msg.sender === 'ai' &&
                                        <AgentTracePanel steps={msg.steps} tokenUsage={msg.tokenUsage}/>}

                                    {/* 3. 最底层：渲染普通的文本主体 */}
                                    <p className={`text-lg md:text-xl leading-snug tracking-tight vn-text-shadow break-words whitespace-pre-wrap ${
                                        msg.sender === 'user' ? 'text-cyan-200' : 'text-white'
                                    }`}>
                                        {msg.text}
                                    </p>
                                </div>
                            ))}
                            <div ref={bottomRef}/>
                        </div>
                    </div>

                    <div
                        className={`mt-4 pt-4 border-t transition-colors duration-500 w-full ${agentQuestion ? 'border-yellow-400/50' : inputBorder}`}>

                        {/* 1. 第一行：系统提问（如果有，单独占一行并闪烁） */}
                        {agentQuestion && (
                            <div className="text-sm text-yellow-300 font-serif tracking-wide animate-pulse mb-2">
                                [System]: Waiting for confirmation: "{agentQuestion}"
                            </div>
                        )}

                        {/* 2. 第二行：左右控制台 */}
                        <div className="flex justify-between items-end mb-2 min-h-[24px]">

                            {/* 左侧：Review 开关 (仅在特定模式且没有提问时显示) */}
                            <div>
                                {(chatMode === 'AGENT' || chatMode === 'EXPERT') && !agentQuestion && (
                                    <label className="flex items-center gap-2 cursor-pointer group w-fit">
                                        <input
                                            type="checkbox"
                                            className="hidden"
                                            checked={isReviewEnabled}
                                            onChange={(e) => {
                                                setIsReviewEnabled(e.target.checked);
                                                localStorage.setItem('crossrow_enable_review', e.target.checked);
                                            }}
                                            disabled={isSending}
                                        />

                                        {/* 动态主题色的方块外框与发光 */}
                                        <div
                                            className={`w-3 h-3 border flex items-center justify-center transition-all duration-300 ${
                                                isReviewEnabled
                                                    ? (chatMode === 'AGENT'
                                                        ? 'border-blue-500 bg-blue-500/20 shadow-[0_0_8px_rgba(59,130,246,0.4)]'
                                                        : 'border-purple-500 bg-purple-500/20 shadow-[0_0_8px_rgba(168,85,247,0.4)]')
                                                    : 'border-slate-600 bg-transparent group-hover:border-slate-400'
                                            }`}>
                                            {/* 动态主题色的内核芯 */}
                                            {isReviewEnabled && (
                                                <div
                                                    className={`w-1.5 h-1.5 ${chatMode === 'AGENT' ? 'bg-blue-400' : 'bg-purple-400'}`}/>
                                            )}
                                        </div>

                                        {/* 动态主题色的伴随文字 */}
                                        <span
                                            className={`text-xs font-sans tracking-widest uppercase transition-colors duration-300 ${
                                                isReviewEnabled
                                                    ? (chatMode === 'AGENT' ? 'text-blue-300' : 'text-purple-300')
                                                    : 'text-slate-500 group-hover:text-slate-300'
                                            }`}>
                                            Enable Supervisor
                                        </span>
                                    </label>
                                )}
                            </div>

                            {/* 右侧：模式切换按钮 */}
                            <button
                                onClick={() => setChatModeIndex((prev) => (prev + 1) % MODES.length)}
                                disabled={isSending || agentQuestion}
                                className={`text-xs font-sans tracking-widest px-3 py-1 rounded transition-all duration-300 border ${modeStyle} ${isSending || agentQuestion ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                            >
                                {modeLabel}
                            </button>
                        </div>

                        {/* 【新增】：准备发送的图片预览区 */}
                        {pendingImages.length > 0 && (
                            <div className="flex gap-3 mb-3 p-3 bg-black/20 rounded-xl overflow-x-auto custom-scrollbar border border-white/5 shadow-inner">
                                {pendingImages.map((file, idx) => (
                                    <div key={idx} className="relative shrink-0 w-20 h-20 rounded-lg border border-white/10 overflow-hidden group shadow-lg">
                                        <img
                                            src={URL.createObjectURL(file)}
                                            alt="preview"
                                            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-110"
                                        />
                                        <button
                                            onClick={() => removePendingImage(idx)}
                                            className="absolute top-1 right-1 bg-red-500/80 text-white rounded-full p-1 opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer hover:bg-red-500"
                                            title="Remove image"
                                        >
                                            <X size={12} />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}

                        {/* 3. 第三行：真正的输入框主体 */}
                        <div className="flex items-center w-full bg-black/10 rounded-xl px-2 py-1">
                            {/* 【新增】：图片选择按钮与隐藏的文件输入框 */}
                            <button
                                onClick={() => fileInputRef.current?.click()}
                                disabled={isSending || !chatId || agentQuestion}
                                className={`p-2 text-slate-400 hover:text-cyan-400 transition-colors ${isSending || !chatId || agentQuestion ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:bg-white/5 rounded-lg'}`}
                                title="Upload Image"
                            >
                                <ImageIcon size={22} />
                            </button>
                            <input
                                type="file"
                                multiple
                                accept="image/*"
                                className="hidden"
                                ref={fileInputRef}
                                onChange={handleImageSelect}
                            />

                            {/* 原有的输入框，增加了 onPaste 监听 */}
                            <input
                                type="text"
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={handleSend}
                                onPaste={handlePaste}
                                autoFocus
                                disabled={isSending || !chatId}
                                placeholder={!chatId ? "Select or create a session from the left..." : isSending ? "Processing..." : (agentQuestion ? "Type your answer here..." : "Message...")}
                                className={`flex-1 w-full bg-transparent border-none outline-none text-lg md:text-xl text-white vn-text-shadow placeholder-white/20 font-vn leading-snug tracking-tight caret-transparent cursor-blink px-2 ${(!chatId || isSending) ? 'opacity-50 cursor-not-allowed' : ''}`}
                                autoComplete="off"
                            />
                        </div>
                    </div>
                </div>
            </div>

            <ImageModal imageUrl={modalImage} onClose={() => setModalImage(null)}/>
        </div>
    );
}