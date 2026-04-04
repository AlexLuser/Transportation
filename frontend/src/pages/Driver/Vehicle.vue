<template>
    <div class="vehicle-manage">

        <div class="toolbar">
            <span class="page-title">我的车辆</span>
            <el-button type="success" :icon="Plus" @click="openAddDialog">添加车辆</el-button>
        </div>

        <el-card shadow="never" class="table-card" v-loading="loading">
            <el-table :data="vehicles" stripe>
                <el-table-column label="车牌号" prop="licensePlate" width="120" />
                <el-table-column label="车辆类型" prop="vehicleType" width="130" />
                <el-table-column label="品牌" prop="vehicleBrand" width="100" />
                <el-table-column label="型号" prop="vehicleModel" width="120" show-overflow-tooltip />
                <el-table-column label="载重(吨)" prop="loadCapacity" width="100" align="center" />
                <el-table-column label="容积(m³)" prop="volumeCapacity" width="100" align="center" />
                <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                        <el-tag :type="row.vehicleStatus === 1 ? 'success' : 'info'" size="small">
                            {{ row.vehicleStatus === 1 ? '可用' : '停用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="220" fixed="right">
                    <template #default="{ row }">
                        <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>
                        <el-button
                            size="small"
                            :type="row.vehicleStatus === 1 ? 'warning' : 'success'"
                            @click="handleToggleStatus(row)"
                        >{{ row.vehicleStatus === 1 ? '停用' : '启用' }}</el-button>
                        <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <el-empty v-if="!loading && vehicles.length === 0" description="暂无车辆，点击右上角「添加车辆」" />
        </el-card>

        <!-- 新增 / 编辑弹窗 -->
        <el-dialog v-model="formVisible" :title="isEdit ? '编辑车辆' : '添加车辆'" width="560px" align-center @closed="resetForm">
            <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px" class="vehicle-form">
                <el-row :gutter="16">
                    <el-col :span="12">
                        <el-form-item label="车牌号" prop="licensePlate">
                            <el-input v-model="formData.licensePlate" placeholder="如：沪A12345" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="车辆类型" prop="vehicleType">
                            <el-select v-model="formData.vehicleType" placeholder="选择类型" style="width:100%">
                                <el-option v-for="t in VEHICLE_TYPES" :key="t" :label="t" :value="t" />
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="品牌">
                            <el-input v-model="formData.vehicleBrand" placeholder="如：东风、解放" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="型号">
                            <el-input v-model="formData.vehicleModel" placeholder="车辆型号（选填）" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="载重(吨)">
                            <el-input-number v-model="formData.loadCapacity" :min="0" :precision="2" style="width:100%" controls-position="right" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="容积(m³)">
                            <el-input-number v-model="formData.volumeCapacity" :min="0" :precision="2" style="width:100%" controls-position="right" />
                        </el-form-item>
                    </el-col>
                </el-row>
            </el-form>
            <template #footer>
                <el-button @click="formVisible = false">取消</el-button>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">
                    {{ isEdit ? '保存修改' : '确认添加' }}
                </el-button>
            </template>
        </el-dialog>

    </div>
</template>

<script setup lang="ts" name="DriverVehicle">
    import { ref, reactive, onMounted } from 'vue';
    import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
    import { Plus } from '@element-plus/icons-vue';
    import { getMyVehicles, addVehicle, updateVehicle, deleteVehicle, updateVehicleStatus, type Vehicle } from '@/api/driver';

    const VEHICLE_TYPES = ['小型货车', '中型货车', '大型货车', '厢式货车', '冷藏车', '危险品车'];

    const loading = ref(false);
    const vehicles = ref<Vehicle[]>([]);

    const fetchVehicles = async () => {
        loading.value = true;
        try {
            const res = await getMyVehicles();
            vehicles.value = res.data ?? [];
        } finally {
            loading.value = false;
        }
    };

    // ==================== 状态切换 ====================
    const handleToggleStatus = async (row: Vehicle) => {
        const newStatus = row.vehicleStatus === 1 ? 0 : 1;
        const label = newStatus === 1 ? '启用' : '停用';
        await ElMessageBox.confirm(`确定要${label}车辆「${row.licensePlate}」吗？`, `${label}确认`, { type: 'warning' });
        await updateVehicleStatus(row.id!, newStatus);
        ElMessage.success(`车辆已${label}`);
        fetchVehicles();
    };

    // ==================== 删除 ====================
    const handleDelete = (row: Vehicle) => {
        ElMessageBox.confirm(`确定要删除车辆「${row.licensePlate}」吗？`, '删除确认', { type: 'warning', confirmButtonText: '确定删除', confirmButtonClass: 'el-button--danger' })
            .then(async () => {
                await deleteVehicle(row.id!);
                ElMessage.success('删除成功');
                fetchVehicles();
            }).catch(() => {});
    };

    // ==================== 表单 ====================
    const formVisible = ref(false);
    const isEdit = ref(false);
    const submitting = ref(false);
    const formRef = ref<FormInstance>();

    const emptyForm = (): Vehicle => ({ licensePlate: '', vehicleType: '', vehicleBrand: '', vehicleModel: '', loadCapacity: 0, volumeCapacity: 0 });
    const formData = reactive<Vehicle>(emptyForm());

    const formRules: FormRules = {
        licensePlate: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
        vehicleType: [{ required: true, message: '请选择车辆类型', trigger: 'change' }],
    };

    const openAddDialog = () => { isEdit.value = false; Object.assign(formData, emptyForm()); formVisible.value = true; };
    const openEditDialog = (row: Vehicle) => { isEdit.value = true; Object.assign(formData, { ...row }); formVisible.value = true; };
    const resetForm = () => formRef.value?.resetFields();

    const handleSubmit = async () => {
        await formRef.value?.validate();
        submitting.value = true;
        try {
            if (isEdit.value) {
                await updateVehicle({ ...formData });
                ElMessage.success('车辆信息修改成功');
            } else {
                await addVehicle({ ...formData });
                ElMessage.success('车辆添加成功');
            }
            formVisible.value = false;
            fetchVehicles();
        } finally {
            submitting.value = false;
        }
    };

    onMounted(() => fetchVehicles());
</script>

<style scoped>
    .vehicle-manage { display: flex; flex-direction: column; gap: 16px; }
    .toolbar { display: flex; align-items: center; justify-content: space-between; background: #fff; padding: 14px 20px; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
    .page-title { font-size: 16px; font-weight: 600; color: #303133; }
    .table-card { border-radius: 8px; }
    .vehicle-form { padding: 4px 8px; }
</style>
