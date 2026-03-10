package com.fm.customer.service;

import com.fm.customer.entity.Customer;

import java.util.List;

/**
 * 顾客服务接口
 */
public interface CustomerService {
    /**
     * 根据user_id获取顾客信息
     */
    Customer getCustomerByUserId(Long userId);
    
    /**
     * 根据customer_id获取顾客信息
     */
    Customer getCustomerById(Long customerId);
    
    /**
     * 创建或更新顾客信息
     */
    Customer saveOrUpdateCustomer(Customer customer);
    
    /**
     * 获取所有顾客列表（管理员使用）
     */
    List<Customer> getAllCustomers();
    
    /**
     * 更新顾客状态
     */
    boolean updateCustomerStatus(Long customerId, Integer status);
    
    /**
     * 删除顾客信息
     */
    boolean deleteCustomer(Long customerId);
}

