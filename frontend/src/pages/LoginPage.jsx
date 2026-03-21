import React, { useState } from 'react';
import Navbar from '../components/Navbar';
import { useTransition } from '../context/TransitionContext';
import { useAuth } from '../context/AuthContext';
import { useLocation } from 'react-router-dom';

// 复用 OptionPage 的精美按钮
const AuthButton = ({ label, onClick, disabled }) => (
    <button
        onClick={onClick}
        disabled={disabled}
        className={`group relative px-8 py-2 overflow-hidden transition-all duration-300 ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:scale-105 cursor-pointer'}`}
    >
        <span className="relative z-10 font-serif text-2xl text-slate-400 group-hover:text-blue-500 transition-colors duration-300">
            {label}
        </span>
        <span className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl opacity-0 group-hover:translate-x-2 group-hover:opacity-100">
            ▶
        </span>
        <span className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl opacity-0 group-hover:-translate-x-2 group-hover:opacity-100">
            ◀
        </span>
    </button>
);

export default function LoginPage() {
    const { navigateWithTransition } = useTransition();
    const { login } = useAuth();
    const location = useLocation();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    // 获取被拦截前想访问的路径，登录后跳过去
    const from = location.state?.from?.pathname || '/';

    const handleLogin = async () => {
        if (!username || !password) {
            setError("Please fill in all fields.");
            return;
        }
        setIsLoading(true);
        setError('');
        try {
            await login(username, password);
            navigateWithTransition(from); // 登录成功，跳转
        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="relative min-h-screen font-serif overflow-hidden flex items-center justify-center">
            <div className="absolute inset-0 bg-white/20 backdrop-blur-sm z-0" />
            <Navbar />

            {/* 修改点1：宽度从 max-w-xl 改为 max-w-4xl，向 OptionPage 看齐 */}
            <div className="relative z-10 w-full max-w-4xl px-6">

                {/* 修改点2：Padding 增加到 p-12 md:p-16，与 OptionPage 一致 */}
                <div className="bg-white/80 backdrop-blur-xl rounded-3xl shadow-2xl p-12 md:p-16 text-center transform transition-all duration-500">

                    {/* 修改点3：Margin bottom 增加到 mb-12 */}
                    <h2 className="text-4xl md:text-5xl font-bold text-slate-700 mb-12 tracking-wider vn-title-container">
                        System Login
                    </h2>

                    {/* 修改点4：增加 max-w-lg mx-auto，防止输入框在宽卡片中拉得太长 */}
                    <div className="space-y-6 mb-8 max-w-lg mx-auto">
                        <input
                            type="text"
                            placeholder="Username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="w-full bg-slate-100/50 border border-slate-200 rounded-2xl px-6 py-4 text-slate-700 text-lg font-sans focus:outline-none focus:ring-2 focus:ring-blue-300 transition-all placeholder:text-slate-400"
                        />
                        <input
                            type="password"
                            placeholder="Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
                            className="w-full bg-slate-100/50 border border-slate-200 rounded-2xl px-6 py-4 text-slate-700 text-lg font-sans focus:outline-none focus:ring-2 focus:ring-blue-300 transition-all placeholder:text-slate-400"
                        />
                    </div>

                    {/* 错误提示 */}
                    <div className="h-6 mb-4">
                        <span className="text-red-500 font-sans text-sm tracking-wide">
                            {error}
                        </span>
                    </div>

                    <div className="flex flex-col items-center gap-6 mt-6 pt-8 border-t border-slate-100">
                        <AuthButton label={isLoading ? "Authenticating..." : "Login"} onClick={handleLogin} disabled={isLoading} />

                        <div className="text-slate-400 font-sans text-sm mt-4">
                            Need an account?{' '}
                            <button onClick={() => navigateWithTransition('/register')} className="text-blue-500 hover:text-blue-700 hover:underline transition-colors">
                                Register here
                            </button>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}