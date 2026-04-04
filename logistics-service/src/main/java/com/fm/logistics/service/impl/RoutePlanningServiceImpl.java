package com.fm.logistics.service.impl;

import com.fm.logistics.dto.RouteResultDTO;
import com.fm.logistics.service.RoutePlanningService;
import com.fm.logistics.service.RouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoutePlanningServiceImpl implements RoutePlanningService {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanningServiceImpl.class);

    @Autowired
    private RouteStrategy routeStrategy;

    @Override
    public RouteResultDTO planRoute(double startLat, double startLon, double endLat, double endLon) {
        
        long startTime = System.currentTimeMillis();

        log.info("开始规划路径: startLat={}, startLon={}, endLat={}, endLon={}", startLat, startLon, endLat, endLon);

        RouteResultDTO result = routeStrategy.plan(startLat, startLon, endLat, endLon);

        long elapsedTime = System.currentTimeMillis() - startTime;

        if(result.isSuccess()) {
            log.info("路径规划成功: distance={}, duration={}", result.getDistanceMeters(), result.getDurationMs());
        } else {
            log.error("路径规划失败: errorMsg={}", result.getErrorMsg());
        }

        return result;
    }
}
