package com.fm.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.customer.entity.Customer;
import com.fm.customer.mapper.CustomerMapper;
import com.fm.customer.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 顾客服务实现类
 */
@Service
public class CustomerServiceImpl implements CustomerService {
    
    @Autowired
    private CustomerMapper customerMapper;
    
    @Override
    public Customer getCustomerByUserId(Long userId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getUserId, userId);
        return customerMapper.selectOne(wrapper);
    }
    
    @Override
    public Customer getCustomerById(Long customerId) {
        return customerMapper.selectById(customerId);
    }
    
    @Override
    public Customer saveOrUpdateCustomer(Customer customer) {
        if (customer.getId() == null) {
            // 新增
            customerMapper.insert(customer);
        } else {
            // 更新
            customerMapper.updateById(customer);
        }
        return customer;
    }
    
    @Override
    public List<Customer> getAllCustomers() {
        return customerMapper.selectList(null);
    }
    
    @Override
    public boolean updateCustomerStatus(Long customerId, Integer status) {
        LambdaUpdateWrapper<Customer> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Customer::getId, customerId)
                .set(Customer::getStatus, status);
        return customerMapper.update(null, wrapper) > 0;
    }
    
    @Override
    public boolean deleteCustomer(Long customerId) {
        // 注意：删除顾客信息前，应该先删除关联的地址信息
        // 这里只删除顾客信息，地址的级联删除需要在业务层处理
        return customerMapper.deleteById(customerId) > 0;
    }
}

