import request from "@/utils/request";

const getMallProducts = (params: any) => {
    return request({
        url: '/mall/products',
        method: 'GET',
        params: params
    })
}

const getMallShop = (id: number) => {
    return request({
        url: `/mall/shop/${id}`,
        method: 'GET'
    })
}

const getMallProductDetail = (id: number) => {
    return request({
        url: `/products/${id}`,
        method: 'GET'
    })
}

/** 管理员搜索所有商品（含待审核、已下架），使用 /products/search 接口 */
const adminSearchProducts = (params: any) => {
    return request({
        url: '/products/search',
        method: 'GET',
        params: params
    })
}

/** 管理员审核通过商品（设置 status=1 上架） */
const adminApproveProduct = (id: number, productData: any) => {
    return request({
        url: '/products',
        method: 'PUT',
        data: { ...productData, id, status: 1 }
    })
}

export { getMallProducts, getMallProductDetail, getMallShop, adminSearchProducts, adminApproveProduct };