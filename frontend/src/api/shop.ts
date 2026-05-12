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

/** 管理员：查询全部商家列表 */
export const getAllShops = () => {
    return request({ url: '/shops', method: 'GET' });
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

/** 获取某仓库库存明细（含商品名称）；传 shopId 则只返回该商家库存 */
export const getStockDetailByWarehouse = (warehouseId: number, shopId?: number) => {
    return request({
        url: `/stocks/warehouse/${warehouseId}/detail`,
        method: 'GET',
        params: shopId ? { shopId } : {},
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

// ==================== 仓库完整 CRUD ====================

/** 新建仓库 */
export const createWarehouse = (data: Warehouse) => {
    return request({ url: '/warehouses', method: 'POST', data });
};

/** 更新仓库信息 */
export const updateWarehouse = (id: number, data: Warehouse) => {
    return request({ url: `/warehouses/${id}`, method: 'PUT', data });
};

/** 删除仓库 */
export const deleteWarehouse = (id: number) => {
    return request({ url: `/warehouses/${id}`, method: 'DELETE' });
};

// ==================== 共享仓管理 ====================

export const getWarehousesByShop = (shopId: number) =>
    request({ url: `/warehouse-shops/shop/${shopId}/warehouses`, method: 'GET' });

export const getShopsByWarehouse = (warehouseId: number) =>
    request({ url: `/warehouse-shops/warehouse/${warehouseId}/shops`, method: 'GET' });

export const bindWarehouseShop = (warehouseId: number, shopId: number, role = 'TENANT') =>
    request({ url: '/warehouse-shops/bind', method: 'POST', params: { warehouseId, shopId, role } });

export const unbindWarehouseShop = (warehouseId: number, shopId: number) =>
    request({ url: '/warehouse-shops/unbind', method: 'DELETE', params: { warehouseId, shopId } });

// ==================== 库位管理 ====================

export const getLocationsByWarehouse = (warehouseId: number) =>
    request({ url: `/warehouse-locations/warehouse/${warehouseId}`, method: 'GET' });

export const getZoneSummary = (warehouseId: number) =>
    request({ url: `/warehouse-locations/warehouse/${warehouseId}/zone-summary`, method: 'GET' });

export const createLocation = (data: any) =>
    request({ url: '/warehouse-locations', method: 'POST', data });

export const updateLocation = (id: number, data: any) =>
    request({ url: `/warehouse-locations/${id}`, method: 'PUT', data });

export const deleteLocation = (id: number) =>
    request({ url: `/warehouse-locations/${id}`, method: 'DELETE' });

// ==================== 入库管理 ====================

export const getInboundOrders = (params: any) =>
    request({ url: '/inbound-orders', method: 'GET', params });

export const getInboundOrderItems = (id: number) =>
    request({ url: `/inbound-orders/${id}/items`, method: 'GET' });

export const createInboundOrder = (data: any) =>
    request({ url: '/inbound-orders', method: 'POST', data });

export const startInbound = (id: number) =>
    request({ url: `/inbound-orders/${id}/start`, method: 'PUT' });

export const completeInbound = (id: number, items: any[]) =>
    request({ url: `/inbound-orders/${id}/complete`, method: 'PUT', data: items });

export const cancelInbound = (id: number, reason = '') =>
    request({ url: `/inbound-orders/${id}/cancel`, method: 'PUT', params: { reason } });

// ==================== 出库管理 ====================

export const getOutboundOrders = (params: any) =>
    request({ url: '/outbound-orders', method: 'GET', params });

export const getOutboundOrderItems = (id: number) =>
    request({ url: `/outbound-orders/${id}/items`, method: 'GET' });

export const createOutboundOrder = (data: any) =>
    request({ url: '/outbound-orders', method: 'POST', data });

export const startOutbound = (id: number) =>
    request({ url: `/outbound-orders/${id}/start`, method: 'PUT' });

export const completeOutbound = (id: number) =>
    request({ url: `/outbound-orders/${id}/complete`, method: 'PUT' });

export const cancelOutbound = (id: number, reason = '') =>
    request({ url: `/outbound-orders/${id}/cancel`, method: 'PUT', params: { reason } });

// ==================== 盘点管理 ====================

export const getInventoryChecks = (params: any) =>
    request({ url: '/inventory-checks', method: 'GET', params });

export const getInventoryCheckItems = (id: number) =>
    request({ url: `/inventory-checks/${id}/items`, method: 'GET' });

export const getInventoryCheckSummary = (id: number) =>
    request({ url: `/inventory-checks/${id}/summary`, method: 'GET' });

export const createInventoryCheck = (data: any) =>
    request({ url: '/inventory-checks', method: 'POST', data });

export const startInventoryCheck = (id: number) =>
    request({ url: `/inventory-checks/${id}/start`, method: 'PUT' });

export const submitCheckItem = (checkId: number, itemId: number, actualQty: number) =>
    request({ url: `/inventory-checks/${checkId}/items/${itemId}`, method: 'PUT', params: { actualQty } });

export const confirmInventoryCheck = (id: number) =>
    request({ url: `/inventory-checks/${id}/confirm`, method: 'PUT' });

// ==================== 调拨管理 ====================

export const getTransferOrders = (params: any) =>
    request({ url: '/transfer-orders', method: 'GET', params });

export const getTransferOrderItems = (id: number) =>
    request({ url: `/transfer-orders/${id}/items`, method: 'GET' });

export const createTransferOrder = (data: any) =>
    request({ url: '/transfer-orders', method: 'POST', data });

export const approveTransfer = (id: number) =>
    request({ url: `/transfer-orders/${id}/approve`, method: 'PUT' });

export const completeTransfer = (id: number) =>
    request({ url: `/transfer-orders/${id}/complete`, method: 'PUT' });

export const cancelTransfer = (id: number, reason = '') =>
    request({ url: `/transfer-orders/${id}/cancel`, method: 'PUT', params: { reason } });

// ==================== 订单发货接口 ====================

/**
 * 商户发货（含仓库选择）
 * POST /orders/{orderId}/ship
 */
export const shipOrder = (orderId: number, warehouseId: number) => {
    return request({
        url: `/orders/${orderId}/ship`,
        method: 'POST',
        data: { warehouseId },
    });
};

/**
 * 查询订单可选仓库（含该订单商品的库存信息）
 * 用于商户发货弹窗下拉选择
 */
export const getOrderAvailableWarehouses = (orderId: number) => {
    return request({
        url: `/orders/${orderId}/available-warehouses`,
        method: 'GET',
    });
};
