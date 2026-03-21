import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext();

export function AuthProvider({ children }) {
    const [token, setToken] = useState(localStorage.getItem('jwt_token') || null);
    const [userId, setUserId] = useState(localStorage.getItem('user_id') || null);

    // 当 token 变化时，同步到 localStorage
    useEffect(() => {
        if (token) {
            localStorage.setItem('jwt_token', token);
            localStorage.setItem('user_id', userId);
        } else {
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('user_id');
        }
    }, [token, userId]);

    // 【新增】：使用 useCallback 包裹 logout，确保函数引用稳定
    const logout = useCallback(() => {
        setToken(null);
        setUserId(null);
    }, []);

    // ==========================================
    // 【核心新增】：全局 Fetch 拦截器 (被动防线)
    // ==========================================
    useEffect(() => {
        // 1. 备份浏览器原生的 fetch 方法
        const originalFetch = window.fetch;

        // 2. 覆盖全局 fetch
        window.fetch = async (...args) => {
            try {
                // 照常发起网络请求
                const response = await originalFetch(...args);

                // 如果后端返回 401 凭证过期/无效
                if (response.status === 401) {
                    // 获取请求的 URL，排除掉登录和注册接口（避免密码错误时也触发）
                    const url = typeof args[0] === 'string' ? args[0] : args[0]?.url || '';
                    if (!url.includes('/api/auth/login') && !url.includes('/api/auth/register')) {
                        console.warn('[Security] Token expired or invalid. Kicking to login...');
                        // 触发登出，清空 Token
                        logout();
                        // 注：触发 logout 后，isAuthenticated 瞬间变为 false，
                        // 外层的 <PrivateRoute> 会自动接管，将页面重定向到 /login
                    }
                }
                return response;
            } catch (error) {
                throw error;
            }
        };

        // 3. 组件卸载时恢复原生的 fetch（良好的清理习惯）
        return () => {
            window.fetch = originalFetch;
        };
    }, [logout]);

    // 登录方法
    const login = async (username, password) => {
        const response = await originalFetchOrFetch('http://localhost:8123/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText || 'Login failed');
        }

        const data = await response.json();
        setToken(data.token);
        setUserId(data.userId || username);
        return data;
    };

    // 此处单独为 login/register 提供无拦截的调用方式，避免被循环劫持，更稳妥
    const originalFetchOrFetch = window.fetch;

    const register = async (username, password) => {
        const response = await originalFetchOrFetch('http://localhost:8123/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(errorText || 'Registration failed');
        }
        return await response.text();
    };

    return (
        <AuthContext.Provider value={{ token, userId, login, register, logout, isAuthenticated: !!token }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    return useContext(AuthContext);
}