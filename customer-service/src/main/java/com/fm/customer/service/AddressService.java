package com.fm.customer.service;

import com.fm.customer.entity.Address;

import java.util.List;

/**
 * 地址服务接口
 */
public interface AddressService {
    /**
     * 根据customer_id获取所有地址
     */
    List<Address> getAddressesByCustomerId(Long customerId);
    
    /**
     * 根据address_id获取地址
     */
    Address getAddressById(Long addressId);
    
    /**
     * 添加地址
     */
    Address addAddress(Address address);
    
    /**
     * 更新地址
     */
    Address updateAddress(Address address);
    
    /**
     * 删除地址
     */
    boolean deleteAddress(Long addressId);
    
    /**
     * 设置默认地址
     */
    boolean setDefaultAddress(Long customerId, Long addressId);
    
    /**
     * 获取默认地址
     */
    Address getDefaultAddress(Long customerId);
}

