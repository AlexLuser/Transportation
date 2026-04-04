package com.fm.logistics.config;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 创建 GraphHopper Bean：读 OSM、用车道规则算路，图数据缓存在本地目录。
@Configuration
public class GraphHopperConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfig.class);

    @Value("${graphhopper.osm-file}")
    private String osmFile;        // OSM 地图文件路径
    @Value("${graphhopper.graph-cache}")
    private String graphCache;    // 图缓存目录（有则加载，无则从 OSM 导入）
    @Value("${graphhopper.vehicle}")
    private String vehicle;       // 出行方式，如 car，与 Profile 名一致

    // 初始化引擎：机动车 custom 权重，然后 importOrLoad。
    @Bean
    public GraphHopper graphHopper() {
        log.info("GraphHopperConfig - osmFile: {}", osmFile);

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphCache);

        CustomModel carModel = new CustomModel()
            .setDistanceInfluence(90.0)
            .addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"))
            .addToPriority(Statement.If("!car_access", Statement.Op.MULTIPLY, "0"));

        hopper.setProfiles(
            new Profile(vehicle)
                .setWeighting("custom")
                .setCustomModel(carModel)
        );

        hopper.setEncodedValuesString("car_access, car_average_speed");
        hopper.importOrLoad();

        log.info("GraphHopperConfig - graphHopper initialized");

        return hopper;
    }
}
