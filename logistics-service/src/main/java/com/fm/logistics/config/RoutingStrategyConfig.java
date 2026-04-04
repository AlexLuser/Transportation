package com.fm.logistics.config;

import com.fm.logistics.service.RouteStrategy;
import com.fm.logistics.service.impl.AStarRouteStrategy;
import com.fm.logistics.service.impl.LlmJudgeRouteStrategy;
import com.fm.logistics.service.impl.LlmWaypointRouteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 路径规划策略选择配置
 *
 * 通过 application.yml 中的 routing.strategy 参数切换策略，无需修改任何业务代码：
 *   A_STAR       - 纯A*算法（默认，稳定可靠）
 *   LLM_JUDGE    - 方案A：多候选路线 + LLM裁判选择最优
 *   LLM_WAYPOINT - 方案B：LLM决策战略路点 + A*分段执行
 *
 * 切换示例（application.yml）：
 *   routing:
 *     strategy: LLM_JUDGE
 */
@Configuration
public class RoutingStrategyConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutingStrategyConfig.class);

    @Value("${routing.strategy:A_STAR}")
    private String strategyMode;

    /**
     * 声明为 @Primary，确保 RoutePlanningServiceImpl 中的
     * @Autowired  RouteStrategy 注入的是此处选定的策略，而非其他实现类
     */
    @Bean
    @Primary
    public RouteStrategy activeRouteStrategy(AStarRouteStrategy astarStrategy,
                                              LlmJudgeRouteStrategy llmJudgeStrategy,
                                              LlmWaypointRouteStrategy llmWaypointStrategy) {
        RouteStrategy selected = switch (strategyMode.toUpperCase()) {
            case "LLM_JUDGE"    -> llmJudgeStrategy;
            case "LLM_WAYPOINT" -> llmWaypointStrategy;
            default             -> astarStrategy;
        };
        log.info("╔══════════════════════════════════════════╗");
        log.info("║  路径规划策略已激活：{}  ║", selected.strategyName());
        log.info("╚══════════════════════════════════════════╝");
        return selected;
    }
}
