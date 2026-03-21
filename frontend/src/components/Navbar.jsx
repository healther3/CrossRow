import React from 'react';
import { User, LogIn } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTransition } from '../context/TransitionContext';

export default function Navbar() {
    // 1. 从 AuthContext 获取真实的登录状态和登出方法
    const { isAuthenticated, logout } = useAuth();
    // 2. 从 TransitionContext 获取平滑跳转方法
    const { navigateWithTransition } = useTransition();

    const handleAuthClick = () => {
        if (isAuthenticated) {
            // 如果已登录，执行登出
            logout();
        } else {
            // 如果未登录，跳转到登录页
            navigateWithTransition('/login');
        }
    };

    return (
        // 保持了你原有的全部 UI 样式
        <nav className="fixed top-0 left-0 w-full z-50 border-b border-white/10 bg-slate-200/100 backdrop-blur-md shadow-lg transition-all duration-300 pointer-events-auto">
            <div className="mx-auto w-full px-6 lg:px-12">
                <div className="flex h-16 items-center justify-between">

                    {/* 左侧：Logo 或 品牌名 */}
                    <div className="flex-shrink-0">
                        {/* 如果需要 logo 可以放这里 */}
                    </div>

                    {/* 右侧：功能区 */}
                    <div className="flex items-center gap-4">
                        <button
                            onClick={handleAuthClick}
                            className="group relative inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-slate-500 transition-all duration-300 hover:text-slate-800 rounded-full hover:bg-slate-300/50"
                        >
                            {/* 根据真实状态显示文字 */}
                            <span>{isAuthenticated ? 'Logout' : 'Login'}</span>

                            {/* 下划线动画 */}
                            <span className="absolute bottom-1 left-4 right-4 h-[1px] scale-x-0 bg-blue-400 transition-transform duration-300 group-hover:scale-x-100" />
                        </button>
                    </div>

                </div>
            </div>
        </nav>
    );
}