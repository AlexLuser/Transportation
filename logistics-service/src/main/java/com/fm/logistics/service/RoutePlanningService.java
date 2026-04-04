package com.fm.logistics.service;

import com.fm.logistics.dto.RouteResultDTO;

public interface RoutePlanningService {

    RouteResultDTO planRoute(double startLat, double startLon, double endLat, double endLon);
}
