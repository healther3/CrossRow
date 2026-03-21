import React from 'react';

export default function ImageModal({ imageUrl, onClose }) {
    if (!imageUrl) return null;

    return (
        <div className="fixed inset-0 z-[999] flex items-center justify-center bg-black/85 backdrop-blur-sm animate-fade-in">
            <button
                onClick={onClose}
                className="absolute top-6 right-8 text-white/50 hover:text-white text-4xl font-sans transition-colors cursor-pointer"
                title="Close"
            >
                &times;
            </button>
            <div className="relative max-w-5xl max-h-[85vh] p-4 bg-white/5 rounded-2xl shadow-[0_0_50px_rgba(0,0,0,0.8)] border border-white/10">
                <img
                    src={imageUrl}
                    alt="Generated Output"
                    className="max-w-full max-h-full object-contain rounded-lg"
                />
            </div>
        </div>
    );
}