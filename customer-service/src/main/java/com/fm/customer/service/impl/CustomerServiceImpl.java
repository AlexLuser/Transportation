package com.fm.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.customer.entity.Customer;
import com.fm.customer.mapper.CustomerMapper;
import com.fm.customer.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    @Cacheable(value = "customerByUser", key = "#userId", unless = "#result == null")
    public Customer getCustomerByUserId(Long userId) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getUserId, userId);
        return customerMapper.selectOne(wrapper);
    }

    @Override
    @Cacheable(value = "customerById", key = "#customerId", unless = "#result == null")
    public Customer getCustomerById(Long customerId) {
        return customerMapper.selectById(customerId);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "customerById",   key = "#customer.id",     condition = "#customer.id != null"),
        @CacheEvict(value = "customerByUser", key = "#customer.userId", condition = "#customer.userId != null")
    })
    public Customer saveOrUpdateCustomer(Customer customer) {
        if (customer.getId() == null) {
            customerMapper.insert(customer);
        } else {
            customerMapper.updateById(customer);
        }
        return customer;
    }

    @Override
    public List<Customer> getAllCustomers() {
        return customerMapper.selectList(null);
    }

    @Override
    @CacheEvict(value = "customerById", key = "#customerId")
    public boolean updateCustomerStatus(Long customerId, Integer status) {
        LambdaUpdateWrapper<Customer> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Customer::getId, customerId)
                .set(Customer::getStatus, status);
        return customerMapper.update(null, wrapper) > 0;
    }

    @Override
    @CacheEvict(value = "customerById", key = "#customerId")
    public boolean deleteCustomer(Long customerId) {
        return customerMapper.deleteById(customerId) > 0;
    }
}

