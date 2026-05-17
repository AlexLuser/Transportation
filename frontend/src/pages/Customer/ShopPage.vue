<template>
    <div class="shop-page">
        <!-- 返回按钮 -->
        <el-button :icon="ArrowLeft" @click="router.back()" class="back-btn">返回承运物列表</el-button>

        <!-- 商家信息卡片 -->
        <el-card class="shop-info-card" v-loading="shopLoading">
            <div v-if="shop" class="shop-info">
                <el-avatar :size="80" :src="shop.logo" class="shop-avatar">
                    {{ shop.shopName?.charAt(0) }}
                </el-avatar>
                <div class="shop-detail">
                    <div class="shop-name">{{ shop.shopName }}</div>
                    <div class="shop-desc" v-if="shop.description">{{ shop.description }}</div>
                    <div class="shop-meta">
                        <span v-if="shop.shopPhone">
                            <el-icon><Phone /></el-icon> {{ shop.shopPhone }}
                        </span>
                        <span v-if="shop.shopEmail">
                            <el-icon><Message /></el-icon> {{ shop.shopEmail }}
                        </span>
                    </div>
                </div>
            </div>
            <el-empty v-else-if="!shopLoading" description="商家信息不存在" />
        </el-card>

        <!-- 商家承运物 -->
        <div class="shop-products">
            <div class="section-title">承运物目录</div>

            <!-- 搜索栏 -->
            <div class="search-bar">
                <el-input
                    v-model="keyword"
                    placeholder="搜索承运物名称"
                    clearable
                    class="search-input"
                    @keyup.enter="handleSearch"
                />
                <el-select v-model="sortType" class="sort-select" @change="handleSearch">
                    <el-option label="最新创建" value="createTime_desc" />
                    <el-option label="申报价值最低" value="price_asc" />
                    <el-option label="发件数最高" value="salesCount_desc" />
                </el-select>
                <el-button type="primary" @click="handleSearch">搜索</el-button>
            </div>

            <!-- 承运物列表 -->
            <el-row :gutter="16" v-loading="productsLoading">
                <el-col :span="6" v-for="product in products" :key="product.id">
                    <el-card class="product-card" shadow="hover">
                        <el-image
                            :src="getFirstImage(product.images)"
                            fit="cover"
                            class="product-image"
                        >
                            <template #error>
                                <div class="image-placeholder">
                                    <el-icon size="40"><Picture /></el-icon>
                                </div>
                            </template>
                        </el-image>
                        <div class="product-info">
                            <div class="product-name" :title="product.productName">
                                {{ product.productName }}
                            </div>
                            <div class="product-price-row">
                                <span class="price">¥{{ product.price }}</span>
                                <span class="original-price"
                                    v-if="product.originalPrice && product.originalPrice > product.price">
                                    ¥{{ product.originalPrice }}
                                </span>
                            </div>
                            <div class="product-sales">已发 {{ product.salesCount ?? 0 }} 件</div>
                            <div class="product-actions">
                                <el-button size="small" @click="openDetail(product)">查看详情</el-button>
                                <el-button size="small" type="primary" @click="goToOrder(product)">创建运单</el-button>
                            </div>
                        </div>
                    </el-card>
                </el-col>
            </el-row>

            <!-- 空状态 -->
            <el-empty v-if="!productsLoading && products.length === 0" description="该商家暂无承运物" />

            <!-- 分页 -->
            <div class="pagination-bar">
                <el-pagination
                    v-model:current-page="currentPage"
                    v-model:page-size="pageSize"
                    :page-sizes="[12, 24, 36]"
                    :total="total"
                    layout="total, sizes, prev, pager, next"
                    @current-change="handlePageChange"
                    @size-change="handleSizeChange"
                />
            </div>
        </div>

        <!-- 承运物详情弹窗 -->
        <el-dialog
            v-model="dialogVisible"
            width="780px"
            :title="selectedProduct?.productName"
            align-center
        >
            <div v-if="selectedProduct" class="dialog-body">
                <div class="dialog-left">
                    <el-image
                        :src="getFirstImage(selectedProduct.images)"
                        fit="cover"
                        class="dialog-image"
                    >
                        <template #error>
                            <div class="image-placeholder dialog-image">
                                <el-icon size="60"><Picture /></el-icon>
                            </div>
                        </template>
                    </el-image>
                </div>
                <div class="dialog-right">
                    <div class="dialog-price-row">
                        <span class="dialog-price">¥{{ selectedProduct.price }}</span>
                        <span class="dialog-original-price"
                            v-if="selectedProduct.originalPrice && selectedProduct.originalPrice > selectedProduct.price">
                            参考价值 ¥{{ selectedProduct.originalPrice }}
                        </span>
                    </div>
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="承运物编码">{{ selectedProduct.productCode ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="单位">{{ selectedProduct.unit ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="重量">{{ selectedProduct.weight ? selectedProduct.weight + ' kg' : '-' }}</el-descriptions-item>
                        <el-descriptions-item label="已发件数">{{ selectedProduct.salesCount ?? 0 }} 件</el-descriptions-item>
                    </el-descriptions>
                    <div class="dialog-description" v-if="selectedProduct.description">
                        <div class="desc-label">承运物描述</div>
                        <p>{{ selectedProduct.description }}</p>
                    </div>
                </div>
            </div>
            <template #footer>
                <el-button type="primary" @click="goToOrder(selectedProduct)">创建运单</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="CustomerShopPage">
    import { ref, onMounted } from 'vue';
    import { useRoute, useRouter } from 'vue-router';
    import { getMallShop, getMallProducts } from '@/api/mall';
    import { ArrowLeft, Phone, Message, Picture } from '@element-plus/icons-vue';
    import { getFirstImage } from '@/utils/common';
    
    const route = useRoute();
    const router = useRouter();
    const shopId = Number(route.params.id);

    const shop = ref<any>(null);
    const shopLoading = ref(false);

    const products = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(12);
    const keyword = ref('');
    const sortType = ref('createTime_desc');
    const productsLoading = ref(false);

    const dialogVisible = ref(false);
    const selectedProduct = ref<any>(null);

    const fetchShop = async () => {
        shopLoading.value = true;
        try {
            const res = await getMallShop(shopId);
            shop.value = res.data;
        } finally {
            shopLoading.value = false;
        }
    };

    const fetchProducts = async () => {
        productsLoading.value = true;
        try {
            const [sortField, sortOrder] = sortType.value.split('_');
            const res = await getMallProducts({
                shopId,
                current: currentPage.value,
                size: pageSize.value,
                keyword: keyword.value,
                sortField,
                sortOrder,
            });
            products.value = res.data.records ?? [];
            total.value = res.data.total ?? 0;
        } finally {
            productsLoading.value = false;
        }
    };

    const handleSearch = () => {
        currentPage.value = 1;
        fetchProducts();
    };

    const handlePageChange = (page: number) => {
        currentPage.value = page;
        fetchProducts();
    };

    const handleSizeChange = (size: number) => {
        pageSize.value = size;
        currentPage.value = 1;
        fetchProducts();
    };

    const openDetail = (product: any) => {
        selectedProduct.value = product;
        dialogVisible.value = true;
    };

    const goToOrder = (product: any) => {
        dialogVisible.value = false;
        router.push({ path: '/sender/home/shipment', query: { productId: product.id } });
    };

    onMounted(() => {
        fetchShop();
        fetchProducts();
    });
</script>

<style scoped>
    .shop-page {
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .back-btn {
        align-self: flex-start;
    }

    .shop-info-card {
        border-radius: 8px;
    }

    .shop-info {
        display: flex;
        align-items: flex-start;
        gap: 24px;
    }

    .shop-avatar {
        flex-shrink: 0;
        font-size: 28px;
        background-color: #409eff;
        color: #fff;
    }

    .shop-detail {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .shop-name {
        font-size: 22px;
        font-weight: bold;
        color: #303133;
    }

    .shop-desc {
        font-size: 14px;
        color: #606266;
        line-height: 1.6;
    }

    .shop-meta {
        display: flex;
        gap: 20px;
        font-size: 13px;
        color: #909399;
    }

    .shop-meta span {
        display: flex;
        align-items: center;
        gap: 4px;
    }

    .section-title {
        font-size: 16px;
        font-weight: bold;
        color: #303133;
        padding-bottom: 12px;
        border-bottom: 2px solid #409eff;
        display: inline-block;
    }

    .shop-products {
        display: flex;
        flex-direction: column;
        gap: 16px;
        background: #fff;
        border-radius: 8px;
        padding: 20px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    }

    .search-bar {
        display: flex;
        gap: 10px;
        align-items: center;
    }

    .search-input {
        width: 280px;
    }

    .sort-select {
        width: 140px;
    }

    .product-card {
        margin-bottom: 16px;
        transition: transform 0.2s;
        cursor: pointer;
    }

    .product-card:hover {
        transform: translateY(-4px);
    }

    .product-image {
        width: 100%;
        height: 160px;
        border-radius: 4px;
        display: block;
    }

    .image-placeholder {
        width: 100%;
        height: 160px;
        background-color: #f5f7fa;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #c0c4cc;
        border-radius: 4px;
    }

    .product-info {
        padding: 10px 0 0;
    }

    .product-name {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        margin-bottom: 6px;
    }

    .product-price-row {
        display: flex;
        align-items: baseline;
        gap: 8px;
        margin-bottom: 4px;
    }

    .price {
        font-size: 18px;
        font-weight: bold;
        color: #f56c6c;
    }

    .original-price {
        font-size: 12px;
        color: #909399;
        text-decoration: line-through;
    }

    .product-sales {
        font-size: 12px;
        color: #909399;
        margin-bottom: 10px;
    }

    .product-actions {
        display: flex;
    }

    .product-actions .el-button {
        width: 100%;
    }

    .pagination-bar {
        display: flex;
        justify-content: center;
        padding-top: 10px;
    }

    .dialog-body {
        display: flex;
        gap: 24px;
    }

    .dialog-left {
        flex-shrink: 0;
        width: 280px;
    }

    .dialog-image {
        width: 280px;
        height: 280px;
        border-radius: 8px;
    }

    .dialog-right {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    .dialog-price-row {
        display: flex;
        align-items: baseline;
        gap: 12px;
    }

    .dialog-price {
        font-size: 28px;
        font-weight: bold;
        color: #f56c6c;
    }

    .dialog-original-price {
        font-size: 14px;
        color: #909399;
        text-decoration: line-through;
    }

    .dialog-description {
        background: #f8f9fa;
        border-radius: 6px;
        padding: 12px;
    }

    .desc-label {
        font-size: 13px;
        font-weight: 600;
        color: #606266;
        margin-bottom: 8px;
    }

    .dialog-description p {
        font-size: 14px;
        color: #606266;
        line-height: 1.6;
        margin: 0;
    }
</style>
