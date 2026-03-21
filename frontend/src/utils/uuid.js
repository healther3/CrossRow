// src/utils/uuid.js

// 一个简单的 UUID 生成器
export function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

//以此获取或生成用户的永久ID
export function getUserId() {
    let userId = localStorage.getItem('app_user_id');
    if (!userId) {
        userId = generateUUID();
        localStorage.setItem('app_user_id', userId);
    }
    return userId;
}