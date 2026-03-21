import React, { createContext, useContext, useState, useEffect } from 'react';

const SettingsContext = createContext();

export function SettingsProvider({ children }) {
    // 默认设置为随机模式
    const [bgConfig, setBgConfig] = useState(() => {
        const saved = localStorage.getItem('app_settings');
        return saved ? JSON.parse(saved) : {
            mode: 'RANDOM', // 'RANDOM' | 'USER'
            coords: null    // { lat: number, lng: number } | null
        };
    });

    // 每次变动自动保存到本地
    useEffect(() => {
        localStorage.setItem('app_settings', JSON.stringify(bgConfig));
    }, [bgConfig]);

    // 更新模式的函数
    const updateMode = (mode) => {
        setBgConfig(prev => ({ ...prev, mode }));
    };

    // 更新坐标的函数
    const updateCoords = (coords) => {
        setBgConfig(prev => ({ ...prev, coords }));
    };

    return (
        <SettingsContext.Provider value={{ bgConfig, updateMode, updateCoords }}>
            {children}
        </SettingsContext.Provider>
    );
}

export function useSettings() {
    return useContext(SettingsContext);
}