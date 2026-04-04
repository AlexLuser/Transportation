import request from '@/utils/request';

export const getCustomerInfo = (userId: number) => {
    console.log("user id ", {userId});
    return request({
        url: `/customers/${userId}`,
        method: 'GET',
    })
}

export const addCustomerInfo = (data: any) => {
    return request({
        url: `/customers`,
        method: 'POST',
        data,
    })
}

export const updateCustomerInfo = (data: any) => {
    return request({
        url: `/customers`,
        method: 'PUT',
        data,
    })
}

export const getCustomerAddresses = (userId: number) => {
    return request({
        url: `/customers/${userId}/address`,
        method: 'GET',
    })
}

export const addCustomerAddress = (data: any) => {
    return request({
        url: `/customers/address`,
        method: 'POST',
        data,
    })
}

export const updateCustomerAddress = (data: any) => {
    return request({
        url: `/customers/address`,
        method: 'PUT',
        data,
    })
}

export const deleteCustomerAddress = (addressId: number) => {
    return request({
        url: `/customers/address/${addressId}`,
        method: 'DELETE',
    })
}

/** 根据地址ID获取地址详情（含经纬度，用于物流路线规划） */
export const getAddressById = (addressId: number) => {
    return request({
        url: `/customers/address/${addressId}`,
        method: 'GET',
    })
}
