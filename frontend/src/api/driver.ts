import request from '@/utils/request';

// ==================== 类型定义 ====================

export interface Driver {
    id?: number;
    userId?: number;
    realName?: string;
    phone?: string;
    email?: string;
    idCard?: string;
    gender?: number;
    birthday?: string;
    avatar?: string;
    licenseNumber?: string;
    licenseType?: string;
    licenseExpireDate?: string;
    status?: number;
    createTime?: string;
    updateTime?: string;
}

export interface Vehicle {
    id?: number;
    driverId?: number;
    vehicleType?: string;
    vehicleBrand?: string;
    vehicleModel?: string;
    licensePlate?: string;
    loadCapacity?: number;
    volumeCapacity?: number;
    vehicleStatus?: number;
    createTime?: string;
    updateTime?: string;
}

export interface DeliveryPageParams {
    current?: number;
    size?: number;
    status?: number;
    sortField?: string;
    sortOrder?: string;
}

// ==================== 运输员信息接口 ====================

/** 获取当前运输员信息 */
export const getMyDriverInfo = () => {
    return request({ url: '/drivers', method: 'GET' });
};

/** 新增运输员信息 */
export const addDriverInfo = (data: Driver) => {
    return request({ url: '/drivers', method: 'POST', data });
};

/** 更新运输员信息 */
export const updateDriverInfo = (data: Driver) => {
    return request({ url: '/drivers', method: 'PUT', data });
};

// ==================== 车辆管理接口 ====================

/** 获取当前运输员的车辆列表 */
export const getMyVehicles = () => {
    return request({ url: '/drivers/vehicles', method: 'GET' });
};

/** 新增车辆 */
export const addVehicle = (data: Vehicle) => {
    return request({ url: '/drivers/vehicles', method: 'POST', data });
};

/** 更新车辆信息（data 中必须包含 id） */
export const updateVehicle = (data: Vehicle) => {
    return request({ url: '/drivers/vehicles', method: 'PUT', data });
};

/** 删除车辆 */
export const deleteVehicle = (id: number) => {
    return request({ url: `/drivers/vehicles/${id}`, method: 'DELETE' });
};

/** 更新车辆状态（0=停用，1=可用） */
export const updateVehicleStatus = (id: number, status: number) => {
    return request({ url: `/drivers/vehicles/${id}/status`, method: 'PUT', params: { status } });
};

// ==================== 配送订单接口 ====================

/** 获取待接单列表（分页） */
export const getPendingDeliveries = (params: { current?: number; size?: number }) => {
    return request({ url: '/drivers/deliveries/pending', method: 'GET', params });
};

/** 获取我的配送订单列表（分页，支持状态筛选） */
export const getMyDeliveries = (params: DeliveryPageParams) => {
    return request({ url: '/drivers/deliveries', method: 'GET', params });
};

/** 进行中的配送（已接单 / 运输中） */
export const getInProgressDeliveries = () => {
    return request({ url: '/drivers/deliveries/in-progress', method: 'GET' });
};

/** 获取配送详情 */
export const getDeliveryDetail = (id: number) => {
    return request({ url: `/drivers/deliveries/${id}`, method: 'GET' });
};

/** 接单（可选指定车辆） */
export const acceptDelivery = (id: number, vehicleId?: number) => {
    return request({
        url: `/drivers/deliveries/${id}/accept`,
        method: 'POST',
        data: vehicleId ? { vehicleId } : {},
    });
};

/** 更新配送状态（status: 2=运输中，3=已送达） */
export const updateDeliveryStatus = (id: number, status: number, remark?: string) => {
    return request({
        url: `/drivers/deliveries/${id}/status`,
        method: 'PUT',
        data: { status, ...(remark ? { remark } : {}) },
    });
};

/** 取消配送 */
export const cancelDelivery = (id: number, cancelReason: string) => {
    return request({
        url: `/drivers/deliveries/${id}/cancel`,
        method: 'POST',
        data: { cancelReason },
    });
};
