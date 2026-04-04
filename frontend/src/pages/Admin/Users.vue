<template>
    <div class="admin-users">

        <div class="toolbar">
            <div class="toolbar-left">
                <el-input
                    v-model="searchInput"
                    placeholder="用户 ID 或用户名"
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
                <el-table-column label="用户ID" prop="id" width="90" align="center" />
                <el-table-column label="用户名" prop="username" min-width="160" show-overflow-tooltip />
                <el-table-column label="角色" width="120">
                    <template #default="{ row }">
                        <el-tag :type="permissionTagType(row.permission)" size="small">
                            {{ permissionLabel(row.permission) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="权限等级" prop="permission" width="100" align="center" />
                <el-table-column label="角色编码" width="110">
                    <template #default="{ row }">{{ permissionCode(row.permission) }}</template>
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

    </div>
</template>

<script setup lang="ts" name="AdminUsers">
    import { ref, computed, onMounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { Search, Refresh } from '@element-plus/icons-vue';
    import { getAllUsers } from '@/api/order';
    import request from '@/utils/request';

    const ROLES = [
        { value: 1, label: '管理员' },
        { value: 2, label: '顾客用户' },
        { value: 3, label: '商户用户' },
        { value: 4, label: '运输员' },
    ];

    const loading = ref(false);
    const users = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(15);
    const showPagination = ref(true);

    const searchInput = ref('');
    const roleFilter = ref<number | null>(null);

    // 前端按角色筛选（全量模式下）
    const displayUsers = computed(() => {
        if (roleFilter.value === null) return users.value;
        return users.value.filter(u => u.permission === roleFilter.value);
    });

    // ==================== 数据加载 ====================

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

    /** 按ID精确查询单个用户 */
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

    // ==================== 交互处理 ====================

    const handleSearch = () => {
        const val = searchInput.value.trim();
        if (!val) {
            currentPage.value = 1;
            fetchAllUsers();
            return;
        }
        // 纯数字：优先按 ID 精确查
        if (/^\d+$/.test(val)) {
            fetchById(val);
        } else {
            // 非数字：按用户名关键词模糊搜
            currentPage.value = 1;
            fetchAllUsers(val);
        }
    };

    const handleClear = () => {
        currentPage.value = 1;
        fetchAllUsers();
    };

    const handleRoleChange = () => {
        // 角色筛选为前端过滤，不触发接口
    };

    const handleReset = () => {
        searchInput.value = '';
        roleFilter.value = null;
        currentPage.value = 1;
        fetchAllUsers();
    };

    // ==================== 工具函数 ====================

    const PERMISSION_MAP: Record<number, { label: string; code: string; tag: string }> = {
        1: { label: '管理员',   code: 'admin',    tag: 'danger' },
        2: { label: '顾客用户', code: 'customer', tag: 'primary' },
        3: { label: '商户用户', code: 'shop',     tag: 'warning' },
        4: { label: '运输员',   code: 'driver',   tag: 'success' },
    };
    const permissionLabel   = (p: number) => PERMISSION_MAP[p]?.label ?? '未知';
    const permissionCode    = (p: number) => PERMISSION_MAP[p]?.code  ?? '-';
    const permissionTagType = (p: number) => PERMISSION_MAP[p]?.tag   ?? 'info';

    onMounted(() => fetchAllUsers());
</script>

<style scoped>
    .admin-users { display: flex; flex-direction: column; gap: 16px; }
    .toolbar {
        display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;
        background: #fff; padding: 14px 20px; border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0,0,0,0.08);
    }
    .toolbar-left { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .search-input { width: 220px; }
    .role-select { width: 110px; }
    .result-hint { font-size: 13px; color: #606266; white-space: nowrap; }
    .table-card { border-radius: 8px; }
    .pagination-bar { display: flex; justify-content: flex-end; padding: 16px 0 4px; }
</style>
