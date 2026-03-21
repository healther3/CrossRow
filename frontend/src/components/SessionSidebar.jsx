import React, { useState, useEffect } from 'react';
import { MessageSquare, Plus, Trash2, Folder, FolderOpen, Edit2 } from 'lucide-react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

export default function SessionSidebar({
                                           isSidebarOpen,
                                           currentChatId,
                                           onSessionSelect,
                                           onNewSession,
                                           token,
                                           dynamicTitleUpdate
                                       }) {
    const baseUrlAPI = import.meta.env.PROD ? '' : 'http://localhost:8123';
    const [folders, setFolders] = useState([]);
    const [expandedFolders, setExpandedFolders] = useState(new Set());

    const [sessionToDelete, setSessionToDelete] = useState(null);
    const [folderToDelete, setFolderToDelete] = useState(null);
    const [typingTitle, setTypingTitle] = useState(null);

    const [editingFolderId, setEditingFolderId] = useState(null);
    const [editingFolderName, setEditingFolderName] = useState("");

    // 1. 初始化加载
    const loadFolders = async () => {
        if (!token) return;
        try {
            const response = await fetch(`${baseUrlAPI}/api/folders`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (response.ok) {
                const data = await response.json();
                setFolders(prevFolders => {
                    const prevIds = new Set(prevFolders.map(f => f.id));
                    setExpandedFolders(prevExpanded => {
                        const nextExpanded = new Set(prevExpanded);
                        if (prevIds.size === 0) {
                            data.forEach(f => nextExpanded.add(f.id));
                        } else {
                            data.forEach(f => {
                                if (!prevIds.has(f.id)) nextExpanded.add(f.id);
                            });
                        }
                        return nextExpanded;
                    });
                    return data;
                });
            }
        } catch (error) {
            console.error("Failed to load folders:", error);
        }
    };

    useEffect(() => { loadFolders(); }, [token]);

    // 2. 动态标题打字机
    useEffect(() => {
        if (dynamicTitleUpdate && dynamicTitleUpdate.id && dynamicTitleUpdate.title) {
            setTypingTitle({ id: dynamicTitleUpdate.id, targetText: dynamicTitleUpdate.title, currentText: "" });
        }
    }, [dynamicTitleUpdate]);

    useEffect(() => {
        if (!typingTitle) return;
        const { id, targetText, currentText } = typingTitle;

        if (currentText.length < targetText.length) {
            const timer = setTimeout(() => {
                setTypingTitle(prev => ({ ...prev, currentText: targetText.slice(0, prev.currentText.length + 1) }));
            }, 50);
            return () => clearTimeout(timer);
        } else {
            setFolders(prevFolders => prevFolders.map(folder => ({
                ...folder,
                sessions: folder.sessions.map(s => s.id === id ? { ...s, title: targetText } : s)
            })));
            const timer = setTimeout(() => setTypingTitle(null), 800);
            return () => clearTimeout(timer);
        }
    }, [typingTitle]);

    // 3. 文件夹操作
    const handleCreateFolder = async () => {
        const name = "NEW FOLDER";
        try {
            const res = await fetch(`${baseUrlAPI}/api/folders?name=${encodeURIComponent(name)}`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) loadFolders();
        } catch (e) { console.error(e); }
    };

    const startRenameFolder = (e, folderId, currentName) => {
        e.stopPropagation(); // 阻止事件冒泡，防止文件夹折叠/展开
        setEditingFolderId(folderId);
        setEditingFolderName(currentName);
    };

    const submitRenameFolder = async (folderId) => {
        const name = editingFolderName.trim();
        const currentFolder = folders.find(f => f.id === folderId);

        // 无论成功与否，先关闭编辑状态
        setEditingFolderId(null);

        // 如果名字没变或者为空，直接取消，不发请求
        if (!name || name === currentFolder.name) return;

        try {
            const res = await fetch(`${baseUrlAPI}/api/folders/${folderId}/name?name=${encodeURIComponent(name)}`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) loadFolders();
        } catch (e) { console.error(e); }
    };

    const handleDeleteFolder = async (folderId) => {
        try {
            const res = await fetch(`${baseUrlAPI}/api/folders/${folderId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) {
                setFolderToDelete(null);
                loadFolders();
            }
        } catch (e) { console.error(e); }
    };

    // 4. 会话操作
    const handleCreateSession = async (folderId = null) => {
        try {
            const url = folderId
                ? `${baseUrlAPI}/api/sessions?folderId=${folderId}`
                : `${baseUrlAPI}/api/sessions`;

            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (response.ok) {
                const rawText = await response.text();
                let actualSessionId = rawText;

                // 核心修复：尝试把后端返回的字符串当做 JSON 解析
                try {
                    const parsedData = JSON.parse(rawText);
                    // 如果解析成功，且里面有 id 字段，就精准提取出 id
                    if (parsedData && parsedData.id) {
                        actualSessionId = parsedData.id;
                    }
                } catch (e) {
                    // 解析失败说明后端返回的就是纯 UUID 字符串，保持原样即可
                }

                loadFolders();
                onNewSession(actualSessionId);
            }
        } catch (error) { console.error(error); }
    };

    const handleDeleteSession = async (sessionId) => {
        try {
            const response = await fetch(`${baseUrlAPI}/api/sessions/${sessionId}`, {
                method: 'DELETE',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (response.ok) {
                setSessionToDelete(null);
                loadFolders();
                if (currentChatId === sessionId) onNewSession(null);
            }
        } catch (error) { console.error(error); }
    };

    const toggleFolder = (folderId) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            if (next.has(folderId)) next.delete(folderId);
            else next.add(folderId);
            return next;
        });
    };

    // 拖拽逻辑
    const onDragEnd = async (result) => {
        const { source, destination, draggableId } = result;
        if (!destination) return;
        if (source.droppableId === destination.droppableId && source.index === destination.index) return;

        const sourceFolderId = source.droppableId;
        const destFolderId = destination.droppableId;

        setFolders(prevFolders => {
            const newFolders = JSON.parse(JSON.stringify(prevFolders));
            const sourceFolder = newFolders.find(f => f.id === sourceFolderId);
            const destFolder = newFolders.find(f => f.id === destFolderId);
            const [movedSession] = sourceFolder.sessions.splice(source.index, 1);
            destFolder.sessions.splice(destination.index, 0, movedSession);
            return newFolders;
        });

        try {
            const res = await fetch(`${baseUrlAPI}/api/folders/sessions/${draggableId}/move?folderId=${destFolderId}`, {
                method: 'PUT',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (!res.ok) {
                console.error("Failed to move session in backend");
                loadFolders();
            }
        } catch (error) {
            console.error("Network error during drag and drop", error);
            loadFolders();
        }
    };

    return (
        // 【核心修复】：h-full 改为了 h-screen shrink-0。强行将侧边栏锁定为屏幕高度！
        <div className={`flex flex-col h-screen shrink-0 bg-slate-950/80 backdrop-blur-2xl border-r border-slate-800/50 transition-all duration-500 z-50 overflow-hidden shadow-2xl ${
            isSidebarOpen ? 'w-72 opacity-100 translate-x-0' : 'w-0 opacity-0 -translate-x-full border-none'
        }`}>
            {/* 侧边栏头部 */}
            <div className="p-5 border-b border-slate-800/50 flex flex-col gap-4">
                <div className="flex justify-between items-center">
                    <span className="text-slate-200 font-serif text-xl tracking-widest uppercase vn-text-shadow select-none">
                        Archives
                    </span>
                    <button
                        onClick={handleCreateFolder}
                        className="text-slate-400 hover:text-cyan-400 transition-colors cursor-pointer p-1"
                        title="New Folder"
                    >
                        <Folder size={18} />
                    </button>
                </div>
            </div>

            <DragDropContext onDragEnd={onDragEnd}>
                {/* 滚动区域：因为父元素被锁死了 h-screen，这里的 flex-1 overflow-y-auto 终于可以完美生效了 */}
                <div className="flex-1 overflow-y-auto custom-scrollbar p-3 space-y-4">
                    {folders.map(folder => {
                        const isExpanded = expandedFolders.has(folder.id);

                        return (
                            <div key={folder.id} className="flex flex-col space-y-1">
                                {/* 文件夹头部 */}
                                <div className="group flex items-center justify-between px-2 py-1.5 rounded hover:bg-slate-800/40 text-slate-400 transition-colors">
                                    <div
                                        className="flex items-center gap-2 flex-1 cursor-pointer select-none overflow-hidden pr-2"
                                        onClick={() => toggleFolder(folder.id)}
                                    >
                                        {isExpanded ? <FolderOpen size={16} className="text-cyan-600/80 shrink-0" /> : <Folder size={16} className="text-cyan-800/80 shrink-0" />}
                                        {editingFolderId === folder.id ? (
                                            <input
                                                type="text"
                                                value={editingFolderName}
                                                autoFocus
                                                onClick={(e) => e.stopPropagation()} // 阻止点击输入框时触发文件夹展开
                                                onChange={(e) => setEditingFolderName(e.target.value)}
                                                onKeyDown={(e) => {
                                                    if (e.key === 'Enter') submitRenameFolder(folder.id);
                                                    if (e.key === 'Escape') setEditingFolderId(null); // 按 Esc 取消
                                                }}
                                                onBlur={() => submitRenameFolder(folder.id)} // 鼠标点到别处时自动保存
                                                className="bg-slate-900 text-cyan-400 border border-cyan-500/50 rounded px-1.5 py-0.5 w-full text-xs font-sans font-bold tracking-wider uppercase outline-none focus:ring-1 focus:ring-cyan-400 shadow-inner"
                                            />
                                        ) : (
                                            <span className="font-sans text-xs font-bold tracking-wider uppercase truncate">
                                            {folder.name}
                                            </span>
                                        )}
                                    </div>

                                    {folderToDelete === folder.id ? (
                                        <div className="flex items-center gap-2 shrink-0 bg-slate-900 px-2 py-1 rounded">
                                            <span className="text-[10px] text-red-500/80 font-sans tracking-widest uppercase animate-pulse pr-1">Purge?</span>
                                            <button onClick={(e) => { e.stopPropagation(); handleDeleteFolder(folder.id); }} className="text-red-400 hover:text-red-300 text-xs font-bold transition-colors cursor-pointer">[Y]</button>
                                            <button onClick={(e) => { e.stopPropagation(); setFolderToDelete(null); }} className="text-slate-400 hover:text-slate-300 text-xs font-bold transition-colors cursor-pointer">[N]</button>
                                        </div>
                                    ) : (
                                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0 bg-slate-800/80 px-1 rounded">
                                            <button onClick={() => handleCreateSession(folder.id)} className="p-1 hover:text-cyan-400 transition-colors cursor-pointer" title="New Chat Here"><Plus size={14} /></button>
                                            {!folder.isDefault && (
                                                <>
                                                    <button
                                                        onClick={(e) => startRenameFolder(e, folder.id, folder.name)}
                                                        className="p-1 hover:text-blue-400 transition-colors cursor-pointer"
                                                        title="Rename"><Edit2 size={12}/></button>
                                                    <button onClick={(e) => { e.stopPropagation(); setFolderToDelete(folder.id); }} className="p-1 hover:text-red-400 transition-colors cursor-pointer" title="Delete"><Trash2 size={12} /></button>
                                                </>
                                            )}
                                        </div>
                                    )}
                                </div>

                                {/* 拖拽接收区 */}
                                {isExpanded && (
                                    <Droppable droppableId={folder.id}>
                                        {(provided, snapshot) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.droppableProps}
                                                className={`flex flex-col space-y-1 pl-4 border-l ml-2 transition-colors duration-300 min-h-[20px] rounded-bl-md ${
                                                    snapshot.isDraggingOver ? 'border-cyan-500/50 bg-cyan-950/20' : 'border-slate-800/30'
                                                }`}
                                            >
                                                {folder.sessions && folder.sessions.length > 0 ? (
                                                    folder.sessions.map((session, index) => (
                                                        <Draggable key={session.id} draggableId={session.id} index={index}>
                                                            {(provided, snapshot) => (
                                                                <div
                                                                    ref={provided.innerRef}
                                                                    {...provided.draggableProps}
                                                                    {...provided.dragHandleProps}
                                                                    style={provided.draggableProps.style}
                                                                    className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-lg cursor-grab active:cursor-grabbing transition-colors ${
                                                                        snapshot.isDragging ? 'bg-cyan-900/60 shadow-[0_0_20px_rgba(6,182,212,0.3)] ring-1 ring-cyan-400/50 z-50' :
                                                                            currentChatId === session.id
                                                                                ? 'bg-blue-900/20 border border-blue-500/30 shadow-[0_0_10px_rgba(59,130,246,0.1)] text-blue-300'
                                                                                : 'hover:bg-slate-800/50 text-slate-400 hover:text-slate-200 border border-transparent'
                                                                    }`}
                                                                >
                                                                    <div className="flex-1 flex items-center gap-3 overflow-hidden" onClick={() => onSessionSelect(session.id)}>
                                                                        <MessageSquare size={16} className={currentChatId === session.id ? 'text-blue-400' : 'text-slate-600 group-hover:text-slate-400 shrink-0'} />
                                                                        <div className="font-sans text-sm tracking-wide truncate flex-1">
                                                                            {typingTitle && typingTitle.id === session.id ? (
                                                                                <span className="text-cyan-400">{typingTitle.currentText}<span className="animate-pulse">█</span></span>
                                                                            ) : (
                                                                                <span>{session.title || 'New Conversation'}</span>
                                                                            )}
                                                                        </div>
                                                                    </div>

                                                                    {sessionToDelete === session.id ? (
                                                                        <div className="flex items-center gap-2 shrink-0 bg-slate-900 px-1 py-0.5 rounded">
                                                                            <button onClick={(e) => { e.stopPropagation(); handleDeleteSession(session.id); }} className="text-red-400 hover:text-red-300 text-xs font-bold transition-colors cursor-pointer">[Y]</button>
                                                                            <button onClick={(e) => { e.stopPropagation(); setSessionToDelete(null); }} className="text-slate-400 hover:text-slate-300 text-xs font-bold transition-colors cursor-pointer">[N]</button>
                                                                        </div>
                                                                    ) : (
                                                                        <button
                                                                            onClick={(e) => { e.stopPropagation(); setSessionToDelete(session.id); }}
                                                                            className="opacity-0 group-hover:opacity-100 text-slate-600 hover:text-red-400 transition-all duration-200 shrink-0 p-1 cursor-pointer bg-slate-800/80 rounded"
                                                                        >
                                                                            <Trash2 size={14} />
                                                                        </button>
                                                                    )}
                                                                </div>
                                                            )}
                                                        </Draggable>
                                                    ))
                                                ) : (
                                                    <div className="py-2 pl-3 text-xs text-slate-600 font-sans italic select-none opacity-50">
                                                        Drop to move here...
                                                    </div>
                                                )}
                                                {provided.placeholder}
                                            </div>
                                        )}
                                    </Droppable>
                                )}
                            </div>
                        );
                    })}
                </div>
            </DragDropContext>
        </div>
    );
}