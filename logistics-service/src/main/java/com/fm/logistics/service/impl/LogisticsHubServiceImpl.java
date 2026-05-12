package com.fm.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fm.logistics.dto.HubDTO;
import com.fm.logistics.entity.LogisticsHub;
import com.fm.logistics.mapper.LogisticsHubMapper;
import com.fm.logistics.service.LogisticsHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 物流中转站服务实现
 */
@Service
public class LogisticsHubServiceImpl implements LogisticsHubService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsHubServiceImpl.class);

    @Autowired
    private LogisticsHubMapper hubMapper;

    @Override
    public List<LogisticsHub> listHubs() {
        return hubMapper.selectList(
            new LambdaQueryWrapper<LogisticsHub>()
                .ne(LogisticsHub::getStatus, 2)  // 排除已关闭的 Hub
                .orderByAsc(LogisticsHub::getRegion)
        );
    }

    @Override
    public LogisticsHub findNearestHub(double lat, double lng) {
        List<LogisticsHub> hubs = hubMapper.selectNearestHubs(lat, lng, 1);
        if (hubs == null || hubs.isEmpty()) {
            log.warn("[Hub] 未找到可用中转站，坐标=({},{})", lat, lng);
            return null;
        }
        LogisticsHub hub = hubs.get(0);
        log.info("[Hub] 找到最近中转站：id={}, name={}", hub.getId(), hub.getName());
        return hub;
    }

    @Override
    public LogisticsHub getHubById(Long hubId) {
        return hubMapper.selectById(hubId);
    }

    @Override
    public LogisticsHub createHub(HubDTO hubDTO) {
        LogisticsHub hub = new LogisticsHub();
        hub.setName(hubDTO.getName());
        hub.setAddress(hubDTO.getAddress());
        hub.setLatitude(hubDTO.getLatitude());
        hub.setLongitude(hubDTO.getLongitude());
        hub.setRegion(hubDTO.getRegion());
        hub.setMaxCapacity(hubDTO.getMaxCapacity() != null ? hubDTO.getMaxCapacity() : 1000);
        hub.setCurrentLoad(0);
        hub.setStatus(0);
        hub.setRemark(hubDTO.getRemark());
        hubMapper.insert(hub);
        log.info("[Hub] 创建中转站：id={}, name={}", hub.getId(), hub.getName());
        return hub;
    }

    @Override
    public LogisticsHub updateHub(HubDTO hubDTO) {
        LogisticsHub hub = hubMapper.selectById(hubDTO.getId());
        if (hub == null) {
            throw new RuntimeException("中转站不存在：id=" + hubDTO.getId());
        }
        if (hubDTO.getName() != null)        hub.setName(hubDTO.getName());
        if (hubDTO.getAddress() != null)     hub.setAddress(hubDTO.getAddress());
        if (hubDTO.getLatitude() != null)    hub.setLatitude(hubDTO.getLatitude());
        if (hubDTO.getLongitude() != null)   hub.setLongitude(hubDTO.getLongitude());
        if (hubDTO.getRegion() != null)      hub.setRegion(hubDTO.getRegion());
        if (hubDTO.getMaxCapacity() != null) hub.setMaxCapacity(hubDTO.getMaxCapacity());
        if (hubDTO.getStatus() != null)      hub.setStatus(hubDTO.getStatus());
        if (hubDTO.getRemark() != null)      hub.setRemark(hubDTO.getRemark());
        hubMapper.updateById(hub);
        return hub;
    }

    @Override
    public void deleteHub(Long hubId) {
        LogisticsHub hub = hubMapper.selectById(hubId);
        if (hub == null) return;
        hub.setStatus(2);  // 软删：status=2=关闭
        hubMapper.updateById(hub);
        log.info("[Hub] 删除（关闭）中转站：id={}", hubId);
    }
}
