package com.fm.logistics.service.impl;

import com.fm.logistics.dto.HubAssignmentDTO;
import com.fm.logistics.entity.NationalHub;
import com.fm.logistics.entity.Warehouse;
import com.fm.logistics.mapper.NationalHubMapper;
import com.fm.logistics.mapper.WarehouseMapper;
import com.fm.logistics.service.ShipmentRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentRoutingServiceImpl implements ShipmentRoutingService {

    private final WarehouseMapper warehouseMapper;
    private final NationalHubMapper nationalHubMapper;

    @Override
    public HubAssignmentDTO assignHubs(Long warehouseId, Double endLat, Double endLng) {
        // Step1: 查仓库，获取 affiliatedHubId
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new IllegalArgumentException("仓库不存在: " + warehouseId);
        }

        Long originHubId = warehouse.getAffiliatedHubId();
        if (originHubId == null) {
            // 兜底：按坐标找最近城市Hub
            if (warehouse.getLatitude() != null && warehouse.getLongitude() != null) {
                NationalHub nearest = nationalHubMapper.selectNearestCityHub(
                        warehouse.getLatitude(), warehouse.getLongitude());
                if (nearest != null) {
                    originHubId = nearest.getId();
                    log.warn("[ShipmentRouting] 仓库{}未配置affiliatedHubId，按坐标找到最近Hub: {}", warehouseId, nearest.getName());
                }
            }
        }
        if (originHubId == null) {
            throw new IllegalStateException("无法确定发货Hub，请为仓库配置 affiliated_hub_id");
        }

        // Step2: 按收货坐标找最近城市Hub
        if (endLat == null || endLng == null) {
            throw new IllegalArgumentException("收货坐标不能为空");
        }
        NationalHub destHub = nationalHubMapper.selectNearestCityHub(endLat, endLng);
        if (destHub == null) {
            throw new IllegalStateException("无法找到收货城市Hub，请检查 national_hub 数据");
        }
        Long destHubId = destHub.getId();

        boolean crossCity = !originHubId.equals(destHubId);
        log.info("[ShipmentRouting] 仓库{}→originHub={}, 收货坐标({},{})→destHub={}, crossCity={}",
                warehouseId, originHubId, endLat, endLng, destHubId, crossCity);

        HubAssignmentDTO dto = new HubAssignmentDTO();
        dto.setOriginHubId(originHubId);
        dto.setDestHubId(destHubId);
        dto.setCrossCity(crossCity);
        return dto;
    }
}
