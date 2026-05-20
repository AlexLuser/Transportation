import request from '@/utils/request'

interface LoginRequest {
    username: string;
    password: string;
}

interface RegisterRequest {
    username: string;
    password: string;
    role: string;
    shopName?: string;
    shopPhone?: string;
    shopEmail?: string;
    businessLicense?: string;
    realName?: string;
    phone?: string;
    email?: string;
    licenseNumber?: string;
    licenseType?: string;
    licenseExpireDate?: string;
}

export const login = (data: LoginRequest) => request.post('/auth/login', data);

export const logout = () => request.post('/auth/logout');

export const register = (data: RegisterRequest) => request.post('/auth/register', data);

export const getPendingUsers = () => request({ url: '/users/pending', method: 'GET' });

export const reviewUser = (userId: number, approve: boolean) =>
    request({ url: `/users/${userId}/review`, method: 'PUT', data: { approve } });