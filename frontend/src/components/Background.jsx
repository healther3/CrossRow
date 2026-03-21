// src/components/Background.jsx
import React from 'react';
// import { useEffect, useState } from 'react'; // 旧的交互不需要了

export default function Background() {
    // 移除旧的 JS 鼠标追踪逻辑，交给 FluidCursor 处理

    return (
        <div className="fixed inset-0 -z-10 overflow-hidden bg-slate-100">
            {/* 1. 基础底色 */}
            <div className="absolute inset-0 bg-gradient-to-b from-blue-100 to-slate-200" />

            {/* 2. 色块层：这里的颜色是“水” */}
            {/* 稍微加深了不透明度，这样被白色划开时对比更强烈 */}

            <div className="absolute -top-[10%] -left-[10%] w-[70vw] h-[70vw] rounded-full bg-blue-600/20 blur-[80px] animate-pulse"
                 style={{ animationDuration: '8s' }} />

            <div className="absolute top-[20%] -right-[15%] w-[60vw] h-[60vw] rounded-full bg-cyan-600/20 blur-[100px]" />

            <div className="absolute -bottom-[10%] left-[20%] w-[60vw] h-[60vw] rounded-full bg-indigo-600/20 blur-[80px]" />

            {/* 3. 噪点纹理 */}
            <div className="absolute inset-0 opacity-[0.05] bg-[url('https://grainy-gradients.vercel.app/noise.svg')]" />
        </div>
    );
}