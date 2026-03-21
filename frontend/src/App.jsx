import React from 'react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import Navbar from './components/Navbar';
import Background from './components/Background';
import InteractiveCanvas from './components/InteractiveCanvas';
import ChatPage from './pages/ChatPage';
import OptionsPage from './pages/OptionPage'; // 1. 引用名保持 OptionsPage
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

import { SettingsProvider } from './context/SettingsContext';
import { TransitionProvider, useTransition } from './context/TransitionContext';
import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';

function Home() {
    const { navigateWithTransition } = useTransition();

    return (
        <div className="flex flex-col items-center justify-center min-h-screen z-10 relative pointer-events-none">
            {/* 移除这里的 InteractiveCanvas，移到下方 AppRoutes 全局显示 */}
            <Navbar />

            {/* Title */}
            <div className="vn-title-container text-6xl md:text-8xl mb-16 tracking-wide -mt-20 transition-all duration-700 ease-in-out">
                <span className="vn-title-stroke" aria-hidden="true">CrossRow</span>
                <span className="vn-title-fill">CrossRow</span>
            </div>

            <p className="mt-2 text-xl font-medium text-slate-500 tracking-wide drop-shadow-sm mb-16">
                - let's chat together -
            </p>

            {/* Menu Buttons Area */}
            <div className="pointer-events-auto flex flex-col items-center gap-8">

                {/* 1. Start Button */}
                <button
                    onClick={() => navigateWithTransition('/chat')}
                    className="group relative px-12 py-3 overflow-hidden rounded-full transition-all duration-300 hover:scale-110 cursor-pointer"
                >
                    {/* 修正：text-1xl -> text-xl */}
                    <span
                        className="relative z-10 font-serif text-xl md:text-2xl text-slate-600 group-hover:text-blue-600 transition-colors duration-300">
                        Start
                    </span>
                    <span
                        className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-full opacity-0 transition-all duration-300 group-hover:translate-x-4 group-hover:opacity-100 text-blue-400 font-serif text-2xl md:text-3xl">▶</span>
                    <span
                        className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-full opacity-0 transition-all duration-300 group-hover:-translate-x-4 group-hover:opacity-100 text-blue-400 font-serif text-2xl md:text-3xl">◀</span>
                </button>

                {/* 2. Config Button */}
                <button
                    onClick={() => navigateWithTransition('/options')}
                    className="group relative px-12 py-3 overflow-hidden rounded-full transition-all duration-300 hover:scale-110 cursor-pointer"
                >
                    <span
                        className="relative z-10 font-serif text-2xl md:text-2xl text-slate-500 group-hover:text-blue-500 transition-colors duration-300">
                        Config
                    </span>
                    <span
                        className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-full opacity-0 transition-all duration-300 group-hover:translate-x-4 group-hover:opacity-100 text-blue-400 font-serif text-xl md:text-2xl">▶</span>
                    <span
                        className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-full opacity-0 transition-all duration-300 group-hover:-translate-x-4 group-hover:opacity-100 text-blue-400 font-serif text-xl md:text-2xl">◀</span>
                </button>

                {/* 3. Exit Button */}
                <button
                    className="group relative px-10 py-2 overflow-hidden rounded-full transition-all duration-300 hover:scale-105 cursor-default">
                     <span
                         className="font-serif text-xl md:text-2xl text-slate-400 group-hover:text-slate-600 transition-colors">
                        Exit
                    </span>
                </button>
            </div>
        </div>
    );
}

function AppRoutes() {
    return (
        <div className="relative min-h-screen text-gray-800 font-sans selection:bg-blue-200 selection:text-blue-900 overflow-hidden">
            <Background/>
            <InteractiveCanvas />

            <Routes>
                {/* 公开路由 */}
                <Route path="/" element={<Home />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />

                {/* --- 受保护路由 --- */}
                <Route path="/chat" element={
                    <PrivateRoute>
                        <ChatPage />
                    </PrivateRoute>
                } />
                <Route path="/options" element={
                    <PrivateRoute>
                        <OptionsPage />
                    </PrivateRoute>
                } />
            </Routes>
        </div>
    );
}

function App() {
    return (
        <AuthProvider>
            <SettingsProvider>
                <BrowserRouter>
                    <TransitionProvider>
                        <AppRoutes/>
                    </TransitionProvider>
                </BrowserRouter>
            </SettingsProvider>
        </AuthProvider>
    );
}

export default App;