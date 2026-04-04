import request from '@/utils/request'

interface LoginRequest {
    username: string;
    password: string;
}

export const login = (data: LoginRequest) => request.post('/auth/login', data);

export const logout = () => request.post('/auth/logout');