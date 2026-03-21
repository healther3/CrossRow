import React, { createContext, useContext, useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import LoadingScreen from '../components/LoadingScreen';

const TransitionContext = createContext();

export function TransitionProvider({ children }) {
    const navigate = useNavigate();
    const location = useLocation();
    const [isLoading, setIsLoading] = useState(false);

    // 核心跳转函数
    // path: 目标路径
    // minDuration: 强制最小停留时间 (默认 1.5秒，给图片加载留足时间)
    const navigateWithTransition = (path, minDuration = 1500) => {
        if (path === location.pathname) return; // 如果是当前页就不跳

        setIsLoading(true); // 1. 立即显示 Loading

        // 2. 等待一段时间后再跳转
        setTimeout(() => {
            navigate(path);

            // 3. 跳转后，稍微再等一小会儿再隐藏 Loading (确保新页面渲染完成)
            setTimeout(() => {
                setIsLoading(false);
            }, 500);

        }, minDuration);
    };

    return (
        <TransitionContext.Provider value={{ navigateWithTransition }}>
            {/* 全局 Loading 组件放在这里，覆盖所有子组件 */}
            <LoadingScreen isVisible={isLoading} />
            {children}
        </TransitionContext.Provider>
    );
}

export function useTransition() {
    return useContext(TransitionContext);
}