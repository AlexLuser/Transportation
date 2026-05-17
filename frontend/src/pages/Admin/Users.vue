<template>
    <div class="admin-users">

        <el-tabs v-model="activeTab" @tab-change="handleTabChange">

            <!-- ===================== Tab 1: 用户列表 ===================== -->
            <el-tab-pane label="用户列表" name="list">
                <div class="toolbar">
                    <div class="toolbar-left">
                        <el-input
                            v-model="searchInput"
                            placeholder="用户编号或用户名"
                            clearable
                            class="search-input"
                            @keyup.enter="handleSearch"
                            @clear="handleClear"
                        />
                        <el-select v-model="roleFilter" placeholder="全部角色" clearable class="role-select" @change="handleRoleChange">
                            <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
                        </el-select>
                        <el-button type="primary" :icon="Search" @click="handleSearch" :loading="loading">查询</el-button>
                        <el-button :icon="Refresh" @click="handleReset" :loading="loading">重置</el-button>
                    </div>
                    <span class="result-hint">共 <strong>{{ total }}</strong> 名用户</span>
                </div>

                <el-card shadow="never" class="table-card" v-loading="loading">
                    <el-table :data="displayUsers" stripe>
                        <el-table-column label="用户编号" prop="id" width="90" align="center" />
                        <el-table-column label="用户名" prop="username" min-width="160" show-overflow-tooltip />
                        <el-table-column label="角色" width="120">
                            <template #default="{ row }">
                                <el-tag :type="permissionTagType(row.permission)" size="small">
                                    {{ permissionLabel(row.permission) }}
                                </el-tag>
                            </template>
                        </el-table-column>
                    </el-table>

                    <div class="pagination-bar" v-if="showPagination">
                        <el-pagination
                            v-model:current-page="currentPage"
                            v-model:page-size="pageSize"
                            :page-sizes="[15, 30, 50]"
                            :total="total"
                            layout="total, sizes, prev, pager, next"
                            @current-change="fetchAllUsers"
                            @size-change="(val: number) => { pageSize = val; currentPage = 1; fetchAllUsers(); }"
                        />
                    </div>

                    <el-empty v-if="!loading && displayUsers.length === 0" description="暂无用户数据" :image-size="80" />
                </el-card>
            </el-tab-pane>

            <!-- ===================== Tab 2: 审核管理 ===================== -->
            <el-tab-pane name="review">
                <template #label>
                    <span>审核管理</span>
                    <el-badge v-if="pendingCount > 0" :value="pendingCount" class="badge-offset" />
                </template>

                <el-card shadow="never" class="table-card" v-loading="reviewLoading">
                    <el-table :data="pendingUsers" stripe>
                        <el-table-column label="用户编号" prop="userId" width="90" align="center" />
                        <el-table-column label="用户名" prop="username" width="150" show-overflow-tooltip />
                        <el-table-column label="角色" width="90" align="center">
                            <template #default="{ row }">
                                <el-tag :type="row.role === 'shop' ? 'warning' : 'success'" size="small">
                                    {{ row.role === 'shop' ? '商户' : '运输员' }}
                                </el-tag>
                            </template>
                        </el-table-column>

                        <!-- 商户专属信息 -->
                        <el-table-column label="详情" min-width="280">
                            <template #default="{ row }">
                                <template v-if="row.role === 'shop'">
                                    <div class="detail-item"><span class="detail-label">商户名：</span>{{ row.shopName }}</div>
                                    <div class="detail-item"><span class="detail-label">电话：</span>{{ row.shopPhone }}</div>
                                    <div class="detail-item"><span class="detail-label">营业执照：</span>{{ row.businessLicense }}</div>
                                    <div v-if="row.shopEmail" class="detail-item"><span class="detail-label">邮箱：</span>{{ row.shopEmail }}</div>
                                </template>
                                <template v-else>
                                    <div class="detail-item"><span class="detail-label">姓名：</span>{{ row.realName }}</div>
                                    <div class="detail-item"><span class="detail-label">电话：</span>{{ row.phone }}</div>
                                    <div class="detail-item"><span class="detail-label">驾驶证号：</span>{{ row.licenseNumber }}</div>
                                    <div class="detail-item"><span class="detail-label">证件类型：</span>{{ row.licenseType }}</div>
                                    <div class="detail-item"><span class="detail-label">到期日：</span>{{ row.licenseExpireDate }}</div>
                                </template>
                            </template>
                        </el-table-column>

                        <el-table-column label="操作" width="160" align="center" fixed="right">
                            <template #default="{ row }">
                                <el-button
                                    type="success"
                                    size="small"
                                    @click="handleReview(row, true)"
                                    :loading="row._loading"
                                >通过</el-button>
                                <el-button
                                    type="danger"
                                    size="small"
                                    @click="handleReview(row, false)"
                                    :loading="row._loading"
                                >拒绝</el-button>
                            </template>
                        </el-table-column>
                    </el-table>

                    <el-empty v-if="!reviewLoading && pendingUsers.length === 0" description="暂无待审核用户" :image-size="80" />
                </el-card>
            </el-tab-pane>

        </el-tabs>

    </div>
</template>

<script setup lang="ts" name="AdminUsers">
    import { ref, computed, onMounted } from 'vue';
    import { ElMessage, ElMessageBox } from 'element-plus';
    import { Search, Refresh } from '@element-plus/icons-vue';
    import { getAllUsers } from '@/api/order';
    import { getPendingUsers, reviewUser } from '@/api/auth';
    import request from '@/utils/request';

    // ==================== 公共 ====================
    const activeTab = ref('list');

    const ROLES = [
        { value: 1, label: '管理员' },
        { value: 2, label: '发货用户' },
        { value: 3, label: '商户用户' },
        { value: 4, label: '运输员' },
    ];

    const PERMISSION_MAP: Record<number, { label: string; code: string; tag: string }> = {
        1: { label: '管理员',   code: 'admin',    tag: 'danger' },
        2: { label: '发货用户', code: 'customer', tag: 'primary' },
        3: { label: '商户用户', code: 'shop',     tag: 'warning' },
        4: { label: '运输员',   code: 'driver',   tag: 'success' },
    };
    const permissionLabel   = (p: number) => PERMISSION_MAP[p]?.label ?? '未知';
    const permissionCode    = (p: number) => PERMISSION_MAP[p]?.code  ?? '-';
    const permissionTagType = (p: number) => PERMISSION_MAP[p]?.tag   ?? 'info';

    // ==================== Tab1: 用户列表 ====================
    const loading = ref(false);
    const users = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(15);
    const showPagination = ref(true);
    const searchInput = ref('');
    const roleFilter = ref<number | null>(null);

    const displayUsers = computed(() => {
        if (roleFilter.value === null) return users.value;
        return users.value.filter(u => u.permission === roleFilter.value);
    });

    const fetchAllUsers = async (keyword?: string) => {
        loading.value = true;
        showPagination.value = true;
        try {
            const params: any = { current: currentPage.value, size: pageSize.value };
            if (keyword) params.keyword = keyword;
            const res = await getAllUsers(params);
            users.value = res.data?.records ?? [];
            total.value = res.data?.total ?? 0;
        } finally {
            loading.value = false;
        }
    };

    const fetchById = async (id: string) => {
        loading.value = true;
        showPagination.value = false;
        try {
            const res = await request({ url: `/users/${id}`, method: 'GET' });
            users.value = res.data ? [res.data] : [];
            total.value = users.value.length;
        } catch {
            users.value = [];
            total.value = 0;
            ElMessage.warning('未找到该用户');
        } finally {
            loading.value = false;
        }
    };

    const handleSearch = () => {
        const val = searchInput.value.trim();
        if (!val) { currentPage.value = 1; fetchAllUsers(); return; }
        if (/^\d+$/.test(val)) { fetchById(val); }
        else { currentPage.value = 1; fetchAllUsers(val); }
    };

    const handleClear = () => { currentPage.value = 1; fetchAllUsers(); };
    const handleRoleChange = () => {};
    const handleReset = () => {
        searchInput.value = '';
        roleFilter.value = null;
        currentPage.value = 1;
        fetchAllUsers();
    };

    // ==================== Tab2: 审核管理 ====================
    const reviewLoading = ref(false);
    const pendingUsers = ref<any[]>([]);
    const pendingCount = computed(() => pendingUsers.value.length);

    const fetchPendingUsers = async () => {
        reviewLoading.value = true;
        try {
            const res = await getPendingUsers();
            pendingUsers.value = (res.data ?? []).map((u: any) => ({ ...u, _loading: false }));
        } finally {
            reviewLoading.value = false;
        }
    };

    const handleReview = async (row: any, approve: boolean) => {
        const action = approve ? '通过' : '拒绝';
        const roleLabel = row.role === 'shop' ? '商户' : '运输员';
        try {
            await ElMessageBox.confirm(
                `确定要${action}用户「${row.username}」的${roleLabel}注册申请吗？${!approve ? '\n拒绝后该用户账号将被删除。' : ''}`,
                `审核确认`,
                { confirmButtonText: '确定', cancelButtonText: '取消', type: approve ? 'info' : 'warning' }
            );
        } catch {
            return;
        }

        row._loading = true;
        try {
            await reviewUser(row.userId, approve);
            ElMessage.success(`已${action}用户「${row.username}」`);
            await fetchPendingUsers();
            // 同步刷新用户列表
            if (activeTab.value === 'list') fetchAllUsers();
        } finally {
            row._loading = false;
        }
    };

    const handleTabChange = (tab: string) => {
        if (tab === 'review') fetchPendingUsers();
    };

    // ==================== 初始化 ====================
    onMounted(() => {
        fetchAllUsers();
        fetchPendingUsers(); // 初始化时也加载，以显示 badge 数量
    });
</script>

<style scoped>
    .admin-users { display: flex; flex-direction: column; gap: 16px; }

    .toolbar {
        display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
        background: #fff; padding: 14px 20px; border-radius: 8px; margin-bottom: 12px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.08);
    }
    .toolbar-left { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .search-input { width: 220px; }
    .role-select { width: 110px; }
    .result-hint { font-size: 13px; color: #606266; white-space: nowrap; }
    .table-card { border-radius: 8px; }
    .pagination-bar { display: flex; justify-content: flex-end; padding: 16px 0 4px; }

    .badge-offset { margin-left: 6px; vertical-align: middle; }

    .detail-item {
        font-size: 13px;
        line-height: 1.8;
        color: #303133;
    }
    .detail-label {
        color: #909399;
        margin-right: 2px;
    }
</style>
