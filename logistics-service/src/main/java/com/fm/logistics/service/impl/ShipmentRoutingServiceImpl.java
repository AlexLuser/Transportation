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
    public HubAssignmentDTO assignHubs(Long warehouseId,
                                       Double startLat, Double startLng,
                                       Double endLat, Double endLng) {
        Long originHubId;

        if (warehouseId != null) {
            // ── 商户订单：通过仓库确定 originHub ──
            Warehouse warehouse = warehouseMapper.selectById(warehouseId);
            if (warehouse == null) {
                throw new IllegalArgumentException("仓库不存在: " + warehouseId);
            }
            originHubId = warehouse.getAffiliatedHubId();
            if (originHubId == null) {
                if (warehouse.getLatitude() != null && warehouse.getLongitude() != null) {
                    NationalHub nearest = nationalHubMapper.selectNearestCityHub(
                            warehouse.getLatitude(), warehouse.getLongitude());
                    if (nearest != null) {
                        originHubId = nearest.getId();
                        log.warn("[ShipmentRouting] 仓库{}未配置affiliatedHubId，按坐标找到最近Hub: {}",
                                warehouseId, nearest.getName());
                    }
                }
            }
            if (originHubId == null) {
                throw new IllegalStateException("无法确定发货Hub，请为仓库配置 affiliated_hub_id");
            }
            log.info("[ShipmentRouting] 商户订单 仓库{}→originHub={}", warehouseId, originHubId);
        } else {
            // ── 个人寄件：通过发件人坐标找最近城市 Hub ──
            if (startLat == null || startLng == null) {
                throw new IllegalArgumentException("个人寄件缺少发件人坐标（startLat/startLng）");
            }
            NationalHub originHub = nationalHubMapper.selectNearestCityHub(startLat, startLng);
            if (originHub == null) {
                throw new IllegalStateException("无法根据发件人坐标找到城市Hub，请检查 national_hub 数据");
            }
            originHubId = originHub.getId();
            log.info("[ShipmentRouting] 个人寄件 发件坐标({},{})→originHub={}", startLat, startLng, originHub.getName());
        }

        // ── 收货城市 Hub ──
        if (endLat == null || endLng == null) {
            throw new IllegalArgumentException("收货坐标不能为空");
        }
        NationalHub destHub = nationalHubMapper.selectNearestCityHub(endLat, endLng);
        if (destHub == null) {
            throw new IllegalStateException("无法找到收货城市Hub，请检查 national_hub 数据");
        }
        Long destHubId = destHub.getId();

        boolean crossCity = !originHubId.equals(destHubId);
        log.info("[ShipmentRouting] 收货坐标({},{})→destHub={}, crossCity={}", endLat, endLng, destHub.getName(), crossCity);

        HubAssignmentDTO dto = new HubAssignmentDTO();
        dto.setOriginHubId(originHubId);
        dto.setDestHubId(destHubId);
        dto.setCrossCity(crossCity);
        return dto;
    }
}
