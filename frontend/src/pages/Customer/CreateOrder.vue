<template>
    <div class="create-order-container" v-loading="loading">
        <!-- 承运物信息 -->
        <el-card class="section-card">
            <template #header>承运物信息</template>
            <div class="product-section">
                <el-image :src="getFirstImage(product?.images)" fit="cover" class="product-image">
                    <template #error>
                        <div class="image-placeholder">
                            <el-icon size="40"><Picture /></el-icon>
                        </div>
                    </template>
                </el-image>
                <div class="product-info">
                    <div class="product-name">{{ product?.productName }}</div>
                    <div class="product-price">¥{{ product?.price }}</div>
                    <div class="product-shop-id">商家编号：{{ product?.shopId }}</div>
                </div>
                <div class="product-quantity">
                    <div class="quantity-label">托运数量</div>
                    <el-input-number v-model="quantity" :min="1" :max="100" controls-position="right" />
                    <div class="subtotal">小计：<span class="subtotal-price">¥{{ totalAmount }}</span></div>
                </div>
            </div>
        </el-card>

        <!-- 收货地址 -->
        <el-card class="section-card">
            <template #header>
                <div class="card-header">
                    <span>收货地址</span>
                    <el-button link type="primary" @click="goToAddress">前往个人中心添加</el-button>
                </div>
            </template>
            <el-radio-group v-model="selectedAddress" class="address-group" v-if="addresses.length > 0">
                <el-radio v-for="address in addresses" :key="address.id" :value="address.id" class="address-radio">
                    <div class="address-row-1">
                        <span class="address-name">{{ address.receiverName }}</span>
                        <span class="address-phone">{{ address.receiverPhone }}</span>
                        <el-tag v-if="address.isDefault === 1" type="success" size="small">默认</el-tag>
                    </div>
                    <div class="address-row-2">
                        {{ address.province }} {{ address.city }} {{ address.district }} {{ address.detailAddress }}
                    </div>
                </el-radio>
            </el-radio-group>
            <el-empty v-else description="暂无收货地址，请前往个人中心添加" />
        </el-card>

        <!-- 订单备注 -->
        <el-card class="section-card">
            <template #header>订单备注（选填）</template>
            <el-input
                v-model="remark"
                placeholder="如有特殊要求请在此填写"
                type="textarea"
                :rows="3"
                :maxlength="200"
                show-word-limit
            />
        </el-card>

        <!-- 底部操作栏 -->
        <div class="order-footer">
            <div class="total-section">
                <span class="total-label">合计</span>
                <span class="total-price">¥{{ totalAmount }}</span>
            </div>
            <div class="action-section">
                <el-button @click="router.back()">返回</el-button>
                <el-button type="primary" size="large" :loading="submitting" @click="submitOrder">
                    提交订单
                </el-button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts" name="CustomerCreateOrder">
    import { ref, onMounted, computed } from 'vue';
    import { useRoute, useRouter } from 'vue-router';
    import { getCustomerAddresses } from '@/api/customer';
    import { createOrder } from '@/api/order';
    import { ElMessage } from 'element-plus';
    import { Picture } from '@element-plus/icons-vue';
    import { getMallProductDetail } from '@/api/mall';
    import { getFirstImage } from '@/utils/common';
    import { useUserStore } from '@/stores/userStore';

    const route = useRoute();
    const router = useRouter();
    const userStore = useUserStore();

    const productId = Number(route.query.productId);
    const product = ref<any>(null);
    const quantity = ref(1);
    const addresses = ref<any[]>([]);
    const selectedAddress = ref<number | null>(null);
    const remark = ref('');
    const loading = ref(false);
    const submitting = ref(false);

    const totalAmount = computed(() => {
        if (!product.value) return '0.00';
        return (product.value.price * quantity.value).toFixed(2);
    });

    const fetchData = async () => {
        if (isNaN(productId)) {
            router.back();
            return;
        }
        loading.value = true;
        try {
            const [productRes, addressRes] = await Promise.all([
                getMallProductDetail(productId),
                getCustomerAddresses(Number(userStore.userInfo?.userId))
            ]);
            product.value = productRes.data;
            console.log(product.value);
            addresses.value = addressRes.data ?? [];
            // 自动选中默认地址，无默认则选第一条
            const defaultAddr = addresses.value.find(a => a.isDefault === 1);
            selectedAddress.value = defaultAddr?.id ?? addresses.value[0]?.id ?? null;
        } finally {
            loading.value = false;
        }
    };

    const submitOrder = async () => {
        if (!selectedAddress.value) {
            ElMessage.warning('请选择收货地址');
            return;
        }
        submitting.value = true;
        try {
            await createOrder({
                shopId: product.value.shopId,
                addressId: selectedAddress.value,
                items: [{ productId: product.value.id, quantity: quantity.value }],
                remark: remark.value,
            });
            ElMessage.success('下单成功');
            router.push('/sender/home/orders');
        } finally {
            submitting.value = false;
        }
    };

    const goToAddress = () => {
        router.push('/sender/home/profile');
    };

    onMounted(() => {
        fetchData();
    });
</script>

<style scoped>
    .create-order-container {
        display: flex;
        flex-direction: column;
        gap: 16px;
        padding-bottom: 80px;
    }

    .section-card {
        border-radius: 8px;
    }

    .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    /* 商品区域 */
    .product-section {
        display: flex;
        gap: 20px;
        align-items: flex-start;
    }

    .product-image {
        width: 120px;
        height: 120px;
        border-radius: 6px;
        flex-shrink: 0;
    }

    .image-placeholder {
        width: 120px;
        height: 120px;
        background: #f5f7fa;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #c0c4cc;
        border-radius: 6px;
    }

    .product-info {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .product-name {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
    }

    .product-price {
        font-size: 20px;
        font-weight: bold;
        color: #f56c6c;
    }

    .product-shop-id {
        font-size: 13px;
        color: #909399;
    }

    .product-quantity {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 10px;
        flex-shrink: 0;
    }

    .quantity-label {
        font-size: 13px;
        color: #606266;
    }

    .subtotal {
        font-size: 13px;
        color: #606266;
    }

    .subtotal-price {
        font-size: 16px;
        font-weight: bold;
        color: #f56c6c;
    }

    /* 地址区域 */
    .address-group {
        display: flex;
        flex-direction: column;
        gap: 12px;
        width: 100%;
    }

    :deep(.address-radio) {
        display: flex;
        align-items: flex-start;
        height: auto;
        padding: 12px;
        border: 1px solid #e4e7ed;
        border-radius: 6px;
        width: 100%;
        box-sizing: border-box;
        margin-right: 0;
        transition: border-color 0.2s, background-color 0.2s;
    }

    :deep(.address-radio:hover) {
        border-color: #409eff;
    }

    :deep(.address-radio.is-checked) {
        border-color: #409eff;
        background-color: #ecf5ff;
    }

    :deep(.address-radio .el-radio__input) {
        margin-top: 3px;
        flex-shrink: 0;
    }

    :deep(.address-radio .el-radio__label) {
        flex: 1;
        white-space: normal;
        color: #303133;
        padding-left: 10px;
    }

    .address-row-1 {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 6px;
    }

    .address-name {
        font-size: 15px;
        font-weight: 600;
        color: #303133;
    }

    .address-phone {
        font-size: 14px;
        color: #606266;
    }

    .address-row-2 {
        font-size: 13px;
        color: #909399;
        line-height: 1.4;
    }

    /* 底部操作栏 */
    .order-footer {
        position: fixed;
        bottom: 0;
        left: 200px;
        right: 0;
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 14px 24px;
        background: #fff;
        border-top: 1px solid #e6e6e6;
        box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
        z-index: 100;
    }

    .total-section {
        display: flex;
        align-items: baseline;
        gap: 8px;
    }

    .total-label {
        font-size: 14px;
        color: #606266;
    }

    .total-price {
        font-size: 26px;
        font-weight: bold;
        color: #f56c6c;
    }

    .action-section {
        display: flex;
        gap: 12px;
        align-items: center;
    }
</style>
