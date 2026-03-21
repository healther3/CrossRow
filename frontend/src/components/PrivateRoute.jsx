import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function PrivateRoute({ children }) {
    const { isAuthenticated } = useAuth();
    const location = useLocation();

    if (!isAuthenticated) {
        // 如果没有登录，重定向到 login，并带上原来想去的地址，方便登录后跳回去
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    return children;
}