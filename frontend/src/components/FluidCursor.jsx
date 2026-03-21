// src/components/FluidCursor.jsx
import React, { useEffect, useRef, useState } from 'react';

const TRAIL_LENGTH = 20; // 轨迹长度，越长“划痕”保留越久

export default function FluidCursor() {
    const [trail, setTrail] = useState([]);
    const requestRef = useRef();
    const timerRef = useRef();

    useEffect(() => {
        // 鼠标移动处理：添加新的点到轨迹中
        const handleMouseMove = (e) => {
            const { clientX, clientY } = e;

            setTrail(prev => {
                // 新的点：坐标 + 唯一的id + 创建时间
                const newPoint = {
                    x: clientX,
                    y: clientY,
                    id: Date.now() + Math.random()
                };
                // 保留最近的 N 个点
                return [...prev, newPoint].slice(-TRAIL_LENGTH);
            });

            // 防抖重置：如果鼠标停止，慢慢清除轨迹
            clearTimeout(timerRef.current);
            timerRef.current = setTimeout(() => {
                setTrail([]);
            }, 500); // 0.5秒后如果没动静就开始愈合
        };

        window.addEventListener('mousemove', handleMouseMove);

        // 动画循环：让轨迹点慢慢变小（模拟水流合拢）
        const animate = () => {
            setTrail(prev => {
                if (prev.length === 0) return [];
                // 这里只是为了触发重渲染，或者你可以加入让点缩小的逻辑
                // 简单版：我们主要依赖 CSS animation 或者是新点的更替
                // 但为了更流畅的效果，我们让旧的点自动剔除
                return prev.slice(1);
            });
            requestRef.current = requestAnimationFrame(animate);
        };

        // 为了性能，我们这里不开启全时 loop，而是利用 CSS 动画来做“消失”
        // 这样 JS 线程压力更小

        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            cancelAnimationFrame(requestRef.current);
            clearTimeout(timerRef.current);
        };
    }, []);

    return (
        <>
            {/* SVG 滤镜定义：这是魔法发生的地方
        它会把重叠的圆形融合在一起，产生类似液体的边缘
      */}
            <svg style={{ position: 'absolute', width: 0, height: 0 }}>
                <filter id="goo">
                    <feGaussianBlur in="SourceGraphic" result="blur" stdDeviation="15" />
                    <feColorMatrix
                        in="blur"
                        mode="matrix"
                        values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 25 -7"
                        result="goo"
                    />
                    <feComposite in="SourceGraphic" in2="goo" operator="atop"/>
                </filter>
            </svg>

            {/* 轨迹容器：应用滤镜 */}
            <div
                className="pointer-events-none fixed inset-0 z-0 overflow-hidden"
                style={{ filter: 'url(#goo)' }} // 应用上面定义的 goo 滤镜
            >
                {trail.map((point, index) => {
                    // 计算大小：越新的点越大，越旧的点越小（模拟拖尾）
                    const ageRatio = index / trail.length;
                    const size = 5 + (ageRatio * 10); // 范围 40px -> 120px

                    return (
                        <div
                            key={point.id}
                            className="absolute rounded-full bg-slate-50 opacity-90"
                            style={{
                                left: point.x,
                                top: point.y,
                                width: `${size}px`,
                                height: `${size}px`,
                                transform: 'translate(-50%, -50%)',
                                // 这是一个淡出的动画，模拟水面合拢
                                transition: 'all 0.1s ease-out',
                            }}
                        />
                    );
                })}
            </div>
        </>
    );
}