<template>
    <div class="product-page">
        <!-- 搜索栏 -->
        <div class="search-bar">
            <el-input
                v-model="keyword"
                placeholder="搜索货物名称"
                clearable
                class="search-input"
                @keyup.enter="handleSearch"
            />
            <el-select v-model="sortType" class="sort-select" @change="handleSearch">
                <el-option label="最新发布" value="createTime_desc" />
                <el-option label="运费最低" value="price_asc" />
                <el-option label="发货量最高" value="salesCount_desc" />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>

        <!-- 货物列表 -->
        <el-row :gutter="16" class="product-grid">
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
                            <span class="price">申报价值 ¥{{ product.price }}</span>
                        </div>
                        <div class="product-sales">已发 {{ product.salesCount ?? 0 }} 件</div>
                        <div class="product-actions">
                            <el-button size="small" @click="openDetail(product)">查看详情</el-button>
                            <el-button size="small" type="primary" @click="goToOrder(product)">发起寄件</el-button>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 空状态 -->
        <el-empty v-if="products.length === 0" description="暂无货物信息" class="empty-state" />

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

        <!-- 货物详情弹窗 -->
        <el-dialog
            v-model="dialogVisible"
            width="780px"
            :title="selectedProduct?.productName"
            align-center
        >
            <div v-if="selectedProduct" class="dialog-body">
                <!-- 左栏：图片 -->
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

                <!-- 右栏：详情 -->
                <div class="dialog-right">
                    <div class="dialog-price-row">
                        <span class="dialog-price-label">申报价值</span>
                        <span class="dialog-price">¥{{ selectedProduct.price }}</span>
                    </div>

                    <el-descriptions :column="1" border class="dialog-desc">
                        <el-descriptions-item label="货物编码">{{ selectedProduct.productCode ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="计量单位">{{ selectedProduct.unit ?? '-' }}</el-descriptions-item>
                        <el-descriptions-item label="重量">{{ selectedProduct.weight ? selectedProduct.weight + ' kg' : '-' }}</el-descriptions-item>
                        <el-descriptions-item label="已发件数">{{ selectedProduct.salesCount ?? 0 }} 件</el-descriptions-item>
                    </el-descriptions>

                    <div class="dialog-description" v-if="selectedProduct.description">
                        <div class="desc-label">货物描述</div>
                        <p>{{ selectedProduct.description }}</p>
                    </div>
                </div>
            </div>

            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="goToShop(selectedProduct)">查看货主信息</el-button>
                    <el-button type="primary" @click="goToOrder(selectedProduct)">发起寄件</el-button>
                </div>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts" name="CustomerProduct">
    import { ref, onMounted } from 'vue';
    import { useRouter } from 'vue-router';
    import { getMallProducts } from '@/api/mall';
    import { Picture } from '@element-plus/icons-vue';

    const router = useRouter();

    const keyword = ref('');
    const sortType = ref('createTime_desc');
    const currentPage = ref(1);
    const pageSize = ref(12);
    const total = ref(0);
    const products = ref<any[]>([]);

    const dialogVisible = ref(false);
    const selectedProduct = ref<any>(null);

    const getFirstImage = (images: string) => {
        if (!images) return '';
        try {
            const arr = JSON.parse(images);
            return Array.isArray(arr) && arr.length > 0 ? arr[0] : '';
        } catch {
            return '';
        }
    };

    const fetchProducts = async () => {
        const [sortField, sortOrder] = sortType.value.split('_');
        const res = await getMallProducts({
            current: currentPage.value,
            size: pageSize.value,
            keyword: keyword.value,
            sortField,
            sortOrder,
        });
        products.value = res.data.records ?? [];
        total.value = res.data.total ?? 0;
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
        router.push({ path: '/sender/home/catalog-shipment', query: { productId: product.id } });
    };

    const goToShop = (product: any) => {
        dialogVisible.value = false;
        router.push({ path: `/sender/home/merchant/${product.shopId}` });
    };

    onMounted(() => {
        fetchProducts();
    });
</script>

<style scoped>
    .product-page {
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .search-bar {
        display: flex;
        gap: 10px;
        align-items: center;
        background: #fff;
        padding: 16px 20px;
        border-radius: 8px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    }

    .search-input {
        width: 320px;
    }

    .sort-select {
        width: 140px;
    }

    .product-grid {
        flex: 1;
    }

    .product-card {
        margin-bottom: 16px;
        cursor: pointer;
        transition: transform 0.2s;
    }

    .product-card:hover {
        transform: translateY(-4px);
    }

    .product-image {
        width: 100%;
        height: 180px;
        border-radius: 4px;
        display: block;
    }

    .image-placeholder {
        width: 100%;
        height: 180px;
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
        margin-bottom: 8px;
    }

    .product-price-row {
        display: flex;
        align-items: baseline;
        gap: 8px;
        margin-bottom: 4px;
    }

    .price {
        font-size: 14px;
        font-weight: 500;
        color: #409eff;
    }

    .product-sales {
        font-size: 12px;
        color: #909399;
        margin-bottom: 10px;
    }

    .product-actions {
        display: flex;
        gap: 8px;
    }

    .empty-state {
        padding: 60px 0;
    }

    .pagination-bar {
        display: flex;
        justify-content: center;
        padding: 10px 0;
    }

    /* 弹窗样式 */
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

    .dialog-price-label {
        font-size: 14px;
        color: #606266;
    }

    .dialog-price {
        font-size: 28px;
        font-weight: bold;
        color: #409eff;
    }

    .dialog-desc {
        margin-top: 4px;
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

    .dialog-footer {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
    }
</style>
