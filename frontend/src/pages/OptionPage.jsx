import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useSettings } from '../context/SettingsContext';
import { useTransition } from '../context/TransitionContext';
import { useAuth } from '../context/AuthContext';

const OptionButton = ({ label, isSelected, onClick, size = "text-lg md:text-xl" }) => {
    return (
        <button
            onClick={onClick}
            className="group relative px-4 md:px-6 py-2 overflow-hidden transition-all duration-300 hover:scale-105 cursor-pointer"
        >
            <span
                className={`relative z-10 font-serif ${size} transition-colors duration-300 
                ${isSelected ? 'text-blue-600 font-bold' : 'text-slate-400 group-hover:text-blue-500'}`}
            >
                {label}
            </span>
            <span
                className={`absolute left-0 top-1/2 -translate-y-1/2 -translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl
                ${isSelected ? 'translate-x-2 opacity-100' : 'opacity-0 group-hover:translate-x-2 group-hover:opacity-100'}`}
            >
                ▶
            </span>
            <span
                className={`absolute right-0 top-1/2 -translate-y-1/2 translate-x-full transition-all duration-300 text-blue-400 font-serif text-xl
                ${isSelected ? '-translate-x-2 opacity-100' : 'opacity-0 group-hover:-translate-x-2 group-hover:opacity-100'}`}
            >
                ◀
            </span>
        </button>
    );
};

export default function OptionPage() {
    const navigate = useNavigate();
    const { navigateWithTransition } = useTransition();
    const { bgConfig, updateMode, updateCoords } = useSettings();
    const { token } = useAuth();

    const baseUrlAPI = import.meta.env.PROD ? '' : 'http://localhost:8123';
    // --- 背景与特效状态 ---
    const [tempMode, setTempMode] = useState(bgConfig.mode || 'DEFAULT');
    const [locationStatus, setLocationStatus] = useState('');
    const [isUploading, setIsUploading] = useState(false);
    const [uploadMsg, setUploadMsg] = useState('');

    // 从 localStorage 读取模糊偏好 (默认开启)
    const [enableBlur, setEnableBlur] = useState(() => {
        return localStorage.getItem('crossrow_bg_blur') !== 'false';
    });

    // --- 模型偏好状态 ---
    const [availableModels, setAvailableModels] = useState([]);
    const [tempModel, setTempModel] = useState('');
    const [originalModel, setOriginalModel] = useState('');
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        const fetchModelPreference = async () => {
            if (!token) return;
            try {
                const response = await fetch(`${baseUrlAPI}/api/user/model-preference`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (response.ok) {
                    const data = await response.json();
                    setAvailableModels(data.availableModels || []);
                    setTempModel(data.currentModel || '');
                    setOriginalModel(data.currentModel || '');
                }
            } catch (error) {
                console.error("Failed to fetch model preferences:", error);
            }
        };

        fetchModelPreference();
    }, [token, baseUrlAPI]);

    const handleLocationRequest = () => {
        if (!navigator.geolocation) {
            setLocationStatus("System Error: Geolocation unavailable.");
            return;
        }
        setLocationStatus("Acquiring signal...");
        navigator.geolocation.getCurrentPosition(
            (position) => {
                const coords = { lat: position.coords.latitude, lng: position.coords.longitude };
                updateCoords(coords);
                setTempMode('USER');
                setLocationStatus("Coordinates confirmed.");
                setUploadMsg('');
            },
            (error) => {
                console.error(error);
                setLocationStatus("Signal lost. Defaulting to Random.");
                setTempMode('RANDOM');
            }
        );
    };

    const handleFileUpload = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        const MAX_SIZE_MB = 5;
        if (file.size > MAX_SIZE_MB * 1024 * 1024) {
            setUploadMsg(`Error: Image is too large. Please select a file under ${MAX_SIZE_MB}MB.`);
            e.target.value = null;
            return;
        }

        setIsUploading(true);
        setUploadMsg("Uploading to cloud storage...");

        const formData = new FormData();
        formData.append("file", file);

        try {
            const response = await fetch(`${baseUrlAPI}/api/user/background`, {
                method: "POST",
                headers: { 'Authorization': `Bearer ${token}` },
                body: formData
            });

            if (response.ok) {
                await response.text();
                setUploadMsg("Transmission complete. Custom background applied.");
                setTempMode('CUSTOM');
            } else {
                const errorText = await response.text();
                setUploadMsg(`Error: ${errorText}`);
            }
        } catch (error) {
            setUploadMsg(`Error: ${error.message}`);
        } finally {
            setIsUploading(false);
            e.target.value = null;
        }
    };

    const handleSave = async () => {
        setIsSaving(true);

        updateMode(tempMode);
        localStorage.setItem('crossrow_bg_blur', enableBlur);

        if (tempModel && tempModel !== originalModel) {
            try {
                await fetch(`${baseUrlAPI}/api/user/model-preference`, {
                    method: 'PUT',
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ model: tempModel })
                });
            } catch (error) {
                console.error("Failed to update model preference:", error);
            }
        }

        setIsSaving(false);

        // 【核心修改】：把 '/' 改成 -1，实现原路返回
        if (navigateWithTransition) {
            navigateWithTransition(-1);
        } else {
            navigate(-1);
        }
    };

    const selectTheme = (mode) => {
        setTempMode(mode);
        setLocationStatus('');
        setUploadMsg('');
    };

    return (
        <div className="relative min-h-screen font-serif overflow-hidden flex items-center justify-center">
            <div className="absolute inset-0 bg-white/20 backdrop-blur-sm z-0" />

            <Navbar />

            <div className="relative z-10 w-full max-w-5xl px-4 md:px-6 py-6 flex justify-center mt-8">
                <div className="w-full bg-white/80 backdrop-blur-xl rounded-3xl shadow-2xl p-8 md:p-10 text-center transform transition-all duration-500 max-h-[95vh] overflow-y-auto custom-scrollbar">

                    <h2 className="text-3xl md:text-4xl font-bold text-slate-700 mb-8 tracking-wider vn-title-container">
                        Configuration
                    </h2>

                    {/* ==================================== */}
                    {/* 模块 1：背景设置                     */}
                    {/* ==================================== */}
                    <div className="mb-6">
                        <p className="text-slate-400 text-base md:text-lg mb-4 uppercase tracking-[0.2em]">
                            - Background Source -
                        </p>

                        <div className="flex flex-wrap justify-center items-center gap-2 md:gap-4">
                            {/* 新增了 DEFAULT 选项 */}
                            <OptionButton label="Default" isSelected={tempMode === 'DEFAULT'} onClick={() => selectTheme('DEFAULT')} />
                            <OptionButton label="Random" isSelected={tempMode === 'RANDOM'} onClick={() => selectTheme('RANDOM')} />
                            <OptionButton label="Nature" isSelected={tempMode === 'NATURE'} onClick={() => selectTheme('NATURE')} />
                            <OptionButton label="Urban" isSelected={tempMode === 'URBAN'} onClick={() => selectTheme('URBAN')} />
                            <OptionButton label="Landmark" isSelected={tempMode === 'LANDMARK'} onClick={() => selectTheme('LANDMARK')} />

                            <div className="hidden md:block w-px h-6 bg-slate-300/50" />
                            <OptionButton label="Local (GPS)" isSelected={tempMode === 'USER'} onClick={handleLocationRequest} />

                            <div className="hidden md:block w-px h-6 bg-slate-300/50" />
                            <OptionButton label="Custom" isSelected={tempMode === 'CUSTOM'} onClick={() => selectTheme('CUSTOM')} />
                        </div>

                        <div className="h-5 mt-2 flex items-center justify-center">
                            <span className={`text-xs md:text-sm font-sans tracking-wide transition-opacity duration-300 ${locationStatus ? 'opacity-100 text-blue-500' : 'opacity-0'}`}>
                                {locationStatus}
                            </span>
                        </div>

                        <div className={`transition-all duration-500 overflow-hidden ${tempMode === 'CUSTOM' ? 'max-h-40 opacity-100' : 'max-h-0 opacity-0'}`}>
                            <div className="flex flex-col items-center p-4 md:p-6 bg-slate-50/50 rounded-xl border border-slate-200/50 mx-auto max-w-md mt-2">
                                <input type="file" accept="image/*" id="custom-bg-upload" className="hidden" onChange={handleFileUpload} disabled={isUploading} />
                                <label htmlFor="custom-bg-upload" className={`group relative px-6 py-2 border border-slate-300 rounded hover:border-blue-400 transition-colors ${isUploading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}>
                                    <span className="font-sans text-xs md:text-sm tracking-widest text-slate-500 group-hover:text-blue-500 transition-colors">
                                        {isUploading ? '[ UPLOADING... ]' : '[ SELECT IMAGE ]'}
                                    </span>
                                </label>
                                <span className="mt-3 text-xs font-sans tracking-wide text-blue-400">
                                    {uploadMsg || <span className="text-slate-400">Upload an image to initialize your world.</span>}
                                </span>
                            </div>
                        </div>
                    </div>

                    <div className="w-2/3 mx-auto border-t border-slate-200/50 my-6"></div>

                    {/* ==================================== */}
                    {/* 模块 2：视觉特效设置 (新增)          */}
                    {/* ==================================== */}
                    <div className="mb-6">
                        <p className="text-slate-400 text-base md:text-lg mb-4 uppercase tracking-[0.2em]">
                            - Visual Effects -
                        </p>
                        <div className="flex justify-center items-center gap-4 md:gap-8">
                            <OptionButton label="Cinematic (Blur & Dim)" isSelected={enableBlur} onClick={() => setEnableBlur(true)} />
                            <OptionButton label="Clear Reality" isSelected={!enableBlur} onClick={() => setEnableBlur(false)} />
                        </div>
                    </div>

                    <div className="w-2/3 mx-auto border-t border-slate-200/50 my-6"></div>

                    {/* ==================================== */}
                    {/* 模块 3：AI 模型选择                  */}
                    {/* ==================================== */}
                    <div className="mb-4">
                        <p className="text-slate-400 text-base md:text-lg mb-4 uppercase tracking-[0.2em]">
                            - Neural Core -
                        </p>
                        {availableModels.length > 0 ? (
                            <div className="flex flex-wrap justify-center items-center gap-2 md:gap-4">
                                {availableModels.map((modelName) => (
                                    <OptionButton key={modelName} label={modelName} isSelected={tempModel === modelName} onClick={() => setTempModel(modelName)} />
                                ))}
                            </div>
                        ) : (
                            <p className="text-slate-400 font-sans text-sm">Loading neural pathways...</p>
                        )}
                    </div>

                    {/* ==================================== */}
                    {/* 底部操作栏                           */}
                    {/* ==================================== */}
                    <div className="flex justify-center gap-8 mt-8 pt-6 border-t border-slate-100">
                        <OptionButton label={isSaving ? "Saving..." : "Confirm"} isSelected={false} size="text-lg md:text-xl" onClick={handleSave} />

                        {/* 【核心修改】：把 onClick={() => navigate('/')} 改成 navigate(-1) */}
                        <OptionButton label="Cancel" isSelected={false} size="text-lg md:text-xl" onClick={() => {
                            if (navigateWithTransition) {
                                navigateWithTransition(-1);
                            } else {
                                navigate(-1);
                            }
                        }} />
                    </div>

                </div>
            </div>
        </div>
    );
}