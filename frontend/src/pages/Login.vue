<template>
    <div class="login-container">
        <el-card>
            <h2>智能物流管理系统</h2>
            <el-form>
                <el-form-item label-width="100px" label="用户名">
                    <el-input v-model="username" placeholder="请输入用户名" />
                </el-form-item>
                <el-form-item label-width="100px" label="密码">
                    <el-input v-model="password" show-password placeholder="请输入密码" type="password" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="handleLogin" style="width: 100%;" :loading="loading">登录</el-button>
                </el-form-item>
            </el-form>
        </el-card>
    </div>
</template>

<script setup lang="ts" name="Login">
    import { ref } from 'vue';
    import { router, roleHomeMap } from '@/router';
    import { login } from '@/api/auth';
    import { useUserStore } from '@/stores/userStore';
    import { ElMessage } from 'element-plus';
    
    const username = ref('');
    const password = ref('');
    const userStore = useUserStore();
    const loading = ref(false);

    const handleLogin = async () => {
        if(!username.value || !password.value) {
            ElMessage.error('请输入用户名和密码');
            return;
        }
        loading.value = true;
        try {
            const res = await login({
                username: username.value,
                password: password.value
            });

            userStore.setUser(res.data.token, res.data.userInfo);
            router.replace(roleHomeMap[res.data.userInfo.roleCode]);
        }
        catch (error) {
            console.error(error);
        }
        finally {
            loading.value = false;
        }
    }
</script>

<style scoped>
    .login-container {
        display: flex;
        justify-content: flex-end;
        align-items: center;
        height: 100vh;
        padding-right: 10%;
        overflow: hidden;
        background-image: url('@/assets/images/LoginBackground.png');
        background-size: cover;
        background-position: center;
    }

    :deep(.el-card) {
        background-color: rgba(255, 255, 255, 0.92);
        backdrop-filter: blur(10px);
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
        padding: 15px;
        width: 400px;
        border-radius: 8px;
    }

    h2{
        text-align: center;
        margin-bottom: 20px;
        color: #409eff
    }

</style>