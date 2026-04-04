<template>
    <div class="profile-manage">

        <!-- 头部 Logo + 商铺名 -->
        <el-card class="header-card" shadow="never">
            <div class="shop-header">
                <el-avatar :size="80" :src="shopInfo?.logo || undefined" shape="square" class="shop-avatar">
                    <el-icon :size="36"><Shop /></el-icon>
                </el-avatar>
                <div class="header-info">
                    <div class="shop-name">{{ shopInfo?.shopName || '尚未创建商铺' }}</div>
                    <div class="shop-meta">
                        <span v-if="shopInfo?.shopPhone" class="meta-item">
                            <el-icon><Phone /></el-icon>{{ shopInfo.shopPhone }}
                        </span>
                        <span v-if="shopInfo?.shopEmail" class="meta-item">
                            <el-icon><Message /></el-icon>{{ shopInfo.shopEmail }}
                        </span>
                        <el-tag v-if="shopInfo" :type="shopStatusTag(shopInfo.status)" size="small" class="meta-item">
                            {{ shopStatusLabel(shopInfo.status) }}
                        </el-tag>
                    </div>
                </div>
                <div class="header-action">
                    <el-button v-if="shopInfo && !isEditing" type="primary" plain @click="startEdit">
                        <el-icon><Edit /></el-icon>&nbsp;编辑信息
                    </el-button>
                    <el-button v-if="!shopInfo && !isEditing" type="success" @click="startCreate">
                        <el-icon><Plus /></el-icon>&nbsp;创建商铺
                    </el-button>
                </div>
            </div>
        </el-card>

        <!-- 详情 / 表单卡片 -->
        <el-card shadow="never" class="content-card" v-loading="loading">

            <!-- 查看模式 -->
            <template v-if="shopInfo && !isEditing">
                <el-descriptions :column="2" border>
                    <el-descriptions-item label="商铺名称">{{ shopInfo.shopName }}</el-descriptions-item>
                    <el-descriptions-item label="联系电话">{{ shopInfo.shopPhone || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="联系邮箱">{{ shopInfo.shopEmail || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="营业执照号">{{ shopInfo.businessLicense || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="状态">
                        <el-tag :type="shopStatusTag(shopInfo.status)" size="small">{{ shopStatusLabel(shopInfo.status) }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="创建时间">{{ formatDate(shopInfo.createTime) }}</el-descriptions-item>
                    <el-descriptions-item label="Logo URL" :span="2">{{ shopInfo.logo || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="商铺描述" :span="2">{{ shopInfo.description || '—' }}</el-descriptions-item>
                </el-descriptions>
            </template>

            <!-- 无商铺时的空状态 -->
            <template v-if="!shopInfo && !isEditing">
                <el-empty description="您还没有创建商铺，点击右上角「创建商铺」开始">
                    <el-button type="success" @click="startCreate">
                        <el-icon><Plus /></el-icon>&nbsp;创建商铺
                    </el-button>
                </el-empty>
            </template>

            <!-- 编辑 / 创建表单 -->
            <template v-if="isEditing">
                <el-form
                    ref="formRef"
                    :model="formData"
                    :rules="formRules"
                    label-width="100px"
                    class="shop-form"
                >
                    <el-row :gutter="20">
                        <el-col :span="12">
                            <el-form-item label="商铺名称" prop="shopName">
                                <el-input v-model="formData.shopName" placeholder="请输入商铺名称" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="联系电话">
                                <el-input v-model="formData.shopPhone" placeholder="请输入联系电话" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="联系邮箱">
                                <el-input v-model="formData.shopEmail" placeholder="请输入联系邮箱" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="营业执照号">
                                <el-input v-model="formData.businessLicense" placeholder="请输入营业执照号（选填）" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="24">
                            <el-form-item label="Logo URL">
                                <el-input v-model="formData.logo" placeholder="Logo 图片链接（选填）" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="24">
                            <el-form-item label="商铺描述">
                                <el-input
                                    v-model="formData.description"
                                    type="textarea"
                                    :rows="4"
                                    placeholder="请输入商铺描述（选填）"
                                />
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <div class="form-actions">
                        <el-button @click="cancelEdit">取消</el-button>
                        <el-button type="primary" :loading="submitting" @click="handleSubmit">
                            {{ shopInfo ? '保存修改' : '确认创建' }}
                        </el-button>
                    </div>
                </el-form>
            </template>
        </el-card>

    </div>
</template>

<script setup lang="ts" name="ShopProfile">
    import { ref, reactive, onMounted } from 'vue';
    import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
    import { Edit, Plus, Shop, Phone, Message } from '@element-plus/icons-vue';
    import { getMyShop, addShopInfo, updateShopInfo, type Shop as ShopType } from '@/api/shop';
    import { useUserStore } from '@/stores/userStore';

    const userStore = useUserStore();

    // ==================== 数据状态 ====================
    const loading = ref(false);
    const shopInfo = ref<ShopType | null>(null);
    const isEditing = ref(false);
    const submitting = ref(false);
    const formRef = ref<FormInstance>();

    const formData = reactive<ShopType>({
        shopName: '',
        shopPhone: '',
        shopEmail: '',
        businessLicense: '',
        logo: '',
        description: '',
    });

    const formRules: FormRules = {
        shopName: [{ required: true, message: '请输入商铺名称', trigger: 'blur' }],
    };

    // ==================== 加载商铺信息 ====================
    const fetchShopInfo = async () => {
        const userId = userStore.userInfo?.userId;
        if (!userId) return;
        loading.value = true;
        try {
            const res = await getMyShop(userId);
            shopInfo.value = res.data ?? null;
        } catch {
            shopInfo.value = null;
        } finally {
            loading.value = false;
        }
    };

    // ==================== 编辑 / 创建 ====================
    const startEdit = () => {
        if (shopInfo.value) {
            Object.assign(formData, { ...shopInfo.value });
        }
        isEditing.value = true;
    };

    const startCreate = () => {
        Object.assign(formData, {
            shopName: '', shopPhone: '', shopEmail: '',
            businessLicense: '', logo: '', description: '',
        });
        isEditing.value = true;
    };

    const cancelEdit = () => {
        isEditing.value = false;
        formRef.value?.resetFields();
    };

    const handleSubmit = async () => {
        await formRef.value?.validate();
        submitting.value = true;
        try {
            if (shopInfo.value) {
                const res = await updateShopInfo({ ...formData });
                shopInfo.value = res.data;
                ElMessage.success('商铺信息修改成功');
            } else {
                const res = await addShopInfo({ ...formData });
                shopInfo.value = res.data;
                ElMessage.success('商铺创建成功');
            }
            isEditing.value = false;
        } finally {
            submitting.value = false;
        }
    };

    // ==================== 工具函数 ====================
    const shopStatusLabel = (status: number | undefined) => {
        if (status === 1) return '正常营业';
        if (status === 0) return '已禁用';
        if (status === 2) return '待审核';
        return '-';
    };

    const shopStatusTag = (status: number | undefined) => {
        if (status === 1) return 'success';
        if (status === 0) return 'danger';
        if (status === 2) return 'warning';
        return 'info';
    };

    const formatDate = (date: string | undefined) => {
        if (!date) return '-';
        return new Date(date).toLocaleString('zh-CN', { hour12: false });
    };

    onMounted(() => {
        fetchShopInfo();
    });
</script>

<style scoped>
    .profile-manage {
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    /* 头部卡片 */
    .header-card { border-radius: 8px; }

    .shop-header {
        display: flex;
        align-items: center;
        gap: 20px;
    }

    .shop-avatar {
        flex-shrink: 0;
        background-color: #ecf5ff;
        color: #409eff;
        border-radius: 8px;
    }

    .header-info {
        flex: 1;
    }

    .shop-name {
        font-size: 20px;
        font-weight: 700;
        color: #303133;
        margin-bottom: 8px;
    }

    .shop-meta {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 16px;
    }

    .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 14px;
        color: #606266;
    }

    .header-action {
        flex-shrink: 0;
    }

    /* 内容卡片 */
    .content-card { border-radius: 8px; }

    /* 表单 */
    .shop-form { padding: 4px 0; }

    .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 10px;
        margin-top: 8px;
        padding-top: 16px;
        border-top: 1px solid #ebeef5;
    }
</style>
