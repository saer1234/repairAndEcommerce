import { Routes } from '@angular/router';
import { HomePage } from './pages/home.page/home.page';
import { AboutPage } from './pages/about.page/about.page';
import { AboutSettingsPage } from './pages/about-settings.page/about-settings.page';
import { SupportPage } from './pages/support.page/support.page';
import { authGuard } from './core/guards/auth-guard';
import { platformOwnerGuard } from './core/guards/platform-owner-guard';
import { PlatformUsersPage } from './pages/platform-users.page/platform-users.page';
import { clientShopGuard } from './core/guards/client-shop-guard';
import { LoginPage } from './pages/auth/login.page/login.page';
import { RegisterPage } from './pages/auth/register.page/register.page';
import { ShellPage } from './pages/shell.page/shell.page';
import { DashboardPage } from './pages/dashboard.page/dashboard.page';
import { ShopPage } from './pages/shop.page/shop.page';
import { OrdersPage } from './pages/orders.page/orders.page';
import { InventoryPage } from './pages/inventory.page/inventory.page';
import { RepairPage } from './pages/repair.page/repair.page';
import { RepairTrackingPage } from './pages/repair-tracking.page/repair-tracking.page';
import { RepairWorkspacePage } from './pages/repair-workspace.page/repair-workspace.page';
import { CompaniesPage } from './pages/companies.page/companies.page';
export const routes:Routes=[
 {path:'',pathMatch:'full',component:HomePage},
 {path:'login',component:LoginPage},
 {path:'register',component:RegisterPage},
 {path:'about',component:AboutPage},
 {path:'',component:ShellPage,canActivate:[authGuard],children:[
   {path:'dashboard',component:DashboardPage},
   {path:'platform-users',component:PlatformUsersPage,canActivate:[platformOwnerGuard]},
   {path:'support',component:SupportPage},
   {path:'about-settings',component:AboutSettingsPage},
   {path:'shop',component:ShopPage,canActivate:[clientShopGuard]},
   {path:'orders',component:OrdersPage},
   {path:'inventory',component:InventoryPage},
   {path:'repairs',component:RepairPage},
   {path:'track-repairs',component:RepairTrackingPage},
   {path:'repair-workspace',component:RepairWorkspacePage},
   {path:'companies',component:CompaniesPage}
 ]},
 {path:'**',redirectTo:''}
];
