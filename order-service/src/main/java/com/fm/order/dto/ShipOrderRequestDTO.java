package com.fm.order.dto;

import lombok.Data;

/**
 * 商户发货请求（商户在发货弹窗中选择仓库后提交）
 */
@Data
public class ShipOrderRequestDTO {

    /** 商户选择的发货仓库ID */
    private Long warehouseId;
}
