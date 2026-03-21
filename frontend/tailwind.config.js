/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            fontFamily: {
                // Apple 风格导航栏
                sans: ['"SF Pro Text"', '"Myriad Set Pro"', '"SF Pro Icons"', '"Helvetica Neue"', 'Helvetica', 'Arial', 'sans-serif'],
                // 游戏标题风格，使用优雅的衬线字体
                serif: ['"Times New Roman"', 'Times', 'serif'],
            },
            colors: {
                // 蓝色渐变
                'title-blue-start': '#003366', // 深蓝
                'title-blue-end': '#0099cc',   // 亮青
            },
            backgroundImage: {
                // 背景,可添加图片
                'soft-gradient': 'linear-gradient(to bottom, #ffffff, #f0f8ff)',
            }
        },
    },
    plugins: [],
}