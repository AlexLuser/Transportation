package com.fm.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人寄件创建请求 DTO
 * 不依赖商户/商品/仓库，通过发件人地址坐标直接接入全国 Hub 网络
 */
@Data
public class CreatePersonalShipmentRequestDTO {

    /** 取件地址 ID（来自 customer_address，提供坐标用于 Hub 分配） */
    private Long senderAddressId;

    /** 收件地址 ID（来自 customer_address） */
    private Long deliveryAddressId;

    /** 货物名称/描述 */
    private String cargoName;

    /** 货物重量（kg），用于运费计算 */
    private Double weight;

    /** 申报价值（元），用于保价参考，不计入运费 */
    private BigDecimal declaredValue;

    /** 数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;
}
