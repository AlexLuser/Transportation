<template>
    <div class="driver-home-container">
        <el-container>
            <el-aside width="200px">
                <div class="aside-logo">智能物流 · 运输</div>
                <el-menu router :default-active="route.path">
                    <el-menu-item index="/driver/home/navigation">
                        <el-icon><Guide /></el-icon>
                        <span>末端路线</span>
                    </el-menu-item>
                    <el-menu-item index="/driver/home/deliveries">
                        <el-icon><Van /></el-icon>
                        <span>配送任务</span>
                    </el-menu-item>
                    <el-menu-item index="/driver/home/vehicles">
                        <el-icon><List /></el-icon>
                        <span>我的车辆</span>
                    </el-menu-item>
                    <el-menu-item index="/driver/home/profile">
                        <el-icon><User /></el-icon>
                        <span>个人信息</span>
                    </el-menu-item>
                    <el-menu-item index="/driver/home/gps-test">
                        <el-icon><Location /></el-icon>
                        <span>定位测试</span>
                    </el-menu-item>
                </el-menu>
            </el-aside>
            <el-container>
                <el-header>
                    <span class="header-title">智能物流管理系统</span>
                    <div class="header-right">
                        <el-tag type="success" size="small" effect="plain" class="role-tag">运输员</el-tag>
                        <span class="header-user">{{ userStore.userInfo?.username }}</span>
                        <el-button type="danger" plain size="small" @click="handleLogout">退出登录</el-button>
                    </div>
                </el-header>
                <el-main>
                    <el-alert
                        v-if="inProgressCount > 0 && route.path !== '/driver/home/navigation'"
                        type="info"
                        show-icon
                        class="nav-hint"
                        :closable="false"
                    >
                        当前有 {{ inProgressCount }} 单配送任务进行中，
                        <el-button type="primary" link @click="$router.push('/driver/home/navigation')">查看计划路线</el-button>
                    </el-alert>
                    <router-view />
                </el-main>
            </el-container>
        </el-container>
    </div>
</template>

<script setup lang="ts" name="DriverHome">
    import { ref, onMounted, watch } from 'vue';
    import { useRoute } from 'vue-router';
    import { logout } from '@/api/auth';
    import { getInProgressDeliveries } from '@/api/driver';
    import { router } from '@/router';
    import { useUserStore } from '@/stores/userStore';
    import { Van, List, User, Guide, Location } from '@element-plus/icons-vue';

    const userStore = useUserStore();
    const route = useRoute();
    const inProgressCount = ref(0);

    const refreshInProgress = async () => {
        try {
            const res = await getInProgressDeliveries();
            const driverTasks = (res.data ?? []).filter((d: any) => d.segmentType !== 1);
            inProgressCount.value = driverTasks.length;
        } catch {
            inProgressCount.value = 0;
        }
    };

    onMounted(refreshInProgress);
    watch(() => route.path, refreshInProgress);

    const handleLogout = () => {
        logout().finally(() => {
            userStore.logout();
            router.push('/login');
        });
    }
</script>

<style scoped>
    .driver-home-container { width: 100%; height: 100%; }

    .el-aside {
        background-color: #304156;
        height: 100vh;
    }

    .aside-logo {
        height: 60px;
        line-height: 60px;
        text-align: center;
        color: #fff;
        font-size: 18px;
        font-weight: bold;
        background-color: #263445;
    }

    .el-menu {
        border-right: none;
        background-color: #304156;
    }

    :deep(.el-menu-item) {
        color: #bfcbd9;
    }

    :deep(.el-menu-item:hover),
    :deep(.el-menu-item.is-active) {
        background-color: #263445;
        color: #409eff;
    }

    .el-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid #e6e6e6;
        background-color: #fff;
    }

    .header-title {
        font-size: 16px;
        font-weight: bold;
        color: #303133;
    }

    .header-right {
        display: flex;
        align-items: center;
        gap: 12px;
    }

    .role-tag {
        font-size: 11px;
    }

    .header-user {
        color: #606266;
        font-size: 14px;
    }

    .el-main {
        background-color: #f0f2f5;
        padding: 20px;
    }

    .nav-hint { margin-bottom: 16px; border-radius: 8px; }
</style>
