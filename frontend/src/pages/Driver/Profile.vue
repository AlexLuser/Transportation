<template>
    <div class="driver-profile">

        <!-- 头部卡片 -->
        <el-card shadow="never" class="header-card">
            <div class="driver-header">
                <div class="avatar-wrap">
                    <el-avatar :size="80" :src="driverInfo?.avatar || undefined">
                        <el-icon :size="36"><UserFilled /></el-icon>
                    </el-avatar>
                </div>
                <div class="header-info">
                    <div class="driver-name">{{ driverInfo?.realName || userStore.userInfo?.username || '未设置姓名' }}</div>
                    <div class="driver-meta">
                        <span v-if="driverInfo?.phone" class="meta-item"><el-icon><Phone /></el-icon>{{ driverInfo.phone }}</span>
                        <span v-if="driverInfo?.email" class="meta-item"><el-icon><Message /></el-icon>{{ driverInfo.email }}</span>
                        <el-tag v-if="driverInfo" :type="statusTag(driverInfo.status)" size="small">
                            {{ statusLabel(driverInfo.status) }}
                        </el-tag>
                    </div>
                </div>
                <div class="header-action">
                    <el-button v-if="driverInfo && !isEditing" type="primary" plain @click="startEdit">
                        <el-icon><Edit /></el-icon>&nbsp;编辑信息
                    </el-button>
                    <el-button v-if="!driverInfo && !isEditing" type="success" @click="startCreate">
                        <el-icon><Plus /></el-icon>&nbsp;完善信息
                    </el-button>
                </div>
            </div>
        </el-card>

        <!-- 详情 / 表单卡片 -->
        <el-card shadow="never" class="content-card" v-loading="loading">

            <!-- 查看模式 -->
            <template v-if="driverInfo && !isEditing">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="真实姓名">{{ driverInfo.realName || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="手机号">{{ driverInfo.phone || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="邮箱">{{ driverInfo.email || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="性别">
                        {{ driverInfo.gender === 1 ? '男' : driverInfo.gender === 2 ? '女' : '未知' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="身份证号">{{ driverInfo.idCard || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="生日">{{ formatDate(driverInfo.birthday) }}</el-descriptions-item>
                    <el-descriptions-item label="驾驶证号">{{ driverInfo.licenseNumber || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="驾驶证类型">{{ driverInfo.licenseType || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="驾驶证到期">{{ formatDate(driverInfo.licenseExpireDate) }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="statusTag(driverInfo.status)" size="small">{{ statusLabel(driverInfo.status) }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="头像URL" :span="2">{{ driverInfo.avatar || '—' }}</el-descriptions-item>
                </el-descriptions>
            </template>

            <!-- 空状态 -->
            <template v-if="!driverInfo && !isEditing">
                <el-empty description="您还未完善运输员信息，请点击右上角按钮填写">
                    <el-button type="success" @click="startCreate">
                        <el-icon><Plus /></el-icon>&nbsp;完善信息
                    </el-button>
                </el-empty>
            </template>

            <!-- 编辑 / 创建表单 -->
            <template v-if="isEditing">
                <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="driver-form">
                    <el-row :gutter="20">
                        <el-col :span="12">
                            <el-form-item label="真实姓名" prop="realName">
                                <el-input v-model="formData.realName" placeholder="请输入真实姓名" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="手机号">
                                <el-input v-model="formData.phone" placeholder="请输入手机号" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="邮箱">
                                <el-input v-model="formData.email" placeholder="请输入邮箱（选填）" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="性别">
                                <el-radio-group v-model="formData.gender">
                                    <el-radio :value="0">未知</el-radio>
                                    <el-radio :value="1">男</el-radio>
                                    <el-radio :value="2">女</el-radio>
                                </el-radio-group>
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="身份证号">
                                <el-input v-model="formData.idCard" placeholder="请输入身份证号" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="生日">
                                <el-date-picker v-model="formData.birthday" type="date" placeholder="选择生日" style="width:100%" value-format="YYYY-MM-DD" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="驾驶证号">
                                <el-input v-model="formData.licenseNumber" placeholder="请输入驾驶证号" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="驾驶证类型">
                                <el-select v-model="formData.licenseType" placeholder="选择类型" style="width:100%">
                                    <el-option v-for="t in LICENSE_TYPES" :key="t" :label="t" :value="t" />
                                </el-select>
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="驾驶证到期">
                                <el-date-picker v-model="formData.licenseExpireDate" type="date" placeholder="选择到期日" style="width:100%" value-format="YYYY-MM-DD" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="头像URL">
                                <el-input v-model="formData.avatar" placeholder="头像图片链接（选填）" />
                            </el-form-item>
                        </el-col>
                    </el-row>
                    <div class="form-actions">
                        <el-button @click="cancelEdit">取消</el-button>
                        <el-button type="primary" :loading="submitting" @click="handleSubmit">
                            {{ driverInfo ? '保存修改' : '提交信息' }}
                        </el-button>
                    </div>
                </el-form>
            </template>
        </el-card>

    </div>
</template>

<script setup lang="ts" name="DriverProfile">
    import { ref, reactive, onMounted } from 'vue';
    import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
    import { UserFilled, Phone, Message, Edit, Plus } from '@element-plus/icons-vue';
    import { getMyDriverInfo, addDriverInfo, updateDriverInfo, type Driver } from '@/api/driver';
    import { useUserStore } from '@/stores/userStore';

    const userStore = useUserStore();
    const LICENSE_TYPES = ['C1', 'C2', 'B1', 'B2', 'A1', 'A2', 'A3'];

    const loading = ref(false);
    const driverInfo = ref<Driver | null>(null);
    const isEditing = ref(false);
    const submitting = ref(false);
    const formRef = ref<FormInstance>();

    const formData = reactive<Driver>({
        realName: '', phone: '', email: '', idCard: '',
        gender: 0, birthday: '', avatar: '', licenseNumber: '',
        licenseType: '', licenseExpireDate: '',
    });

    const formRules: FormRules = {
        realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
    };

    const fetchDriverInfo = async () => {
        loading.value = true;
        try {
            const res = await getMyDriverInfo();
            driverInfo.value = res.data ?? null;
        } catch {
            driverInfo.value = null;
        } finally {
            loading.value = false;
        }
    };

    const startEdit = () => { Object.assign(formData, { ...driverInfo.value }); isEditing.value = true; };
    const startCreate = () => {
        Object.assign(formData, { realName: '', phone: '', email: '', idCard: '', gender: 0, birthday: '', avatar: '', licenseNumber: '', licenseType: '', licenseExpireDate: '' });
        isEditing.value = true;
    };
    const cancelEdit = () => { isEditing.value = false; formRef.value?.resetFields(); };

    const handleSubmit = async () => {
        await formRef.value?.validate();
        submitting.value = true;
        try {
            if (driverInfo.value) {
                const res = await updateDriverInfo({ ...formData });
                driverInfo.value = res.data;
                ElMessage.success('信息修改成功');
            } else {
                const res = await addDriverInfo({ ...formData });
                driverInfo.value = res.data;
                ElMessage.success('信息提交成功');
            }
            isEditing.value = false;
        } finally {
            submitting.value = false;
        }
    };

    const statusLabel = (s: number | undefined) => s === 1 ? '正常' : s === 0 ? '禁用' : s === 2 ? '待审核' : '-';
    const statusTag = (s: number | undefined) => s === 1 ? 'success' : s === 0 ? 'danger' : 'warning';
    const formatDate = (d: string | undefined) => d ? new Date(d).toLocaleDateString('zh-CN') : '—';

    onMounted(() => fetchDriverInfo());
</script>

<style scoped>
    .driver-profile { display: flex; flex-direction: column; gap: 16px; }
    .header-card, .content-card { border-radius: 8px; }
    .driver-header { display: flex; align-items: center; gap: 20px; }
    .avatar-wrap { flex-shrink: 0; }
    .header-info { flex: 1; }
    .driver-name { font-size: 20px; font-weight: 700; color: #303133; margin-bottom: 8px; }
    .driver-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; }
    .meta-item { display: flex; align-items: center; gap: 4px; font-size: 14px; color: #606266; }
    .header-action { flex-shrink: 0; }
    .driver-form { padding: 4px 0; }
    .form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; padding-top: 16px; border-top: 1px solid #ebeef5; }
</style>
