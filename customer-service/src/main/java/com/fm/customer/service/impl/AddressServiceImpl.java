package com.fm.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.common.exception.BusinessException;
import com.fm.common.result.ResultCode;
import com.fm.customer.entity.Address;
import com.fm.customer.mapper.AddressMapper;
import com.fm.customer.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地址服务实现类
 */
@Service
public class AddressServiceImpl implements AddressService {
    
    @Autowired
    private AddressMapper addressMapper;
    
    @Override
    public List<Address> getAddressesByCustomerId(Long customerId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getCustomerId, customerId)
                .orderByDesc(Address::getIsDefault)  // 默认地址排在前面
                .orderByDesc(Address::getCreateTime);  // 按创建时间倒序
        return addressMapper.selectList(wrapper);
    }
    
    @Override
    public Address getAddressById(Long addressId) {
        return addressMapper.selectById(addressId);
    }
    
    @Override
    @Transactional
    public Address addAddress(Address address) {
        // 如果是第一条地址，自动设为默认
        List<Address> existingAddresses = getAddressesByCustomerId(address.getCustomerId());
        if (existingAddresses.isEmpty()) {
            address.setIsDefault(1);
        }
        
        addressMapper.insert(address);
        
        // 如果设置为默认地址，清除其他默认地址
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            setDefaultAddress(address.getCustomerId(), address.getId());
            address.setIsDefault(1);
        }
        
        return address;
    }
    
    @Override
    @Transactional
    public Address updateAddress(Address address) {
        // 获取更新前的地址信息
        Address existingAddress = getAddressById(address.getId());
        if (existingAddress == null) {
            throw new BusinessException(ResultCode.FAIL.getCode(), "地址不存在");
        }
        
        // 处理默认地址逻辑
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            // 如果设置为默认地址，先取消其他默认地址，然后设置当前地址为默认
            setDefaultAddress(address.getCustomerId(), address.getId());
            address.setIsDefault(1);
        } else if (address.getIsDefault() != null && address.getIsDefault() == 0) {
            // 如果取消默认地址，需要检查是否还有其他默认地址
            // 如果当前地址是唯一的默认地址，则不允许取消
            if (existingAddress.getIsDefault() != null && existingAddress.getIsDefault() == 1) {
                List<Address> allAddresses = getAddressesByCustomerId(address.getCustomerId());
                long defaultCount = allAddresses.stream()
                        .filter(addr -> addr.getIsDefault() != null && addr.getIsDefault() == 1)
                        .count();
                if (defaultCount <= 1) {
                    throw new BusinessException(ResultCode.FAIL.getCode(), "至少需要保留一个默认地址");
                }
            }
            address.setIsDefault(0);
        }
        
        addressMapper.updateById(address);
        return address;
    }
    
    @Override
    public boolean deleteAddress(Long addressId) {
        return addressMapper.deleteById(addressId) > 0;
    }
    
    @Override
    @Transactional
    public boolean setDefaultAddress(Long customerId, Long addressId) {
        // 1. 先将该顾客的所有地址设为非默认
        LambdaUpdateWrapper<Address> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(Address::getCustomerId, customerId)
                .set(Address::getIsDefault, 0);
        addressMapper.update(null, clearWrapper);
        
        // 2. 设置指定地址为默认
        LambdaUpdateWrapper<Address> setWrapper = new LambdaUpdateWrapper<>();
        setWrapper.eq(Address::getId, addressId)
                .set(Address::getIsDefault, 1);
        return addressMapper.update(null, setWrapper) > 0;
    }
    
    @Override
    public Address getDefaultAddress(Long customerId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getCustomerId, customerId)
                .eq(Address::getIsDefault, 1)
                .last("LIMIT 1");
        return addressMapper.selectOne(wrapper);
    }
}

