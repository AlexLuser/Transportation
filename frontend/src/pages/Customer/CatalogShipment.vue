<template>
    <div class="shipment-container" v-loading="loading">

        <!-- 货物信息卡 -->
        <el-card class="product-info-card" v-if="product">
            <div class="product-banner">
                <el-icon size="36" color="#409eff" class="banner-icon"><Box /></el-icon>
                <div class="product-banner-text">
                    <div class="product-banner-name">{{ product.productName }}</div>
                    <div class="product-banner-meta">
                        <span>重量：{{ product.weight ?? '-' }} kg</span>
                        <el-divider direction="vertical" />
                        <span>申报价值：¥{{ product.price ?? '-' }}</span>
                        <el-divider direction="vertical" />
                        <span>货主：{{ product.shopName ?? '-' }}</span>
                    </div>
                    <div class="product-banner-desc" v-if="product.description">{{ product.description }}</div>
                </div>
            </div>
        </el-card>
        <el-alert v-else-if="!loading" type="error" title="货物信息加载失败，请返回重试" :closable="false" />

        <!-- 收件地址选择 -->
        <el-card class="section-card">
            <template #header>
                <div class="card-header">
                    <span>选择收件地址</span>
                    <el-button link type="primary" @click="goToProfile">前往个人中心添加地址</el-button>
                </div>
            </template>
            <el-empty v-if="addresses.length === 0" description="暂无地址，请先前往个人中心添加" />
            <el-radio-group v-else v-model="deliveryAddressId" class="address-group">
                <el-radio
                    v-for="addr in addresses"
                    :key="addr.id"
                    :value="addr.id"
                    class="address-radio"
                >
                    <div class="address-row-1">
                        <span class="addr-name">{{ addr.receiverName }}</span>
                        <span class="addr-phone">{{ addr.receiverPhone }}</span>
                        <el-tag v-if="addr.isDefault === 1" type="success" size="small">默认</el-tag>
                    </div>
                    <div class="address-row-2">
                        {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detailAddress }}
                    </div>
                </el-radio>
            </el-radio-group>
        </el-card>

        <!-- 备注 + 提交 -->
        <el-card class="section-card">
            <template #header>备注（可选）</template>
            <el-input
                v-model="remark"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="如有特殊要求请在此填写"
            />
            <div class="submit-footer">
                <el-button @click="router.back()">返回</el-button>
                <el-button
                    type="primary"
                    size="large"
                    :loading="submitting"
                    :disabled="!deliveryAddressId || !product"
                    @click="submit"
                >
                    提交寄件单
                </el-button>
            </div>
        </el-card>

    </div>
</template>

<script setup lang="ts" name="CatalogShipment">
    import { ref, onMounted } from 'vue';
    import { useRouter, useRoute } from 'vue-router';
    import { ElMessage } from 'element-plus';
    import { Box } from '@element-plus/icons-vue';
    import { getCustomerAddresses } from '@/api/customer';
    import { createOrder } from '@/api/order';
    import { getMallProductDetail } from '@/api/mall';
    import { useUserStore } from '@/stores/userStore';

    const router = useRouter();
    const route = useRoute();
    const userStore = useUserStore();

    const loading = ref(false);
    const submitting = ref(false);
    const addresses = ref<any[]>([]);
    const deliveryAddressId = ref<number | null>(null);
    const product = ref<any>(null);
    const remark = ref('');

    const fetchAddresses = async () => {
        const userId = Number(userStore.userInfo?.userId);
        const res = await getCustomerAddresses(userId);
        addresses.value = res.data ?? [];
        const def = addresses.value.find((a: any) => a.isDefault === 1);
        deliveryAddressId.value = def?.id ?? addresses.value[0]?.id ?? null;
    };

    const fetchProduct = async () => {
        const productId = route.query.productId;
        if (!productId) {
            ElMessage.error('缺少货物参数，请从货物目录重新选择');
            router.replace('/sender/home/products');
            return;
        }
        const res = await getMallProductDetail(Number(productId));
        product.value = res.data ?? null;
        if (!product.value) {
            ElMessage.error('货物信息不存在');
        }
    };

    const submit = async () => {
        if (!deliveryAddressId.value) {
            ElMessage.warning('请选择收件地址');
            return;
        }
        if (!product.value) {
            ElMessage.error('货物信息异常，请返回重试');
            return;
        }
        submitting.value = true;
        try {
            await createOrder({
                shopId: product.value.shopId,
                addressId: deliveryAddressId.value,
                items: [{ productId: product.value.id, quantity: 1 }],
                remark: remark.value
            });
            ElMessage.success('寄件单已提交，请前往运单列表支付');
            router.push('/sender/home/orders');
        } finally {
            submitting.value = false;
        }
    };

    const goToProfile = () => router.push('/sender/home/profile');

    onMounted(async () => {
        loading.value = true;
        try {
            await Promise.all([fetchAddresses(), fetchProduct()]);
        } finally {
            loading.value = false;
        }
    });
</script>

<style scoped>
    .shipment-container {
        display: flex;
        flex-direction: column;
        gap: 20px;
        padding-bottom: 40px;
    }

    .product-info-card {
        border-radius: 10px;
        border-left: 4px solid #409eff;
    }

    .product-banner {
        display: flex;
        align-items: flex-start;
        gap: 16px;
    }

    .banner-icon { flex-shrink: 0; margin-top: 2px; }

    .product-banner-text { flex: 1; }

    .product-banner-name {
        font-size: 18px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 8px;
    }

    .product-banner-meta {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: #606266;
        margin-bottom: 6px;
    }

    .product-banner-desc {
        font-size: 13px;
        color: #909399;
        line-height: 1.6;
    }

    .section-card { border-radius: 8px; }

    .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .address-group {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .address-radio {
        display: flex;
        align-items: flex-start;
        border: 1px solid #e4e7ed;
        border-radius: 8px;
        padding: 14px 16px;
        cursor: pointer;
        transition: border-color .2s;
        height: auto;
    }

    .address-radio:hover { border-color: #409eff; }

    .address-row-1 {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 6px;
    }

    .addr-name { font-weight: 600; font-size: 15px; }
    .addr-phone { color: #606266; font-size: 13px; }

    .address-row-2 {
        font-size: 13px;
        color: #606266;
        line-height: 1.5;
    }

    .submit-footer {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 16px;
        padding-top: 16px;
        border-top: 1px solid #f0f2f5;
    }
</style>
