import React, { useState } from 'react';
import Navbar from '../components/Navbar';
import { useTransition } from '../context/TransitionContext';
import { useAuth } from '../context/AuthContext';

const AuthButton = ({ label, onClick, disabled }) => (
    <button
        onClick={onClick}
        disabled={disabled}
        className={`group relative px-8 py-2 overflow-hidden transition-all duration-300 ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:scale-105 cursor-pointer'}`}
    >
        <span className="relative z-10 font-serif text-2xl text-slate-400 group-hover:text-blue-500 transition-colors duration-300">{label}</span>
        <span className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl opacity-0 group-hover:translate-x-2 group-hover:opacity-100">▶</span>
        <span className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl opacity-0 group-hover:-translate-x-2 group-hover:opacity-100">◀</span>
    </button>
);

export default function RegisterPage() {
    const { navigateWithTransition } = useTransition();
    const { register } = useAuth();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [successMsg, setSuccessMsg] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleRegister = async () => {
        if (!username || !password) {
            setError("Please fill in all fields.");
            return;
        }
        setIsLoading(true);
        setError('');
        setSuccessMsg('');
        try {
            await register(username, password);
            setSuccessMsg("Registration successful! Redirecting to login...");
            setTimeout(() => {
                navigateWithTransition('/login');
            }, 1500);
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

            {/* 修改点1：宽度 max-w-4xl */}
            <div className="relative z-10 w-full max-w-4xl px-6">

                {/* 修改点2：Padding p-12 md:p-16 */}
                <div className="bg-white/80 backdrop-blur-xl rounded-3xl shadow-2xl p-12 md:p-16 text-center transform transition-all duration-500">

                    {/* 修改点3：mb-12 */}
                    <h2 className="text-4xl md:text-5xl font-bold text-slate-700 mb-12 tracking-wider vn-title-container">
                        Register
                    </h2>

                    {/* 修改点4：约束输入框宽度 max-w-lg mx-auto */}
                    <div className="space-y-6 mb-8 max-w-lg mx-auto">
                        <input
                            type="text"
                            placeholder="Choose a Username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="w-full bg-slate-100/50 border border-slate-200 rounded-2xl px-6 py-4 text-slate-700 text-lg font-sans focus:outline-none focus:ring-2 focus:ring-blue-300 transition-all placeholder:text-slate-400"
                        />
                        <input
                            type="password"
                            placeholder="Choose a Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleRegister()}
                            className="w-full bg-slate-100/50 border border-slate-200 rounded-2xl px-6 py-4 text-slate-700 text-lg font-sans focus:outline-none focus:ring-2 focus:ring-blue-300 transition-all placeholder:text-slate-400"
                        />
                    </div>

                    <div className="h-6 mb-4">
                        {error && <span className="text-red-500 font-sans text-sm tracking-wide">{error}</span>}
                        {successMsg && <span className="text-green-500 font-sans text-sm tracking-wide">{successMsg}</span>}
                    </div>

                    <div className="flex flex-col items-center gap-6 mt-6 pt-8 border-t border-slate-100">
                        <AuthButton label={isLoading ? "Processing..." : "Register"} onClick={handleRegister} disabled={isLoading || successMsg !== ''} />

                        <div className="text-slate-400 font-sans text-sm mt-4">
                            Already exist?{' '}
                            <button onClick={() => navigateWithTransition('/login')} className="text-blue-500 hover:text-blue-700 hover:underline transition-colors">
                                Return to Login
                            </button>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}