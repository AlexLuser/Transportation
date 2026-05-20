<template>
    <div class="dashboard">

        <!-- 欢迎卡片 -->
        <el-card shadow="never" class="welcome-card">
            <div class="welcome-body">
                <div class="welcome-text">
                    <div class="welcome-title">你好，{{ userStore.userInfo?.username }} 👋</div>
                    <div class="welcome-sub">欢迎使用智能物流管理系统后台，请从左侧菜单选择功能模块</div>
                </div>
                <el-tag type="danger" size="large" effect="plain">系统管理员</el-tag>
            </div>
        </el-card>

        <!-- 模块入口卡片 -->
        <el-row :gutter="16">
            <el-col :span="6" v-for="mod in modules" :key="mod.path">
                <el-card
                    shadow="hover"
                    class="module-card"
                    @click="router.push(mod.path)"
                >
                    <div class="module-body">
                        <div class="module-icon" :style="{ backgroundColor: mod.bgColor }">
                            <el-icon :size="28" :style="{ color: mod.iconColor }">
                                <component :is="mod.icon" />
                            </el-icon>
                        </div>
                        <div class="module-info">
                            <div class="module-name">{{ mod.name }}</div>
                            <div class="module-desc">{{ mod.desc }}</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 数据概览 -->
        <el-row :gutter="16">
            <el-col :span="8" v-for="stat in stats" :key="stat.label">
                <el-card shadow="never" class="stat-card" v-loading="stat.loading">
                    <div class="stat-body">
                        <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
                        <div class="stat-label">{{ stat.label }}</div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

    </div>
</template>

<script setup lang="ts" name="AdminDashboard">
    import { reactive, onMounted, markRaw } from 'vue';
    import { useRouter } from 'vue-router';
    import { useUserStore } from '@/stores/userStore';
    import { List, ShoppingBag, UserFilled, Connection, Van } from '@element-plus/icons-vue';
    import { getWarehouses } from '@/api/shop';
    import { adminSearchProducts } from '@/api/mall';
    import { getAllOrders } from '@/api/order';

    const router = useRouter();
    const userStore = useUserStore();

    const modules = [
        { name: '订单管理', desc: '查看和处理所有订单', path: '/admin/home/orders', icon: markRaw(List), bgColor: '#ecf5ff', iconColor: '#409eff' },
        { name: '商品管理', desc: '浏览平台全部商品', path: '/admin/home/goods', icon: markRaw(ShoppingBag), bgColor: '#fdf6ec', iconColor: '#e6a23c' },
        { name: '用户管理', desc: '查询系统用户信息', path: '/admin/home/users', icon: markRaw(UserFilled), bgColor: '#f0f9eb', iconColor: '#67c23a' },
        { name: '全国干线', desc: '干线调度、全国网络、流量规划', path: '/admin/home/national', icon: markRaw(Connection), bgColor: '#e8f4ff', iconColor: '#409eff' },
        { name: '末端配送', desc: '城市调度、配送批次、中转站、末端仓库', path: '/admin/home/lastmile', icon: markRaw(Van), bgColor: '#fef0f0', iconColor: '#f56c6c' },
    ];

    const stats = reactive([
        { label: '仓库总数',   value: '-', color: '#409eff', loading: true },
        { label: '平台商品数', value: '-', color: '#e6a23c', loading: true },
        { label: '待揽件订单', value: '-', color: '#67c23a', loading: true },
    ]);

    onMounted(async () => {
        // 仓库总数：直接取列表长度
        try {
            const res = await getWarehouses();
            stats[0].value = String((res.data as any[])?.length ?? 0);
        } catch {
            stats[0].value = '-';
        } finally {
            stats[0].loading = false;
        }

        // 平台商品数：使用 /products/search（管理员可访问全部状态商品，含上架/下架/待审核）
        try {
            const res = await adminSearchProducts({ current: 1, size: 1 });
            stats[1].value = String(res.data?.total ?? 0);
        } catch {
            stats[1].value = '-';
        } finally {
            stats[1].loading = false;
        }

        // 待揽件订单数：使用管理员订单接口查 status=2（待揽件 = 已付款待司机接单）
        try {
            const res = await getAllOrders({ current: 1, size: 1, status: 2 });
            stats[2].value = String(res.data?.total ?? 0);
        } catch {
            stats[2].value = '-';
        } finally {
            stats[2].loading = false;
        }
    });
</script>

<style scoped>
    .dashboard { display: flex; flex-direction: column; gap: 16px; }

    /* 欢迎卡片 */
    .welcome-card { border-radius: 8px; }
    .welcome-body { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; }
    .welcome-title { font-size: 20px; font-weight: 700; color: #303133; margin-bottom: 6px; }
    .welcome-sub { font-size: 14px; color: #909399; }

    /* 模块入口 */
    .module-card { border-radius: 8px; cursor: pointer; transition: transform 0.2s; }
    .module-card:hover { transform: translateY(-3px); }
    .module-body { display: flex; align-items: center; gap: 14px; }
    .module-icon { width: 54px; height: 54px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .module-name { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 4px; }
    .module-desc { font-size: 12px; color: #909399; }

    /* 统计卡片 */
    .stat-card { border-radius: 8px; }
    .stat-body { text-align: center; padding: 8px 0; }
    .stat-value { font-size: 36px; font-weight: 700; line-height: 1.2; }
    .stat-label { font-size: 13px; color: #909399; margin-top: 6px; }
</style>
