import request from '@/utils/request';

/** 按订单 ID 查询物流路线详情（节点、计划路径等，与 logistics-service 一致） */
export const getRouteByOrderId = (orderId: number) => {
    return request({ url: `/logistics/routes/order/${orderId}`, method: 'GET' });
};

export interface CreateRouteRequest {
    orderId: number;
    warehouseId: number;
    startAddress: string;
    startLatitude?: number;
    startLongitude?: number;
    endAddress: string;
    endLatitude?: number;
    endLongitude?: number;
    receiverName?: string;
    receiverPhone?: string;
}

/**
 * 创建物流路线（发货时调用，触发 LLM 路线规划策略）
 * data 为 { route, llmEnhanced, llmDecision }，LLM 元数据仅本次响应可见、不入库
 */
export const createRoute = (data: CreateRouteRequest) => {
    return request({ url: '/logistics/routes', method: 'POST', data });
};
