<template>
    <div class="hubs-container">
        <el-card>
            <template #header>
                <div class="card-header">
                    <span>物流中转站管理</span>
                    <el-button type="primary" size="small" @click="openCreateDialog">
                        <el-icon><Plus /></el-icon> 新增中转站
                    </el-button>
                </div>
            </template>

            <el-table :data="hubs" stripe v-loading="loading" style="width:100%">
                <el-table-column prop="id" label="ID" width="70" />
                <el-table-column prop="name" label="中转站名称" min-width="160" />
                <el-table-column prop="region" label="区域" width="90" />
                <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
                <el-table-column label="坐标" width="190">
                    <template #default="{ row }">
                        <span class="coord-text">{{ row.latitude?.toFixed(4) }}, {{ row.longitude?.toFixed(4) }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="maxCapacity" label="最大容量" width="100" align="center" />
                <el-table-column prop="currentLoad" label="当前负载" width="100" align="center" />
                <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                        <el-tag :type="statusTagType(row.status)" size="small">
                            {{ statusLabel(row.status) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center">
                    <template #default="{ row }">
                        <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 新增/编辑对话框 -->
        <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑中转站' : '新增中转站'" width="500px">
            <el-form :model="form" label-width="100px">
                <el-form-item label="名称" required>
                    <el-input v-model="form.name" placeholder="如：上海浦东分拨中心" />
                </el-form-item>
                <el-form-item label="地址" required>
                    <el-input v-model="form.address" placeholder="详细地址" />
                </el-form-item>
                <el-form-item label="区域">
                    <el-input v-model="form.region" placeholder="如：浦东" />
                </el-form-item>
                <el-form-item label="纬度" required>
                    <el-input-number v-model="form.latitude" :precision="6" :step="0.001" style="width:100%" />
                </el-form-item>
                <el-form-item label="经度" required>
                    <el-input-number v-model="form.longitude" :precision="6" :step="0.001" style="width:100%" />
                </el-form-item>
                <el-form-item label="最大容量">
                    <el-input-number v-model="form.maxCapacity" :min="1" style="width:100%" />
                </el-form-item>
                <el-form-item label="状态" v-if="isEdit">
                    <el-select v-model="form.status" style="width:100%">
                        <el-option :value="0" label="正常" />
                        <el-option :value="1" label="满载" />
                        <el-option :value="2" label="关闭" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注">
                    <el-input v-model="form.remark" type="textarea" :rows="2" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { getHubs, createHub, updateHub, deleteHub } from '@/api/logistics';

const hubs = ref<any[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const form = ref<any>({ name: '', address: '', region: '', latitude: 31.2, longitude: 121.4, maxCapacity: 1000, status: 0, remark: '' });

const statusLabel = (status: number) => ({ 0: '正常', 1: '满载', 2: '关闭' }[status] ?? '未知');
const statusTagType = (status: number) => ({ 0: 'success', 1: 'warning', 2: 'danger' }[status] ?? 'info');

const fetchHubs = async () => {
    loading.value = true;
    try {
        const res = await getHubs();
        hubs.value = res.data || [];
    } catch {
        ElMessage.error('加载中转站列表失败');
    } finally {
        loading.value = false;
    }
};

const openCreateDialog = () => {
    isEdit.value = false;
    form.value = { name: '', address: '', region: '', latitude: 31.2, longitude: 121.4, maxCapacity: 1000, status: 0, remark: '' };
    dialogVisible.value = true;
};

const openEditDialog = (row: any) => {
    isEdit.value = true;
    form.value = { ...row };
    dialogVisible.value = true;
};

const handleSubmit = async () => {
    if (!form.value.name || !form.value.address || form.value.latitude == null || form.value.longitude == null) {
        ElMessage.warning('请填写必填项（名称、地址、坐标）');
        return;
    }
    submitting.value = true;
    try {
        if (isEdit.value) {
            await updateHub(form.value.id, form.value);
            ElMessage.success('更新成功');
        } else {
            await createHub(form.value);
            ElMessage.success('创建成功');
        }
        dialogVisible.value = false;
        fetchHubs();
    } catch (e: any) {
        ElMessage.error(e?.response?.data?.message || '操作失败');
    } finally {
        submitting.value = false;
    }
};

const handleDelete = async (id: number) => {
    await ElMessageBox.confirm('确定要关闭该中转站吗？', '确认', { type: 'warning' });
    try {
        await deleteHub(id);
        ElMessage.success('已关闭');
        fetchHubs();
    } catch {
        ElMessage.error('操作失败');
    }
};

onMounted(fetchHubs);
</script>

<style scoped>
.hubs-container { padding: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.coord-text { font-family: monospace; font-size: 12px; color: #606266; }
</style>
