import request from '@/utils/request';

interface CreateOrderRequest {
    shopId: number;
    addressId: number;
    items: [{productId: number, quantity: number}];
    remark: string;
}

export const createOrder = (data: CreateOrderRequest) => {
    return request({
        url: '/orders',
        method: 'POST',
        data
    })
}

export const getCustomerOrders = () => {
    return request({
        url: '/orders/my',
        method: 'GET'
    })
}

export const cancelOrder = (orderId: number, cancelReason: string) => {
    return request({
        url: `/orders/${orderId}/cancel`,
        method: 'PUT',
        data: { cancelReason }
    })
}

export const payOrder = (orderId: number) => {
    return request({
        url: `/orders/${orderId}/pay`,
        method: 'PUT'
    })
}

export const getOrderDetail = (orderId: number) => {
    return request({
        url: `/orders/${orderId}`,
        method: 'GET'
    })
}

export const deleteOrder = (orderId: number) => {
    return request({
        url: `/orders/${orderId}`,
        method: 'DELETE'
    })
}

export const getShopOrders = (shopId: number) => {
    return request({
        url: `/orders/shop/${shopId}`,
        method: 'GET'
    })
}

export const updateOrderStatus = (orderId: number, orderStatus: number) => {
    return request({
        url: `/orders/${orderId}/status`,
        method: 'PUT',
        data: { orderStatus }
    })
}

/** 管理员分页查询全部订单（支持按状态过滤） */
export const getAllOrders = (params: { current: number; size: number; status?: number }) => {
    return request({
        url: '/orders/admin/all',
        method: 'GET',
        params
    })
}

/** 管理员按订单号精确查询 */
export const getOrderByNo = (orderNo: string) => {
    return request({
        url: `/orders/no/${encodeURIComponent(orderNo)}`,
        method: 'GET'
    })
}

/** 顾客签收订单（待签收:6 → 已完成:4） */
export const signOrder = (orderId: number) => {
    return request({
        url: `/orders/${orderId}/sign`,
        method: 'PUT'
    })
}

/** 个人寄件请求参数 */
export interface PersonalShipmentRequest {
    senderAddressId: number
    deliveryAddressId: number
    cargoName: string
    weight: number
    declaredValue: number
    quantity: number
    remark?: string
}

/** 创建个人寄件单 */
export const createPersonalShipment = (data: PersonalShipmentRequest) => {
    return request({
        url: '/orders/personal-shipment',
        method: 'POST',
        data
    })
}

/** 管理员分页查询全部用户（支持用户名关键词搜索） */
export const getAllUsers = (params: { current: number; size: number; keyword?: string }) => {
    return request({
        url: '/users',
        method: 'GET',
        params
    })
}
