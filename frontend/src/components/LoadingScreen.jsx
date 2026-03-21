import React from 'react';

export default function LoadingScreen({ isVisible }) {
    // 使用 CSS transition 来控制淡入淡出
    // pointer-events-none 确保在隐藏时不会挡住鼠标点击
    return (
        <div
            className={`fixed inset-0 z-[100] bg-black flex flex-col items-center justify-center transition-opacity duration-500 ease-in-out ${
                isVisible ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
            }`}
        >
            {/* 加载文字：复古衬线体 + 呼吸动画 */}
            <div className="text-white font-serif text-3xl md:text-4xl tracking-[0.2em] animate-pulse">
                Now Loading...
            </div>

            {/* 装饰性进度条 (模拟) */}
            <div className="mt-8 w-48 h-[2px] bg-gray-800 overflow-hidden">
                <div className="h-full bg-white w-full origin-left animate-[progress_2s_ease-in-out_infinite]" />
            </div>

            {/* 添加对应的 CSS 动画 (Tailwind config 没配置的话可以用 style) */}
            <style jsx>{`
                @keyframes progress {
                    0% { transform: scaleX(0); }
                    50% { transform: scaleX(0.7); }
                    100% { transform: scaleX(1); opacity: 0; }
                }
            `}</style>
        </div>
    );
}