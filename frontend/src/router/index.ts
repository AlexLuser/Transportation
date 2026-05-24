import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

export const roleHomeMap: Record<string, string> = {
    admin: '/admin/home',
    customer: '/customer/home',
    shop: '/shop/home',
    driver: '/driver/home'
}

const routes = [
    {
        path: '/login',
        component: () => import('@/pages/Login.vue')
    },
    {
        path: '/register',
        component: () => import('@/pages/Register.vue')
    },
    {
        path: '/admin/home',
        component: () => import('@/pages/AdminHome.vue'),
        redirect: '/admin/home/dashboard',
        meta: {role: 'admin'},
        children: [
            {
                path: 'dashboard',
                component: () => import('@/pages/Admin/Dashboard.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'orders',
                component: () => import('@/pages/Admin/Orders.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'users',
                component: () => import('@/pages/Admin/Users.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'goods',
                component: () => import('@/pages/Admin/Goods.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'national',
                component: () => import('@/pages/Admin/NationalBusiness.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'lastmile',
                component: () => import('@/pages/Admin/LastMileBusiness.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'warehouse-mgmt',
                component: () => import('@/pages/Admin/WarehouseMgmt.vue'),
                meta: {role: 'admin'}
            },
            {
                path: 'hub-operations',
                component: () => import('@/pages/Admin/HubOperations.vue'),
                meta: {role: 'admin'}
            },
            { path: 'system', redirect: '/admin/home/warehouse-mgmt' },
            { path: 'hubs', redirect: { path: '/admin/home/lastmile', query: { tab: 'hubs' } } },
            { path: 'batches', redirect: { path: '/admin/home/lastmile', query: { tab: 'batches' } } },
            { path: 'dispatch', redirect: { path: '/admin/home/national', query: { tab: 'trunk' } } },
            { path: 'national-network', redirect: { path: '/admin/home/national', query: { tab: 'network' } } },
            { path: 'flow-plan', redirect: { path: '/admin/home/national', query: { tab: 'flow' } } }
        ]
    },
    {
        path: '/customer/home',
        component: () => import('@/pages/CustomerHome.vue'),
        meta: {role: 'customer'},
        redirect: '/customer/home/products',
        children: [
            {
                path: 'products',
                component: () => import('@/pages/Customer/Product.vue'),
                meta: {role: 'customer'}
            },
            {
                path: 'orders',
                component: () => import('@/pages/Customer/Order.vue'),
                meta: {role: 'customer'}
            },
            {
                path: 'profile',
                component: () => import('@/pages/Customer/Profile.vue'),
                meta: {role: 'customer'}
            },
            {
                path: 'shop-order',
                component: () => import('@/pages/Customer/CreateOrder.vue'),
                meta: {role: 'customer'}
            },
            {
                path: 'shop/:id',
                component: () => import('@/pages/Customer/ShopPage.vue'),
                meta: {role: 'customer'}
            }
        ]
    },
    {
        path: '/shop/home',
        component: () => import('@/pages/ShopHome.vue'),
        meta: {role: 'shop'},
        redirect: '/shop/home/products',
        children: [
            {
                path: 'products',
                component: () => import('@/pages/Shop/Product.vue'),
                meta: {role: 'shop'}
            },
            {
                path: 'orders',
                component: () => import('@/pages/Shop/Order.vue'),
                meta: {role: 'shop'}
            },
            {
                path: 'profile',
                component: () => import('@/pages/Shop/Profile.vue'),
                meta: {role: 'shop'}
            },
            {
                path: 'stock',
                component: () => import('@/pages/Shop/Stock.vue'),
                meta: {role: 'shop'}
            },
            {
                path: 'warehouse',
                component: () => import('@/pages/Shop/Warehouse.vue'),
                meta: {role: 'shop'}
            }
        ]
    },
    {
        path: '/driver/home',
        component: () => import('@/pages/DriverHome.vue'),
        meta: {role: 'driver'},
        redirect: '/driver/home/deliveries',
        children: [
            {
                path: 'navigation',
                component: () => import('@/pages/Driver/Navigation.vue'),
                meta: { role: 'driver' }
            },
            {
                path: 'deliveries',
                component: () => import('@/pages/Driver/Delivery.vue'),
                meta: {role: 'driver'}
            },
            {
                path: 'vehicles',
                component: () => import('@/pages/Driver/Vehicle.vue'),
                meta: {role: 'driver'}
            },
            {
                path: 'profile',
                component: () => import('@/pages/Driver/Profile.vue'),
                meta: {role: 'driver'}
            },
            {
                path: 'gps-test',
                component: () => import('@/pages/Driver/GpsTest.vue'),
                meta: {role: 'driver'}
            }
        ]
    },
    {
        path: '/', 
        redirect: '/login'
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/login'
    }
]

export const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, _from, next) => {
    const userStore = useUserStore();
    const token = userStore.token;
    const roleCode = userStore.userInfo?.roleCode;

    if(to.path === '/login' || to.path === '/register') {
        if(token && roleCode && to.path === '/login') {
            next(roleHomeMap[roleCode]);
        } else {
            next();
        }
        return;
    } 
    
    if(!token){
        next('/login');
        return;
    }

    const requiredRole = to.meta.role as string | undefined;
    if(requiredRole && roleCode && requiredRole !== roleCode) {
        next(roleHomeMap[roleCode]);
        return;
    }

    next();
})