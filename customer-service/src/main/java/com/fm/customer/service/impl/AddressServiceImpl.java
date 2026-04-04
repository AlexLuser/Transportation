package com.fm.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fm.common.exception.BusinessException;
import com.fm.common.geo.AmapGeocodingService;
import com.fm.common.geo.GeoPoint;
import com.fm.common.result.ResultCode;
import com.fm.customer.entity.Address;
import com.fm.customer.mapper.AddressMapper;
import com.fm.customer.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 地址服务实现类
 */
@Service
public class AddressServiceImpl implements AddressService {
    
    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private AmapGeocodingService amapGeocodingService;
    
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

        applyGeocode(address, null);

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
            address.setIsDefault(0);
        }

        applyGeocode(address, existingAddress);

        addressMapper.updateById(address);
        return address;
    }
    
    @Override
    @Transactional
    public boolean deleteAddress(Long addressId) {
        Address address = getAddressById(addressId);
        if (address == null) {
            return false;
        }

        boolean deleted = addressMapper.deleteById(addressId) > 0;

        // 被删除的是默认地址时，从剩余地址中自动选一个设为默认
        if (deleted && address.getIsDefault() != null && address.getIsDefault() == 1) {
            List<Address> remaining = getAddressesByCustomerId(address.getCustomerId());
            if (!remaining.isEmpty()) {
                LambdaUpdateWrapper<Address> setWrapper = new LambdaUpdateWrapper<>();
                setWrapper.eq(Address::getId, remaining.get(0).getId())
                        .set(Address::getIsDefault, 1);
                addressMapper.update(null, setWrapper);
            }
        }

        return deleted;
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

    /**
     * 使用高德地理编码补全经纬度；失败时不阻断保存，更新场景下尽量保留原坐标。
     */
    private void applyGeocode(Address incoming, Address existing) {
        String p = pick(incoming.getProvince(), existing != null ? existing.getProvince() : null);
        String c = pick(incoming.getCity(), existing != null ? existing.getCity() : null);
        String d = pick(incoming.getDistrict(), existing != null ? existing.getDistrict() : null);
        String detail = pick(incoming.getDetailAddress(), existing != null ? existing.getDetailAddress() : null);
        Optional<GeoPoint> geo = amapGeocodingService.geocode(p, c, d, detail);
        if (geo.isPresent()) {
            GeoPoint pt = geo.get();
            incoming.setLatitude(pt.latitude());
            incoming.setLongitude(pt.longitude());
        } else if (existing != null) {
            if (incoming.getLatitude() == null) {
                incoming.setLatitude(existing.getLatitude());
            }
            if (incoming.getLongitude() == null) {
                incoming.setLongitude(existing.getLongitude());
            }
        }
    }

    private static String pick(String incoming, String existing) {
        return incoming != null ? incoming : existing;
    }
}

