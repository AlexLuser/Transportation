import request from '@/utils/request';

// ==================== 类型定义 ====================

export interface Shop {
    id?: number;
    userId?: number;
    shopName: string;
    shopPhone?: string;
    shopEmail?: string;
    description?: string;
    logo?: string;
    businessLicense?: string;
    status?: number;
    createTime?: string;
    updateTime?: string;
}

export interface Product {
    id?: number;
    shopId?: number;
    categoryId?: number;
    productName: string;
    productCode?: string;
    description?: string;
    price: number;
    originalPrice?: number;
    unit?: string;
    weight?: number;
    images?: string;
    status?: number;
    salesCount?: number;
    createTime?: string;
    updateTime?: string;
}

export interface Warehouse {
    id?: number;
    warehouseName: string;
    warehousePhone?: string;
    province?: string;
    city?: string;
    district?: string;
    detailAddress?: string;
    postalCode?: string;
    capacity?: number;
    status?: number;
    latitude?: number;
    longitude?: number;
    createTime?: string;
    updateTime?: string;
}

export interface WarehouseProduct {
    id?: number;
    warehouseId: number;
    productId: number;
    stock: number;
    createTime?: string;
    updateTime?: string;
}

/** 获取商户信息（by shop ID） */
export const getShopInfo = (id: number) => {
    return request({
        url: `/shops/${id}`,
        method: 'GET',
    });
};

/** 新增商户信息 */
export const addShopInfo = (data: any) => {
    return request({
        url: '/shops',
        method: 'POST',
        data,
    });
};

/** 修改商户信息 */
export const updateShopInfo = (data: any) => {
    return request({
        url: '/shops',
        method: 'PUT',
        data,
    });
};

/** 根据 userId 获取自己的商户信息（内部接口，商户自用） */
export const getMyShop = (userId: number) => {
    return request({
        url: `/shops/internal/user/${userId}`,
        method: 'GET',
    });
};

/** 删除商户信息（by shop ID） */
export const deleteShopInfo = (id: number) => {
    return request({
        url: `/shops/${id}`,
        method: 'DELETE',
    });
};

// ==================== 商品管理接口 ====================

/** 获取商品详情（by product ID） */
export const getProductDetail = (id: number) => {
    return request({
        url: `/products/${id}`,
        method: 'GET',
    });
};

/** 获取商品列表（分页，不传 shopId 则自动取当前登录商户） */
export const getProducts = (params: any) => {
    return request({
        url: '/products',
        method: 'GET',
        params,
    });
};

/** 搜索商品（分页） */
export const searchProducts = (params: any) => {
    return request({
        url: '/products/search',
        method: 'GET',
        params,
    });
};

/** 新增商品 */
export const addProduct = (data: any) => {
    return request({
        url: '/products',
        method: 'POST',
        data,
    });
};

/** 修改商品（data 中必须包含 id） */
export const updateProduct = (data: any) => {
    return request({
        url: '/products',
        method: 'PUT',
        data,
    });
};

/** 删除商品（by product ID） */
export const deleteProduct = (id: number) => {
    return request({
        url: `/products/${id}`,
        method: 'DELETE',
    });
};

// ==================== 库存管理接口 ====================

/** 获取某商品在所有仓库的库存 */
export const getStockByProduct = (productId: number) => {
    return request({
        url: `/stocks/product/${productId}`,
        method: 'GET',
    });
};

/** 获取某仓库中所有商品的库存 */
export const getStockByWarehouse = (warehouseId: number) => {
    return request({
        url: `/stocks/warehouse/${warehouseId}`,
        method: 'GET',
    });
};

/** 修改库存（body: { warehouseId, productId, stock }） */
export const updateStock = (data: any) => {
    return request({
        url: '/stocks',
        method: 'PUT',
        data,
    });
};

// ==================== 仓库管理接口 ====================

/** 获取仓库列表 */
export const getWarehouses = () => {
    return request({
        url: '/warehouses',
        method: 'GET',
    });
};

/** 获取仓库详情（by warehouse ID） */
export const getWarehouseDetail = (id: number) => {
    return request({
        url: `/warehouses/${id}`,
        method: 'GET',
    });
};

/** 修改仓库容量（capacity=0 表示无限制） */
export const updateWarehouseCapacity = (id: number, capacity: number) => {
    return request({
        url: `/warehouses/${id}/capacity`,
        method: 'PUT',
        params: { capacity },
    });
};
