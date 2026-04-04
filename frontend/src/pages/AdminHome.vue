<template>
    <div class="admin-home-container">
        <el-container>
            <el-aside width="200px">
                <div class="aside-logo">智能物流 · 管理</div>
                <el-menu router :default-active="$route.path">
                    <el-menu-item index="/admin/home/dashboard">
                        <el-icon><DataBoard /></el-icon>
                        <span>系统概览</span>
                    </el-menu-item>
                    <el-menu-item index="/admin/home/orders">
                        <el-icon><List /></el-icon>
                        <span>订单管理</span>
                    </el-menu-item>
                    <el-menu-item index="/admin/home/goods">
                        <el-icon><ShoppingBag /></el-icon>
                        <span>商品管理</span>
                    </el-menu-item>
                    <el-menu-item index="/admin/home/users">
                        <el-icon><UserFilled /></el-icon>
                        <span>用户管理</span>
                    </el-menu-item>
                    <el-menu-item index="/admin/home/system">
                        <el-icon><Setting /></el-icon>
                        <span>系统管理</span>
                    </el-menu-item>
                </el-menu>
            </el-aside>
            <el-container>
                <el-header>
                    <span class="header-title">智能物流管理系统</span>
                    <div class="header-right">
                        <el-tag type="danger" size="small" effect="plain" class="role-tag">管理员</el-tag>
                        <span class="header-user">{{ userStore.userInfo?.username }}</span>
                        <el-button type="danger" plain size="small" @click="handleLogout">退出登录</el-button>
                    </div>
                </el-header>
                <el-main>
                    <router-view />
                </el-main>
            </el-container>
        </el-container>
    </div>
</template>

<script setup lang="ts" name="AdminHome">
    import { logout } from '@/api/auth';
    import { router } from '@/router';
    import { useUserStore } from '@/stores/userStore';
    import { DataBoard, List, ShoppingBag, UserFilled, Setting } from '@element-plus/icons-vue';

    const userStore = useUserStore();

    const handleLogout = () => {
        logout().finally(() => {
            userStore.logout();
            router.push('/login');
        });
    }
</script>

<style scoped>
    .admin-home-container { width: 100%; height: 100%; }

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
</style>
