import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/userStore'
import { router } from '@/router'

const request = axios.create({
    baseURL: '/api',
    timeout: 60000
})

request.interceptors.request.use(
    config => {
        const userStore = useUserStore();
        if(userStore.token) {
            config.headers.Authorization = `Bearer ${userStore.token}`;
        }
        return config;
    },
    error => {
        return Promise.reject(error);
    }
)

request.interceptors.response.use(
    response => {
        const res = response.data;
        if(res.code !== 200) {
            ElMessage.error(res.message || '操作失败');
            return Promise.reject(res.message);
        }
        return res;
    },
    error => {
        if(error.response?.status === 401) {
            ElMessage.error('未授权，请先登录');
            const userStore = useUserStore();
            userStore.logout();
            router.replace('/login');
        } else {
            ElMessage.error(error.response?.data.message || '操作失败');
        }
        return Promise.reject(error);
    }
)

export default request;