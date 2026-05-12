<template>
    <div class="shop-home-container">
        <el-container>
            <el-aside width="200px">
                <div class="aside-logo">智能物流</div>
                <el-menu router :default-active="$route.path">
                    <el-menu-item index="/merchant/home/products">
                        <el-icon><Box /></el-icon>
                        <span>货物管理</span>
                    </el-menu-item>
                    <el-menu-item index="/merchant/home/orders">
                        <el-icon><List /></el-icon>
                        <span>发货订单</span>
                    </el-menu-item>
                    <el-menu-item index="/merchant/home/stock">
                        <el-icon><Van /></el-icon>
                        <span>库存管理</span>
                    </el-menu-item>
                    <el-menu-item index="/merchant/home/warehouse">
                        <el-icon><OfficeBuilding /></el-icon>
                        <span>仓储管理</span>
                    </el-menu-item>
                    <el-menu-item index="/merchant/home/profile">
                        <el-icon><User /></el-icon>
                        <span>企业信息</span>
                    </el-menu-item>
                </el-menu>
            </el-aside>
            <el-container>
                <el-header>
                    <span class="header-title">智能物流管理系统</span>
                    <div class="header-right">
                        <el-tag type="warning" size="small" effect="plain" class="role-tag">货主</el-tag>
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

<script setup lang="ts" name="ShopHome">
    import { logout } from '@/api/auth';
    import { router } from '@/router';
    import { useUserStore } from '@/stores/userStore';
    import { Box, List, User, Van, OfficeBuilding } from '@element-plus/icons-vue';

    const userStore = useUserStore();

    const handleLogout = () => {
        logout().finally(() => {
            userStore.logout();
            router.push('/login');
        });
    }
</script>

<style scoped>
    .shop-home-container {
        width: 100%;
        height: 100%;
    }

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