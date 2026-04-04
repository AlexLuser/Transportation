import {defineStore} from 'pinia'

interface UserInfo {
    userId: number;
    username: string;
    roleCode: string;
    roleName: string;
}

export const useUserStore = defineStore('user', {
    state: () => ({
        token: localStorage.getItem('token') || null,
        userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null') as UserInfo | null
    }),
    actions: {
        setUser(token: string, userInfo: UserInfo) {
            this.token = token;
            this.userInfo = userInfo;
            localStorage.setItem('token', token);
            localStorage.setItem('userInfo', JSON.stringify(userInfo));
        },
        logout() {
            this.token = null;
            this.userInfo = null;
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
        }
    }
})