package com.fm.logistics.controller;

import com.fm.common.result.Result;
import com.fm.logistics.dto.AssignHubsRequestDTO;
import com.fm.logistics.dto.HubAssignmentDTO;
import com.fm.logistics.service.ShipmentRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 发货路由接口（被 order-service 通过 Feign 调用）
 */
@RestController
@RequestMapping("/api/logistics/routing")
@RequiredArgsConstructor
public class ShipmentRoutingController {

    private final ShipmentRoutingService shipmentRoutingService;

    /**
     * 根据发货仓库和收货坐标，分配 originHub / destHub
     */
    @PostMapping("/assign-hubs")
    public Result<HubAssignmentDTO> assignHubs(@RequestBody AssignHubsRequestDTO req) {
        HubAssignmentDTO result = shipmentRoutingService.assignHubs(
                req.getWarehouseId(), req.getEndLat(), req.getEndLng());
        return Result.success(result);
    }
}
