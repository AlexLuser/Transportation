package com.fm.gateway.filter;

import com.fm.common.result.Result;
import com.fm.common.result.ResultCode;
import com.fm.common.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 在网关层统一验证Token
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 白名单：不需要验证Token的路径
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/auth/login",      // 登录接口
            "/api/auth/register",   // 注册接口
            "/swagger-ui/**",       // Swagger文档
            "/v3/api-docs/**"       // Swagger API文档
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 检查是否在白名单中
        if (isWhiteList(path)) {
            return chain.filter(exchange);  // 白名单直接放行
        }

        // 2. 从请求头获取Token
        String token = getToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "Token不能为空");
        }

        // 3. 验证Token
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange, "Token无效或已过期");
        }

        // 4. 验证通过，将用户信息放入请求头（可选，供下游服务使用）
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String roleCode = jwtUtil.getRoleCodeFromToken(token);

        // 5. 基于角色的权限控制
        if (!hasPermission(path, roleCode)) {
            return forbidden(exchange, "无权限访问该资源");
        }

        ServerHttpRequest.Builder builder = request.mutate();
        if (userId != null) {
            builder.header("userId", userId.toString());
        }
        if (StringUtils.hasText(username)) {
            builder.header("username", username);
        }
        if (StringUtils.hasText(roleCode)) {
            builder.header("roleCode", roleCode);
        }

        ServerHttpRequest modifiedRequest = builder.build();

        // 6. 放行请求
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * 判断是否在白名单中
     */
    private boolean isWhiteList(String path) {
        AntPathMatcher pathMatcher = new AntPathMatcher();
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 从请求头获取Token
     * 支持两种方式：Authorization: Bearer {token} 或 token: {token}
     */
    private String getToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);  // 去掉 "Bearer " 前缀
        }
        // 如果没有Authorization头，尝试从token头获取
        return request.getHeaders().getFirst("token");
    }

    /**
     * 基于角色的权限控制
     * 根据路径和角色判断是否有权限访问
     */
    private boolean hasPermission(String path, String roleCode) {
        // 管理员可以访问所有资源
        if ("admin".equals(roleCode)) {
            return true;
        }
        
        // 顾客地址详情查询（内部只读接口）：shop/driver/admin 创建物流时需要读取收货地址
        if (path.matches("/api/customers/address/\\d+")) {
            return "customer".equals(roleCode) || "shop".equals(roleCode)
                    || "driver".equals(roleCode) || "admin".equals(roleCode);
        }

        // 顾客服务：只允许customer角色访问
        if (path.startsWith("/api/customers")) {
            return "customer".equals(roleCode);
        }
        
        // 商户服务：只允许shop角色访问
        if (path.startsWith("/api/shops")) {
            return "shop".equals(roleCode);
        }
        
        // 商品服务：shop和customer都可以访问（shop管理，customer浏览）
        if (path.startsWith("/api/products")) {
            return "shop".equals(roleCode) || "customer".equals(roleCode);
        }
        
        // 商城服务：所有登录用户都可以访问（customer浏览，shop也可以查看）
        if (path.startsWith("/api/mall")) {
            return "shop".equals(roleCode) || "customer".equals(roleCode) || "admin".equals(roleCode);
        }
        
        // 仓库服务：只允许shop角色访问
        if (path.startsWith("/api/warehouses")) {
            return "shop".equals(roleCode);
        }
        
        // 库存服务：只允许shop角色访问
        if (path.startsWith("/api/stocks")) {
            return "shop".equals(roleCode);
        }
        
        // 运输员服务：只允许driver角色访问
        if (path.startsWith("/api/drivers")) {
            return "driver".equals(roleCode) || "admin".equals(roleCode);
        }

        // 物流路线查询：买家、商户、运输员、管理员均可查询
        if (path.startsWith("/api/logistics/routes")) {
            return "customer".equals(roleCode) || "shop".equals(roleCode)
                    || "driver".equals(roleCode) || "admin".equals(roleCode);
        }

        // 实时轨迹上报：运输员上报，所有人可查询
        if (path.startsWith("/api/logistics/track")) {
            return "customer".equals(roleCode) || "shop".equals(roleCode)
                    || "driver".equals(roleCode) || "admin".equals(roleCode);
        }

        // 调度管理接口：仅管理员
        if (path.startsWith("/api/logistics/dispatch")) {
            return "admin".equals(roleCode);
        }

        // AI 接口：所有登录用户可访问（各接口内部再细化权限）
        if (path.startsWith("/api/logistics/ai")) {
            return "customer".equals(roleCode) || "shop".equals(roleCode)
                    || "driver".equals(roleCode) || "admin".equals(roleCode);
        }

        // 默认允许访问（如果路径没有特殊权限要求）
        return true;
    }
    
    /**
     * 返回未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        Result<Object> result = Result.error(message);
        // Result.error 默认 code=500，这里按未授权场景改成401
        result.setCode(ResultCode.UNAUTHORIZED.getCode());
        try {
            String json = objectMapper.writeValueAsString(result);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
    
    /**
     * 返回禁止访问响应（403）
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        Result<Object> result = Result.error(message);
        result.setCode(ResultCode.FORBIDDEN.getCode());
        try {
            String json = objectMapper.writeValueAsString(result);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    /**
     * 过滤器执行顺序（数字越小越先执行）
     */
    @Override
    public int getOrder() {
        return 0;  // 设置为0，确保在其他过滤器之前执行
    }
}