<template>
    <div class="admin-system">

        <div class="toolbar">
            <span class="page-title">仓库管理</span>
            <el-button :icon="Refresh" @click="fetchWarehouses">刷新</el-button>
        </div>

        <el-card shadow="never" class="table-card" v-loading="loading">
            <el-table :data="warehouses" stripe>
                <el-table-column label="仓库名称" prop="warehouseName" min-width="150" show-overflow-tooltip />
                <el-table-column label="联系电话" prop="warehousePhone" width="130" />
                <el-table-column label="所在城市" width="150">
                    <template #default="{ row }">
                        {{ [row.province, row.city, row.district].filter(Boolean).join(' ') || '-' }}
                    </template>
                </el-table-column>
                <el-table-column label="详细地址" prop="detailAddress" min-width="180" show-overflow-tooltip />
                <el-table-column label="容量" width="100" align="center">
                    <template #default="{ row }">
                        <span v-if="row.capacity === 0" class="text-muted">无限制</span>
                        <span v-else>{{ row.capacity }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                            {{ row.status === 1 ? '启用' : '禁用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" @click="openDetail(row)">详情</el-button>
                        <el-button size="small" type="primary" @click="openEditCapacity(row)">修改容量</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && warehouses.length === 0" description="暂无仓库数据" />
        </el-card>

        <!-- 仓库详情弹窗 -->
        <el-dialog v-model="detailVisible" title="仓库详情" width="580px" align-center>
            <div v-if="detailWarehouse">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="仓库名称" :span="2">{{ detailWarehouse.warehouseName }}</el-descriptions-item>
                    <el-descriptions-item label="联系电话">{{ detailWarehouse.warehousePhone || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="邮编">{{ detailWarehouse.postalCode || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="省份">{{ detailWarehouse.province || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="城市">{{ detailWarehouse.city || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="区/县">{{ detailWarehouse.district || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="容量">
                        {{ detailWarehouse.capacity === 0 ? '无限制' : detailWarehouse.capacity }}
                    </el-descriptions-item>
                    <el-descriptions-item label="经纬度" :span="2">
                        {{ detailWarehouse.latitude ? `${detailWarehouse.latitude}, ${detailWarehouse.longitude}` : '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="详细地址" :span="2">{{ detailWarehouse.detailAddress || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="detailWarehouse.status === 1 ? 'success' : 'info'" size="small">
                            {{ detailWarehouse.status === 1 ? '启用' : '禁用' }}
                        </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="创建时间">{{ formatDate(detailWarehouse.createTime) }}</el-descriptions-item>
                </el-descriptions>
            </div>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
                <el-button type="primary" @click="openEditCapacity(detailWarehouse!)">修改容量</el-button>
            </template>
        </el-dialog>

        <!-- 修改容量弹窗 -->
        <el-dialog v-model="capacityVisible" title="修改仓库容量" width="400px" align-center>
            <el-form label-width="80px" class="capacity-form" v-if="editWarehouse">
                <el-form-item label="仓库">
                    <el-text>{{ editWarehouse.warehouseName }}</el-text>
                </el-form-item>
                <el-form-item label="容量">
                    <el-input-number v-model="newCapacity" :min="0" :precision="0" style="width:100%" controls-position="right" />
                </el-form-item>
                <div class="capacity-hint">提示：设置为 0 表示无限制容量</div>
            </el-form>
            <template #footer>
                <el-button @click="capacityVisible = false">取消</el-button>
                <el-button type="primary" :loading="submitting" @click="handleUpdateCapacity">确认修改</el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="AdminSystem">
    import { ref, onMounted } from 'vue';
    import { ElMessage } from 'element-plus';
    import { Refresh } from '@element-plus/icons-vue';
    import { getWarehouses, updateWarehouseCapacity, type Warehouse } from '@/api/shop';

    const loading = ref(false);
    const warehouses = ref<Warehouse[]>([]);

    const fetchWarehouses = async () => {
        loading.value = true;
        try {
            const res = await getWarehouses();
            warehouses.value = res.data ?? [];
        } finally {
            loading.value = false;
        }
    };

    const detailVisible = ref(false);
    const detailWarehouse = ref<Warehouse | null>(null);
    const openDetail = (row: Warehouse) => { detailWarehouse.value = row; detailVisible.value = true; };

    const capacityVisible = ref(false);
    const editWarehouse = ref<Warehouse | null>(null);
    const newCapacity = ref(0);
    const submitting = ref(false);

    const openEditCapacity = (row: Warehouse) => {
        editWarehouse.value = row;
        newCapacity.value = row.capacity ?? 0;
        detailVisible.value = false;
        capacityVisible.value = true;
    };

    const handleUpdateCapacity = async () => {
        if (!editWarehouse.value?.id) return;
        submitting.value = true;
        try {
            await updateWarehouseCapacity(editWarehouse.value.id, newCapacity.value);
            ElMessage.success('仓库容量更新成功');
            capacityVisible.value = false;
            fetchWarehouses();
        } finally {
            submitting.value = false;
        }
    };

    const formatDate = (d: string | undefined) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }) : '-';

    onMounted(() => fetchWarehouses());
</script>

<style scoped>
    .admin-system { display: flex; flex-direction: column; gap: 16px; }
    .toolbar { display: flex; align-items: center; justify-content: space-between; background: #fff; padding: 14px 20px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
    .page-title { font-size: 16px; font-weight: 600; color: #303133; }
    .table-card { border-radius: 8px; }
    .text-muted { color: #909399; }
    .capacity-form { padding: 4px 0; }
    .capacity-hint { font-size: 12px; color: #909399; padding-left: 80px; margin-top: -4px; }
</style>
